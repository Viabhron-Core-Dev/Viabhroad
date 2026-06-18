package com.example.keyboard.toolbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.keyboard.toolbar.ToolbarSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarCustomizationScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var isEditingPinned by remember { mutableStateOf(false) }
    var isEditingExpanded by remember { mutableStateOf(false) }

    when {
        isEditingPinned -> {
            ToolbarKeyEditorScreen(
                title = "Select pinned toolbar keys",
                initialKeys = ToolbarSettingsManager.getPinnedKeys(context),
                onSave = { newKeys ->
                    ToolbarSettingsManager.savePinnedKeys(context, newKeys)
                    isEditingPinned = false
                },
                onCancel = { isEditingPinned = false }
            )
        }
        isEditingExpanded -> {
            ToolbarKeyEditorScreen(
                title = "Select toolbar keys",
                initialKeys = ToolbarSettingsManager.getToolbarKeys(context),
                onSave = { newKeys ->
                    ToolbarSettingsManager.saveToolbarKeys(context, newKeys)
                    isEditingExpanded = false
                },
                onCancel = { isEditingExpanded = false }
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Toolbar Customization") },
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
                ) {
                    ListItem(
                        headlineContent = { Text("Pinned toolbar keys") },
                        supportingContent = { Text("Customize buttons always visible on the toolbar") },
                        modifier = Modifier.clickable { isEditingPinned = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Toolbar keys") },
                        supportingContent = { Text("Customize buttons in the expandable toolbar menu") },
                        modifier = Modifier.clickable { isEditingExpanded = true }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarKeyEditorScreen(
    title: String,
    initialKeys: List<String>,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit
) {
    var enabledKeys by remember { mutableStateOf(initialKeys.toSet()) }
    
    val allActions = ToolbarSettingsManager.ALL_ACTIONS
    var orderedActions by remember { 
        val initiallyEnabledIds = initialKeys.filter { id -> allActions.any { it.id == id } }
        val initiallyEnabled = initiallyEnabledIds.mapNotNull { id -> allActions.find { it.id == id } }
        val initiallyDisabled = allActions.filter { !initialKeys.contains(it.id) }
        mutableStateOf(initiallyEnabled + initiallyDisabled)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(orderedActions, key = { it.id }) { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_drag_indicator),
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                androidx.compose.ui.res.painterResource(id = action.iconResId),
                                contentDescription = action.name,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = action.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = enabledKeys.contains(action.id),
                                onCheckedChange = { isChecked ->
                                    enabledKeys = if (isChecked) {
                                        enabledKeys + action.id
                                    } else {
                                        enabledKeys - action.id
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { 
                        // Reset logic. We don't have default keys passed here, so we skip standard default logic or just use empty for now.
                        // Leaving as standard text button
                    }) {
                        Text("Default")
                    }
                    Row(horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onSave(orderedActions.filter { enabledKeys.contains(it.id) }.map { it.id }) }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
