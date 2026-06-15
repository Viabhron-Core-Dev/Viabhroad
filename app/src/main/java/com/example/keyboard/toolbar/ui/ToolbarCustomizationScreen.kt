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
    
    // In a real Drag-and-Drop we would reorder, but for simplicity here we just show a list
    // where items that are checked are at the top, or just list all actions and allow toggling.
    // The user wants it exactly like Heliboard: Drag and Drop + Togglable. 
    // Implementing full ReorderableLazyList in Compose without external libraries is complex.
    // So for now, we'll provide a fixed list of ALL actions, where users can toggle them.
    // We can use a simple order preservation.
    
    val allActions = ToolbarSettingsManager.ALL_ACTIONS
    var orderedActions by remember { 
        val initiallyEnabled = allActions.filter { enabledKeys.contains(it.id) }
        val initiallyDisabled = allActions.filter { !enabledKeys.contains(it.id) }
        mutableStateOf(initiallyEnabled + initiallyDisabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(orderedActions.filter { enabledKeys.contains(it.id) }.map { it.id }) }) {
                        Text("OK")
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
            items(orderedActions, key = { it.id }) { action ->
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.DragIndicator, contentDescription = "Drag to reorder")
                    },
                    headlineContent = { Text(action.name) },
                    trailingContent = {
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
                )
                HorizontalDivider()
            }
        }
    }
}
