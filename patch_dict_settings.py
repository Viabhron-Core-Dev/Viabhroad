import re

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "r") as f:
    content = f.read()

# 1. Add isMigratingDatabase
content = content.replace("var isImportingDictionary by remember { mutableStateOf(false) }",
                          "var isImportingDictionary by remember { mutableStateOf(false) }\n    var isMigratingDatabase by remember { mutableStateOf(false) }")

# 2. Remove migrateWordsToDatabase from textDictLauncher
content = content.replace("""                        val importEngine = DictionaryEngine(context, autoLoad = false)
                        importEngine.loadCombinedDictionary(destinationFile.inputStream(), destinationFile.name, destinationFile.length())
                        importEngine.migrateWordsToDatabase(destinationFile.inputStream())
                    }
                    Toast.makeText(context, "Dictionary imported successfully.", Toast.LENGTH_LONG).show()""",
                          """                        val importEngine = DictionaryEngine(context, autoLoad = false)
                        importEngine.loadCombinedDictionary(destinationFile.inputStream(), destinationFile.name, destinationFile.length())
                    }
                    Toast.makeText(context, "Dictionary imported successfully.", Toast.LENGTH_LONG).show()""")

# 3. Add back button guard
content = content.replace("IconButton(onClick = { if (!isImportingDictionary) onClose() })",
                          "IconButton(onClick = { if (!isImportingDictionary && !isMigratingDatabase) onClose() })")

# 4. Modify loading overlay conditional
old_overlay = """        if (isImportingDictionary) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing dictionary, please wait...")
                }
            }"""
new_overlay = """        if (isImportingDictionary || isMigratingDatabase) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (isImportingDictionary) "Importing dictionary, please wait..."
                        else "Building database, please wait..."
                    )
                }
            }"""
content = content.replace(old_overlay, new_overlay)

# 5. Add new button in Dictionaries section
old_dict_section = """            Text("Dictionaries", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { textDictLauncher.launch(arrayOf("text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Basic Text Dictionary (.txt)")
            }"""

new_dict_section = """            Text("Dictionaries", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { textDictLauncher.launch(arrayOf("text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Basic Text Dictionary (.txt)")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val importsDir = File(context.filesDir, "imported_dicts")
                        val existingFile = importsDir.listFiles()?.firstOrNull()
                        if (existingFile == null) {
                            Toast.makeText(context, "Import a dictionary first.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            withContext(Dispatchers.Main) {
                                isMigratingDatabase = true
                            }
                            withContext(Dispatchers.IO) {
                                val migrationEngine = DictionaryEngine(context, autoLoad = false)
                                migrationEngine.migrateWordsToDatabase(existingFile.inputStream())
                            }
                            Toast.makeText(context, "Database build complete.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DB_MIGRATION_FAILED | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                            Toast.makeText(context, "Database build failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            withContext(Dispatchers.Main) {
                                isMigratingDatabase = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Build Fast Lookup Database (Step 2)")
            }"""

content = content.replace(old_dict_section, new_dict_section)

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "w") as f:
    f.write(content)
