package com.example.logkeeper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.logkeeper.TheLogKeeper
import com.example.logkeeper.data.LogEntry
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogKeeperScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val logKeeper = remember { TheLogKeeper.getInstance(context) }
    var isMasterSwitchOn by remember { mutableStateOf(logKeeper.isMasterSwitchOn) }
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    var selectedInterval by remember { mutableStateOf(1L) } // in hours

    LaunchedEffect(selectedInterval) {
        logs = if (selectedInterval == -1L) {
            logKeeper.getAllLogsStatic()
        } else {
            logKeeper.getLogsSince(selectedInterval * 60 * 60 * 1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Log Keeper") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text("Master Switch")
                    Switch(
                        checked = isMasterSwitchOn,
                        onCheckedChange = {
                            isMasterSwitchOn = it
                            logKeeper.isMasterSwitchOn = it
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            )
        }
    ) { paddingInfo ->
        Column(modifier = Modifier.padding(paddingInfo).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IntervalButton("1H", 1L, selectedInterval) { selectedInterval = it }
                IntervalButton("6H", 6L, selectedInterval) { selectedInterval = it }
                IntervalButton("12H", 12L, selectedInterval) { selectedInterval = it }
                IntervalButton("24H", 24L, selectedInterval) { selectedInterval = it }
                IntervalButton("All", -1L, selectedInterval) { selectedInterval = it }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    val logsText = formatLogs(logs)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("logs", logsText))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }

                Button(onClick = {
                    val logsText = formatLogs(logs)
                    try {
                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "viaboard_logs.txt")
                        FileWriter(file).use { it.write(logsText) }
                        Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Download")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    LogItemCard(log)
                }
            }
        }
    }
}

@Composable
fun IntervalButton(label: String, hours: Long, selectedState: Long, onClick: (Long) -> Unit) {
    FilledTonalButton(
        onClick = { onClick(hours) },
        colors = if (selectedState == hours) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label)
    }
}

@Composable
fun LogItemCard(log: LogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
            Text(text = "[$date] ${log.type} - ${log.component}", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = log.message, style = MaterialTheme.typography.bodyMedium)
            if (log.stackTrace != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = log.stackTrace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun formatLogs(logs: List<LogEntry>): String {
    val sb = java.lang.StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    for (log in logs) {
        sb.append("[${sdf.format(Date(log.timestamp))}] ${log.type} / ${log.component}\n")
        sb.append(log.message).append("\n")
        if (log.stackTrace != null) {
            sb.append(log.stackTrace).append("\n")
        }
        sb.append("----------------------------\n")
    }
    return sb.toString()
}
