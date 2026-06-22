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
                    Text("Use Lightweight Transformer Model")
                    Text(
                        "Future support for ML-based completions (TensorLite).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useTransformerEngine,
                    onCheckedChange = {
                        useTransformerEngine = it
                        prefs.edit().putBoolean("use_transformer", it).apply()
                        if (it) {
                            Toast.makeText(context, "Note: Transformer model is a placeholder feature.", Toast.LENGTH_SHORT).show()
                        }
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
                Text("Import Text Dictionary (.txt)")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { /* TODO: Launch file picker to load transformer */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Lightweight Transformer Model (.tflite)")
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
