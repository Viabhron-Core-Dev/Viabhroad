import re

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.ui.Alignment" not in content:
    content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.Alignment")

old_state = """    val prefs = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
    
    var autoCorrectAggressiveness by remember { mutableStateOf(prefs.getFloat("autocorrect_aggressiveness", 1.0f)) }"""

new_state = """    val prefs = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
    
    var isImportingDictionary by remember { mutableStateOf(false) }
    var autoCorrectAggressiveness by remember { mutableStateOf(prefs.getFloat("autocorrect_aggressiveness", 1.0f)) }"""

content = content.replace(old_state, new_state)


old_toast = """                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_WRITE_COMPLETE | destination=[${destinationFile.absolutePath}] | size_bytes=[${destinationFile.length()}]")
                    }
                    Toast.makeText(context, "Text dictionary imported. Reopen keyboard to apply!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {"""

new_toast = """                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_WRITE_COMPLETE | destination=[${destinationFile.absolutePath}] | size_bytes=[${destinationFile.length()}]")
                    }
                    withContext(Dispatchers.Main) {
                        isImportingDictionary = true
                    }
                    
                    val importEngine = DictionaryEngine(context)
                    importEngine.onReadyCallback = {
                        isImportingDictionary = false
                        Toast.makeText(context, "Dictionary imported successfully.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {"""

content = content.replace(old_toast, new_toast)

old_nav = """                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }"""

new_nav = """                navigationIcon = {
                    IconButton(onClick = { if (!isImportingDictionary) onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }"""

content = content.replace(old_nav, new_nav)

old_body = """    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {"""

new_body = """    ) { paddingValues ->
        if (isImportingDictionary) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing dictionary, please wait...")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {"""

content = content.replace(old_body, new_body)

# And close the else block
old_end = """            Button(
                onClick = onOpenPersonalDictionary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Custom Words")
            }
        }
    }
}"""

new_end = """            Button(
                onClick = onOpenPersonalDictionary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Custom Words")
            }
        }
    }
}
}"""

content = content.replace(old_end, new_end)

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "w") as f:
    f.write(content)

