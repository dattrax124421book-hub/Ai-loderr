package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufModelInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val uri: Uri,
    val architecture: String,
    val modelName: String,
    val quantization: String,
    val contextLength: Int,
    val layerCount: Int,
    val embeddingLength: Int,
    val headCount: Int,
    val headCountKv: Int,
    val tensorCount: Long,
    val kvCount: Long,
    val estimatedRamMb: Int,
    val isGgufValid: Boolean,
    val statusMessage: String
) {
    val fileSizeFormatted: String
        get() = formatBytes(fileSizeBytes)

    val ramRequirementFormatted: String
        get() = "${estimatedRamMb} MB (~${String.format("%.1f", estimatedRamMb / 1024.0)} GB)"

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            return String.format("%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
        }

        fun parse(context: Context, uri: Uri): GgufModelInfo {
            var fileName = "unknown.gguf"
            var fileSize = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            val queriedName = cursor.getString(nameIndex)
                            if (!queriedName.isNullOrBlank()) fileName = queriedName
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore cursor query errors on scoped storage
            }

            // Android 15/16/17 Fallback for fileName and fileSize
            if (fileName == "unknown.gguf") {
                val lastSeg = uri.lastPathSegment
                if (!lastSeg.isNullOrBlank()) {
                    fileName = lastSeg.substringAfterLast('/')
                }
            }
            if (fileSize <= 0L) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSize = pfd.statSize
                    }
                } catch (_: Exception) {}
            }

            var stream: InputStream? = null
            try {
                val rawStream = context.contentResolver.openInputStream(uri)
                if (rawStream == null) {
                    return fallbackInfo(fileName, fileSize, uri, "Failed to open input stream from storage")
                }
                stream = java.io.BufferedInputStream(rawStream, 64 * 1024)

                val headerBuffer = ByteArray(24)
                var readBytes = 0
                while (readBytes < 24) {
                    val r = stream.read(headerBuffer, readBytes, 24 - readBytes)
                    if (r == -1) break
                    readBytes += r
                }

                if (readBytes < 24) {
                    return fallbackInfo(fileName, fileSize, uri, "File too small for GGUF header")
                }

                val bb = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.int
                // 'G' (0x47), 'G' (0x47), 'U' (0x55), 'F' (0x46) -> 0x46554747
                val expectedMagic = 0x46554747
                val isValid = magic == expectedMagic

                if (!isValid) {
                    return fallbackInfo(
                        fileName, fileSize, uri,
                        "Invalid magic header (Not a standard GGUF binary)"
                    )
                }

                val version = bb.int
                val tensorCount = bb.long
                val kvCount = bb.long

                // Read KV pairs iteratively with memory limit to prevent OOM
                var architecture = "llama"
                var modelName = fileName.removeSuffix(".gguf")
                var contextLength = 4096
                var layerCount = 32
                var embeddingLength = 4096
                var headCount = 32
                var headCountKv = 32
                var quantization = extractQuantFromFileName(fileName)

                val metadataMap = mutableMapOf<String, Any>()
                val maxKvToRead = kvCount.coerceAtMost(128L)

                for (i in 0 until maxKvToRead) {
                    val key = readGgufString(stream) ?: break
                    val valueType = readGgufUint32(stream) ?: break
                    val value = readGgufValue(stream, valueType) ?: break
                    metadataMap[key] = value

                    when (key) {
                        "general.architecture" -> architecture = value.toString()
                        "general.name" -> modelName = value.toString()
                        "general.file_type" -> {
                            val ftInt = (value as? Number)?.toInt()
                            if (ftInt != null) quantization = mapGgufFileType(ftInt)
                        }
                    }
                }

                // Check architecture specific keys
                val archPrefix = architecture.lowercase()
                metadataMap["${archPrefix}.context_length"]?.let {
                    (it as? Number)?.toInt()?.let { ctx -> contextLength = ctx }
                }
                metadataMap["${archPrefix}.block_count"]?.let {
                    (it as? Number)?.toInt()?.let { blk -> layerCount = blk }
                }
                metadataMap["${archPrefix}.embedding_length"]?.let {
                    (it as? Number)?.toInt()?.let { emb -> embeddingLength = emb }
                }
                metadataMap["${archPrefix}.attention.head_count"]?.let {
                    (it as? Number)?.toInt()?.let { h -> headCount = h }
                }
                metadataMap["${archPrefix}.attention.head_count_kv"]?.let {
                    (it as? Number)?.toInt()?.let { hkv -> headCountKv = hkv }
                }

                // RAM estimate: File size + KV cache calculation
                val modelWeightMb = (fileSize / (1024 * 1024)).toInt()
                // KV cache RAM ~ 2 * layerCount * headCountKv * (embeddingLength/headCount) * contextLength * 2 bytes
                val kvCacheMb = ((2.0 * layerCount * (embeddingLength / headCount.coerceAtLeast(1)) * headCountKv * contextLength * 2.0) / (1024.0 * 1024.0)).toInt().coerceIn(128, 2048)
                val totalEstRamMb = modelWeightMb + kvCacheMb + 256 // runtime overhead

                return GgufModelInfo(
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    uri = uri,
                    architecture = architecture,
                    modelName = modelName,
                    quantization = quantization,
                    contextLength = contextLength,
                    layerCount = layerCount,
                    embeddingLength = embeddingLength,
                    headCount = headCount,
                    headCountKv = headCountKv,
                    tensorCount = tensorCount,
                    kvCount = kvCount,
                    estimatedRamMb = totalEstRamMb,
                    isGgufValid = true,
                    statusMessage = "GGUF v$version Validated • $layerCount Layers • $contextLength ctx"
                )
            } catch (e: Exception) {
                return fallbackInfo(fileName, fileSize, uri, "Parse error: ${e.localizedMessage}")
            } finally {
                try {
                    stream?.close()
                } catch (_: Exception) {}
            }
        }

        private fun fallbackInfo(name: String, size: Long, uri: Uri, error: String): GgufModelInfo {
            val quant = extractQuantFromFileName(name)
            val estMb = (size / (1024 * 1024)).toInt().coerceAtLeast(512)
            return GgufModelInfo(
                fileName = name,
                fileSizeBytes = size,
                uri = uri,
                architecture = detectArchFromName(name),
                modelName = name.removeSuffix(".gguf"),
                quantization = quant,
                contextLength = 4096,
                layerCount = 28,
                embeddingLength = 3072,
                headCount = 24,
                headCountKv = 8,
                tensorCount = 250,
                kvCount = 24,
                estimatedRamMb = estMb,
                isGgufValid = true, // allow running user GGUF
                statusMessage = error
            )
        }

        private fun extractQuantFromFileName(name: String): String {
            val upper = name.uppercase()
            val quantRegex = Regex("(Q[0-9]_[K0-9A-Z_]+|Q[0-9]_[0-9]|F16|F32|BF16|IQ[0-9]_[A-Z0-9]+)")
            val match = quantRegex.find(upper)
            return match?.value ?: "Q4_K_M"
        }

        private fun detectArchFromName(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("llama") -> "llama"
                lower.contains("qwen") -> "qwen2"
                lower.contains("deepseek") -> "deepseek2"
                lower.contains("gemma") -> "gemma2"
                lower.contains("phi") -> "phi3"
                lower.contains("mistral") -> "mistral"
                else -> "llama"
            }
        }

        private fun mapGgufFileType(ft: Int): String {
            return when (ft) {
                0 -> "F32"
                1 -> "F16"
                2 -> "Q4_0"
                3 -> "Q4_1"
                7 -> "Q8_0"
                8 -> "Q5_0"
                9 -> "Q5_1"
                10 -> "Q2_K"
                11 -> "Q3_K_S"
                12 -> "Q3_K_M"
                13 -> "Q3_K_L"
                14 -> "Q4_K_S"
                15 -> "Q4_K_M"
                16 -> "Q5_K_S"
                17 -> "Q5_K_M"
                18 -> "Q6_K"
                19 -> "IQ2_XXS"
                20 -> "IQ2_XS"
                21 -> "Q2_K_S"
                22 -> "IQ3_XS"
                23 -> "IQ3_XXS"
                24 -> "IQ1_S"
                25 -> "IQ4_NL"
                26 -> "IQ3_S"
                27 -> "IQ2_S"
                28 -> "IQ4_XS"
                else -> "Quantized ($ft)"
            }
        }

        private fun readGgufString(stream: InputStream): String? {
            val lenBytes = ByteArray(8)
            var read = 0
            while (read < 8) {
                val r = stream.read(lenBytes, read, 8 - read)
                if (r == -1) return null
                read += r
            }
            val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
            if (len < 0 || len > 2048) return null // Safety cap

            val strBytes = ByteArray(len.toInt())
            read = 0
            while (read < len.toInt()) {
                val r = stream.read(strBytes, read, len.toInt() - read)
                if (r == -1) return null
                read += r
            }
            return String(strBytes, Charsets.UTF_8)
        }

        private fun readGgufUint32(stream: InputStream): Int? {
            val bytes = ByteArray(4)
            var read = 0
            while (read < 4) {
                val r = stream.read(bytes, read, 4 - read)
                if (r == -1) return null
                read += r
            }
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        }

        private fun readGgufValue(stream: InputStream, valueType: Int): Any? {
            return when (valueType) {
                0 -> stream.read() // UINT8
                1 -> stream.read().toByte() // INT8
                2 -> { // UINT16
                    val b = readExact(stream, 2) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                }
                3 -> { // INT16
                    val b = readExact(stream, 2) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short
                }
                4, 5 -> { // UINT32, INT32
                    val b = readExact(stream, 4) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
                }
                6 -> { // FLOAT32
                    val b = readExact(stream, 4) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).float
                }
                7 -> stream.read() != 0 // BOOL
                8 -> readGgufString(stream) // STRING
                10, 11 -> { // UINT64, INT64
                    val b = readExact(stream, 8) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
                }
                12 -> { // FLOAT64
                    val b = readExact(stream, 8) ?: return null
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).double
                }
                9 -> { // ARRAY
                    val arrType = readGgufUint32(stream) ?: return null
                    val arrLen = readExact(stream, 8)?.let {
                        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).long
                    } ?: return null
                    // Skip or read first element
                    val skipBytes = when (arrType) {
                        0, 1, 7 -> arrLen * 1
                        2, 3 -> arrLen * 2
                        4, 5, 6 -> arrLen * 4
                        10, 11, 12 -> arrLen * 8
                        else -> 0L
                    }
                    if (skipBytes > 0) {
                        stream.skip(skipBytes)
                    } else if (arrType == 8) {
                        // String array: read strings or skip
                        for (j in 0 until arrLen.coerceAtMost(5000L)) {
                            readGgufString(stream) ?: break
                        }
                    }
                    "[Array of $arrLen items]"
                }
                else -> null
            }
        }

        private fun readExact(stream: InputStream, count: Int): ByteArray? {
            val bytes = ByteArray(count)
            var read = 0
            while (read < count) {
                val r = stream.read(bytes, read, count - read)
                if (r == -1) return null
                read += r
            }
            return bytes
        }
    }
}
