package com.example.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsScreen(onClose: () -> Unit) {
    var autoCorrectAggressiveness by remember { mutableStateOf(0.5f) }

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
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Dictionaries", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { /* TODO: Launch file picker to load heliboard dict */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import HeliBoard Dictionary (.en_US_dict etc)")
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
                onClick = { /* TODO: Open personal dictionary manager */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Custom Words")
            }
        }
    }
}
