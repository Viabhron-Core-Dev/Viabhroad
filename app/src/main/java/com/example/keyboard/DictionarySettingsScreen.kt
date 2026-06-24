package com.example.keyboard

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsScreen(onClose: () -> Unit, onOpenPersonalDictionary: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
    
    var autoCorrectAggressiveness by remember { mutableStateOf(0.5f) }
    var useTransformerEngine by remember { mutableStateOf(prefs.getBoolean("use_transformer", false)) }
    
    val scope = rememberCoroutineScope()
    
    val tfliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val modelDir = File(context.filesDir, "transformer")
                        if (!modelDir.exists()) modelDir.mkdirs()
                        val destinationFile = File(modelDir, "model.tflite")
                        context.contentResolver.openInputStream(it)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Toast.makeText(context, "Transformer model imported!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import model: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val vocabLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val modelDir = File(context.filesDir, "transformer")
                        if (!modelDir.exists()) modelDir.mkdirs()
                        val destinationFile = File(modelDir, "vocab.txt")
                        context.contentResolver.openInputStream(it)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Toast.makeText(context, "Vocabulary file imported!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import vocab: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val textDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val importsDir = File(context.filesDir, "imported_dicts")
                        if (!importsDir.exists()) importsDir.mkdirs()
                        
                        // We extract the file name or generate a unique one
                        val fileName = "imported_${System.currentTimeMillis()}.txt"
                        val destinationFile = File(importsDir, fileName)
                        
                        context.contentResolver.openInputStream(it)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Toast.makeText(context, "Text dictionary imported. Reopen keyboard to apply!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import dict: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionary & Prediction") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("Auto-Correct Aggressiveness", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = autoCorrectAggressiveness,
                onValueChange = { autoCorrectAggressiveness = it },
                valueRange = 0f..1f,
                steps = 10
            )
            val levelText = when {
                autoCorrectAggressiveness < 0.2f -> "Off"
                autoCorrectAggressiveness < 0.5f -> "Mild"
                autoCorrectAggressiveness < 0.8f -> "Moderate"
                else -> "Aggressive"
            }
            Text("Current level: $levelText")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Engine Selection", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Built-in Transformer Model")
                    Text(
                        "Enables the lightweight (3MB) ML model for next-word completions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useTransformerEngine,
                    onCheckedChange = {
                        useTransformerEngine = it
                        prefs.edit().putBoolean("use_transformer", it).apply()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Dictionaries", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { textDictLauncher.launch(arrayOf("text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Basic Text Dictionary (.txt)")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Custom Transformer Model (Advanced)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "You can override the built-in 3MB model by importing your own TensorFlow Lite model (.tflite) and vocabulary file (.txt).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "If you import a custom model, the built-in model will be disabled automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { tfliteLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import .tflite")
                }
                OutlinedButton(
                    onClick = { vocabLauncher.launch(arrayOf("text/plain")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import Vocab")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Personal Dictionary", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenPersonalDictionary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Custom Words")
            }
        }
    }
}
