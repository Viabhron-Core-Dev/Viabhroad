import re

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "r") as f:
    content = f.read()

old_launcher = """    val textDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_TRIGGERED | uri=[${it.toString()}]")
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val importsDir = File(context.filesDir, "imported_dicts")
                        if (!importsDir.exists()) importsDir.mkdirs()
                        
                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_DIR_STATUS | path=[${importsDir.absolutePath}] | exists=[${importsDir.exists()}] | is_directory=[${importsDir.isDirectory}]")
                        
                        val identifierLine = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            reader.readLine()
                        }?.trim() ?: ""

                        val existingFiles = importsDir.listFiles() ?: emptyArray()
                        for (existingFile in existingFiles) {
                            val existingFirstLine = existingFile.bufferedReader().use { reader -> reader.readLine() }?.trim() ?: ""
                            if (existingFirstLine.isNotEmpty() && existingFirstLine == identifierLine) {
                                existingFile.delete()
                                TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_DUPLICATE_REMOVED | old_file=[${existingFile.name}]")
                            }
                        }

                        // We extract the file name or generate a unique one
                        val fileName = "imported_${System.currentTimeMillis()}.txt"
                        val destinationFile = File(importsDir, fileName)
                        
                        context.contentResolver.openInputStream(it)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_WRITE_COMPLETE | destination=[${destinationFile.absolutePath}] | size_bytes=[${destinationFile.length()}]")
                    }
                    withContext(Dispatchers.Main) {
                        isImportingDictionary = true
                    }
                    
                    val importEngine = DictionaryEngine(context)
                    importEngine.onReadyCallback = {
                        isImportingDictionary = false
                        Toast.makeText(context, "Dictionary imported successfully.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_FAILED | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                    Toast.makeText(context, "Failed to import dict: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }"""

new_launcher = """    val textDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_TRIGGERED | uri=[${it.toString()}]")
            scope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        isImportingDictionary = true
                    }
                    withContext(Dispatchers.IO) {
                        val importsDir = File(context.filesDir, "imported_dicts")
                        if (!importsDir.exists()) importsDir.mkdirs()

                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_DIR_STATUS | path=[${importsDir.absolutePath}] | exists=[${importsDir.exists()}] | is_directory=[${importsDir.isDirectory}]")

                        val identifierLine = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            reader.readLine()
                        }?.trim() ?: ""

                        val existingFiles = importsDir.listFiles() ?: emptyArray()
                        for (existingFile in existingFiles) {
                            val existingFirstLine = existingFile.bufferedReader().use { reader -> reader.readLine() }?.trim() ?: ""
                            if (existingFirstLine.isNotEmpty() && existingFirstLine == identifierLine) {
                                existingFile.delete()
                                TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_DUPLICATE_REMOVED | old_file=[${existingFile.name}]")
                            }
                        }

                        val fileName = "imported_${System.currentTimeMillis()}.txt"
                        val destinationFile = File(importsDir, fileName)

                        context.contentResolver.openInputStream(it)?.use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_WRITE_COMPLETE | destination=[${destinationFile.absolutePath}] | size_bytes=[${destinationFile.length()}]")

                        val importEngine = DictionaryEngine(context, autoLoad = false)
                        importEngine.loadCombinedDictionary(destinationFile.inputStream(), destinationFile.name, destinationFile.length())
                    }
                    Toast.makeText(context, "Dictionary imported successfully.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_FAILED | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                    Toast.makeText(context, "Failed to import dict: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    withContext(Dispatchers.Main) {
                        isImportingDictionary = false
                    }
                }
            }
        }
    }"""

content = content.replace(old_launcher, new_launcher)

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "w") as f:
    f.write(content)

