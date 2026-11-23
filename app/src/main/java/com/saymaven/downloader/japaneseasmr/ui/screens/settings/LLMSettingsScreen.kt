package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager

private data class LlmPreset(
    val name: String,
    val url: String,
    val defaultModel: String,
    val note: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val initialUrl by viewModel.llmApiUrl.collectAsState()
    val initialKey by viewModel.llmApiKey.collectAsState()
    val initialModel by viewModel.llmModel.collectAsState()
    val initialPrompt by viewModel.llmPrompt.collectAsState()
    val initialConcurrency by viewModel.llmConcurrency.collectAsState()

    var apiUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var apiKey by remember(initialKey) { mutableStateOf(initialKey) }
    var modelName by remember(initialModel) { mutableStateOf(initialModel) }
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var concurrency by remember(initialConcurrency) { mutableIntStateOf(initialConcurrency) }

    val presets = remember {
        listOf(
            LlmPreset(
                name = "Claude",
                url = "https://api.anthropic.com/v1",
                defaultModel = "claude-haiku-5-5",
                note = "Anthropic Claude API"
            ),
            LlmPreset(
                name = "Gemini",
                url = "https://generativelanguage.googleapis.com/v1beta/openai",
                defaultModel = "gemini-3.5-flash-lite",
                note = "Google AI Studio"
            ),
            LlmPreset(
                name = "DeepSeek",
                url = "https://api.deepseek.com/v1",
                defaultModel = "deepseek-chat",
                note = "Sangat ramah dialog dewasa & murah"
            ),
            LlmPreset(
                name = "Groq",
                url = "https://api.groq.com/openai/v1",
                defaultModel = "qwen/qwen3.8-27b",
                note = "Kecepatan inferensi tinggi"
            ),
            LlmPreset(
                name = "OpenAI",
                url = "https://api.openai.com/v1",
                defaultModel = "gpt-4o-mini",
                note = "OpenAI Official"
            ),
            LlmPreset(
                name = "OpenRouter",
                url = "https://openrouter.ai/api/v1",
                defaultModel = "anthropic/claude-haiku-5.5",
                note = "Multi-provider hub"
            ),
            LlmPreset(
                name = "Ollama",
                url = "http://192.168.1.100:11434/v1",
                defaultModel = "qwen2.5:7b",
                note = "AI Lokal LAN"
            )
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_llm_config),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.setLlmConfig(apiUrl.trim(), apiKey.trim(), modelName.trim(), prompt.trim(), concurrency)
                        Toast.makeText(context, context.getString(R.string.action_save), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_llm_config_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Quick Preset Selection Chips
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Template Cepat (Presets)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pilih provider untuk mengisi endpoint URL dan model default secara otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = apiUrl == preset.url
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    apiUrl = preset.url
                                    modelName = preset.defaultModel
                                },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                }
            }

            // API Connection Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Koneksi API",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text(stringResource(R.string.settings_llm_api_url)) },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.settings_llm_api_key)) },
                        placeholder = { Text("sk-... / API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text(stringResource(R.string.settings_llm_model)) },
                        placeholder = { Text("claude-haiku-5-5 / deepseek-chat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Concurrency Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Konkurensi Request",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$concurrency request serentak",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Setel slider ke 1-2 jika menggunakan API Key gratis (Free Tier) agar tidak terkena batas request per menit (HTTP 429). Gunakan 3-5 jika menggunakan akun API berbayar dengan batas request tinggi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = concurrency.toFloat(),
                        onValueChange = { concurrency = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Master Prompt Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_llm_prompt),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { prompt = PreferencesManager.DEFAULT_LLM_PROMPT }
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Default", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text(PreferencesManager.DEFAULT_LLM_PROMPT) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Info Card mengenai konten dewasa/NSFW
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Tips Konten Dewasa & NSFW:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Untuk audio drama erotis (R-18), model seperti DeepSeek sangat direkomendasikan karena tidak menolak dialog intim. Jika provider LLM menolak teks karena filter keamanan, aplikasi otomatis mengalihkan terjemahan ke Google Translate tanpa error.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.setLlmConfig(apiUrl.trim(), apiKey.trim(), modelName.trim(), prompt.trim(), concurrency)
                    Toast.makeText(context, context.getString(R.string.action_save), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
