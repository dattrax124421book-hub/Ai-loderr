package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.EngineConfig
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InferenceSettingsSheet(
    config: EngineConfig,
    systemPrompt: String,
    onSaveConfig: (EngineConfig) -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var temp by remember { mutableFloatStateOf(config.temperature) }
    var topP by remember { mutableFloatStateOf(config.topP) }
    var threads by remember { mutableIntStateOf(config.threads) }
    var gpuLayers by remember { mutableIntStateOf(config.gpuLayers) }
    var vulkanEnabled by remember { mutableStateOf(config.vulkanEnabled) }
    var maxTokens by remember { mutableIntStateOf(config.maxTokens) }
    var currentSysPrompt by remember { mutableStateOf(systemPrompt) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceBorder)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Inference Parameters",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tune speed vs precision on Helio G100-Ultra",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Reset to Helio G100 defaults
                    IconButton(
                        onClick = {
                            temp = 0.7f
                            topP = 0.9f
                            threads = 4
                            gpuLayers = 24
                            vulkanEnabled = true
                            maxTokens = 1024
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = AccentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CPU Threads
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CPU Threads", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$threads Threads ${if (threads == 4) "(Helio G100 Recommended)" else ""}",
                                color = if (threads == 4) AccentEmerald else AccentCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = threads.toFloat(),
                            onValueChange = { threads = it.toInt() },
                            valueRange = 1f..8f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("threads_slider")
                        )
                        Text(
                            text = "4 threads optimizes the 2x A76 Performance cores without causing thermal throttling on Redmi Note 14 Pro.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vulkan & GPU Layers
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Mali-G57 Vulkan Acceleration", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Hardware matrix multiplication", color = TextTertiary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = vulkanEnabled,
                                onCheckedChange = { vulkanEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = DarkSurfaceElevated
                                ),
                                modifier = Modifier.testTag("vulkan_switch")
                            )
                        }

                        if (vulkanEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GPU Layers Offload", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    text = "$gpuLayers Layers",
                                    color = AccentEmerald,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = gpuLayers.toFloat(),
                                onValueChange = { gpuLayers = it.toInt() },
                                valueRange = 0f..32f,
                                steps = 31,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentEmerald,
                                    activeTrackColor = AccentEmerald,
                                    inactiveTrackColor = DarkSurfaceBorder
                                ),
                                modifier = Modifier.testTag("gpu_layers_slider")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sampling Settings (Temperature & Top P)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Temperature
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Temperature", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%.2f", temp),
                                color = AccentCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = temp,
                            onValueChange = { temp = it },
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("temperature_slider")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Top P
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Top-P Nucleus Sampling", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format("%.2f", topP),
                                color = AccentCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = topP,
                            onValueChange = { topP = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("top_p_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // System Prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("System Prompt", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PromptPill("General") {
                                currentSysPrompt = "You are a concise, helpful local AI assistant running on mobile hardware."
                            }
                            PromptPill("Code Expert") {
                                currentSysPrompt = "You are an expert software engineer specializing in Kotlin, C++, and algorithms. Provide clean, production-ready code."
                            }
                            PromptPill("Deep Thinker") {
                                currentSysPrompt = "Think step-by-step with rigorous logic and clear reasoning before providing your final answer."
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = currentSysPrompt,
                            onValueChange = { currentSysPrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .testTag("system_prompt_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSaveConfig(
                            config.copy(
                                temperature = temp,
                                topP = topP,
                                threads = threads,
                                gpuLayers = if (vulkanEnabled) gpuLayers else 0,
                                vulkanEnabled = vulkanEnabled,
                                maxTokens = maxTokens
                            )
                        )
                        onSaveSystemPrompt(currentSysPrompt)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_settings_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Apply Parameters", color = Color(0xFF021B2A), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun PromptPill(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = title, color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
