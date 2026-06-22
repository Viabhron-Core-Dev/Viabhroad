package com.example.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDictionaryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { ClipboardDatabase.getDatabase(context).personalDictionaryDao() }
    val scope = rememberCoroutineScope()
    val words by dao.getAllWords().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Dictionary") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Word")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(words) { item ->
                ListItem(
                    headlineContent = { Text(item.word) },
                    supportingContent = { if (!item.shortcut.isNullOrBlank()) Text("Shortcut: ${item.shortcut}") },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { dao.delete(item) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
        
        if (showAddDialog) {
            AddWordDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { word, shortcut ->
                    scope.launch {
                        dao.insert(PersonalDictionaryItem(word = word, shortcut = shortcut.takeIf { it.isNotBlank() }))
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun AddWordDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var word by remember { mutableStateOf("") }
    var shortcut by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Word") },
        text = {
            Column {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Word") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = shortcut,
                    onValueChange = { shortcut = it },
                    label = { Text("Shortcut (Optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(word.trim(), shortcut.trim()) },
                enabled = word.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
