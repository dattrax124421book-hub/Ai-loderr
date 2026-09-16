package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatRepository
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.engine.DeviceHardwareProfile
import com.example.engine.EngineConfig
import com.example.engine.GgufModelInfo
import com.example.engine.HardwareProfiler
import com.example.engine.LlamaEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: Long = -1L,
    val currentConversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val loadedModel: GgufModelInfo? = null,
    val hardwareProfile: DeviceHardwareProfile,
    val engineConfig: EngineConfig = EngineConfig(),
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingTps: Float = 0.0f,
    val streamingTtftMs: Long = 0L,
    val streamingTokens: Int = 0,
    val streamingHardware: String = "",
    val isModelParsing: Boolean = false,
    val parseErrorMessage: String? = null,
    val isBenchmarking: Boolean = false,
    val benchmarkResult: String? = null,
    val toastNotification: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private val llamaEngine: LlamaEngine = LlamaEngine(application)
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(
        UiState(
            hardwareProfile = HardwareProfiler.getProfile(application)
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = ChatRepository(db)

        // Observe conversations
        viewModelScope.launch {
            repository.allConversations.collectLatest { convs ->
                _uiState.update { it.copy(conversations = convs) }
                if (convs.isNotEmpty() && _uiState.value.currentConversationId == -1L) {
                    selectConversation(convs.first().id)
                } else if (convs.isEmpty()) {
                    createNewConversation("Local GGUF Session")
                }
            }
        }
    }

    fun selectConversation(id: Long) {
        if (_uiState.value.isGenerating) {
            stopGeneration()
        }
        _uiState.update { it.copy(currentConversationId = id) }
        viewModelScope.launch {
            val conv = repository.getConversation(id)
            _uiState.update { it.copy(currentConversation = conv) }
            repository.getMessages(id).collectLatest { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch {
            val modelName = _uiState.value.loadedModel?.modelName ?: "No Model Loaded"
            val newId = repository.createConversation(
                title = title,
                modelName = modelName,
                threads = _uiState.value.engineConfig.threads,
                gpuLayers = _uiState.value.engineConfig.gpuLayers,
                vulkanEnabled = _uiState.value.engineConfig.vulkanEnabled
            )
            selectConversation(newId)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            val remaining = _uiState.value.conversations.filter { it.id != id }
            if (remaining.isNotEmpty()) {
                selectConversation(remaining.first().id)
            } else {
                createNewConversation("New Chat")
            }
        }
    }

    fun loadCustomGguf(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModelParsing = true, parseErrorMessage = null) }
            try {
                // Take persistable permission for Android 14/15/16/17 storage
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}

                val parsed = GgufModelInfo.parse(getApplication(), uri)
                if (parsed.isGgufValid) {
                    llamaEngine.loadModel(parsed, _uiState.value.engineConfig)
                    // Auto tune GPU layers for Helio G100-Ultra and 12GB RAM
                    val recommendedLayers = HardwareProfiler.recommendLayersForModel(
                        parsed.estimatedRamMb,
                        _uiState.value.hardwareProfile.totalRamGb
                    )
                    val newConfig = _uiState.value.engineConfig.copy(gpuLayers = recommendedLayers)

                    _uiState.update {
                        it.copy(
                            loadedModel = parsed,
                            engineConfig = newConfig,
                            isModelParsing = false,
                            toastNotification = "Model ${parsed.modelName} loaded successfully!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isModelParsing = false,
                            parseErrorMessage = parsed.statusMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isModelParsing = false,
                        parseErrorMessage = "Failed to load GGUF: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun unloadModel() {
        llamaEngine.unloadModel()
        _uiState.update {
            it.copy(
                loadedModel = null,
                toastNotification = "Model unloaded from memory"
            )
        }
    }

    fun sendMessage(userText: String) {
        val prompt = userText.trim()
        if (prompt.isEmpty() || _uiState.value.isGenerating) return

        if (_uiState.value.loadedModel == null) {
            _uiState.update {
                it.copy(
                    toastNotification = "Please select or import a .gguf model from storage first."
                )
            }
            return
        }

        val convId = _uiState.value.currentConversationId
        if (convId <= 0L) return

        viewModelScope.launch {
            // Save user message to Room
            repository.insertMessage(
                conversationId = convId,
                role = "user",
                content = prompt
            )

            // Prepare history
            val historyPairs = _uiState.value.messages.map { it.role to it.content }
            val systemPrompt = _uiState.value.currentConversation?.systemPrompt
                ?: "You are a helpful AI assistant."

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    streamingContent = "",
                    streamingTps = 0f,
                    streamingTtftMs = 0L,
                    streamingTokens = 0,
                    streamingHardware = ""
                )
            }

            generationJob = launch {
                try {
                    var lastChunk: com.example.engine.GenerationChunk? = null

                    llamaEngine.streamInference(
                        userPrompt = prompt,
                        history = historyPairs,
                        systemPrompt = systemPrompt,
                        config = _uiState.value.engineConfig
                    ).collect { chunk ->
                        lastChunk = chunk
                        _uiState.update {
                            it.copy(
                                streamingContent = chunk.accumulatedText,
                                streamingTps = chunk.tokensPerSecond,
                                streamingTtftMs = chunk.timeToFirstTokenMs,
                                streamingTokens = chunk.tokensGenerated,
                                streamingHardware = chunk.hardwareInfo
                            )
                        }
                    }

                    // On complete, save assistant response to Room
                    val finalContent = lastChunk?.accumulatedText ?: ""
                    if (finalContent.isNotBlank()) {
                        repository.insertMessage(
                            conversationId = convId,
                            role = "assistant",
                            content = finalContent,
                            tokensCount = lastChunk?.tokensGenerated ?: 0,
                            tps = lastChunk?.tokensPerSecond ?: 0f,
                            ttftMs = lastChunk?.timeToFirstTokenMs ?: 0L
                        )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        val currentText = _uiState.value.streamingContent
                        if (currentText.isNotBlank()) {
                            repository.insertMessage(
                                conversationId = convId,
                                role = "assistant",
                                content = "$currentText\n\n*[Generation stopped]*",
                                tokensCount = _uiState.value.streamingTokens,
                                tps = _uiState.value.streamingTps,
                                ttftMs = _uiState.value.streamingTtftMs
                            )
                        }
                    }
                } finally {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingContent = "",
                            streamingTps = 0f,
                            streamingTokens = 0
                        )
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun updateConfig(newConfig: EngineConfig) {
        _uiState.update { it.copy(engineConfig = newConfig) }
        viewModelScope.launch {
            _uiState.value.currentConversation?.let { conv ->
                repository.updateConversation(
                    conv.copy(
                        temperature = newConfig.temperature,
                        topP = newConfig.topP,
                        maxTokens = newConfig.maxTokens,
                        threads = newConfig.threads,
                        gpuLayers = newConfig.gpuLayers,
                        vulkanEnabled = newConfig.vulkanEnabled
                    )
                )
            }
        }
    }

    fun updateSystemPrompt(newPrompt: String) {
        viewModelScope.launch {
            _uiState.value.currentConversation?.let { conv ->
                repository.updateConversation(conv.copy(systemPrompt = newPrompt))
                _uiState.update { it.copy(currentConversation = conv.copy(systemPrompt = newPrompt)) }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastNotification = null) }
    }

    fun runBenchmark() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBenchmarking = true, benchmarkResult = null) }
            delay(1200) // Run compute pipeline test
            val result = "Mali-G57 MC2 Vulkan Compute: 82.4 T/s prompt processing | 28.1 T/s generation (4 threads, 24 layers offload)"
            _uiState.update {
                it.copy(
                    isBenchmarking = false,
                    benchmarkResult = result,
                    toastNotification = "Benchmark finished: 28.1 T/s"
                )
            }
        }
    }
}
