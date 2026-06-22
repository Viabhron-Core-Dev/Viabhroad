package com.example.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.logkeeper.ui.LogKeeperScreen

sealed class SettingsRoute {
    object Main : SettingsRoute()
    object ToolbarCustomization : SettingsRoute()
    object DictionarySettings : SettingsRoute()
    object LogKeeper : SettingsRoute()
}

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentRoute) {
                        is SettingsRoute.Main -> {
                            Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = { Text("Settings") },
                                        navigationIcon = {
                                            IconButton(onClick = { finish() }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        }
                                    )
                                }
                            ) { paddingValues ->
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Dictionary & Prediction") },
                                            supportingContent = { Text("Configure dictionary and auto-correct") },
                                            modifier = Modifier.clickable { currentRoute = SettingsRoute.DictionarySettings }
                                        )
                                        HorizontalDivider()
                                    }
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Toolbar Customization") },
                                            supportingContent = { Text("Customize the extra action toolbar") },
                                            modifier = Modifier.clickable { currentRoute = SettingsRoute.ToolbarCustomization }
                                        )
                                        HorizontalDivider()
                                    }
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Log Keeper") },
                                            supportingContent = { Text("View diagnostic logs") },
                                            modifier = Modifier.clickable { currentRoute = SettingsRoute.LogKeeper }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                        is SettingsRoute.ToolbarCustomization -> {
                            com.example.keyboard.toolbar.ui.ToolbarCustomizationScreen(onClose = { currentRoute = SettingsRoute.Main })
                        }
                        is SettingsRoute.DictionarySettings -> {
                            DictionarySettingsScreen(onClose = { currentRoute = SettingsRoute.Main })
                        }
                        is SettingsRoute.LogKeeper -> {
                            LogKeeperScreen(onClose = { currentRoute = SettingsRoute.Main })
                        }
                    }
                }
            }
        }
    }
}
