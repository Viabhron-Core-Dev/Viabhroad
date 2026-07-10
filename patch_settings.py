import re

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "r") as f:
    content = f.read()

# Add import if missing
if "import com.example.logkeeper.TheLogKeeper" not in content:
    content = content.replace("import java.io.File\n", "import java.io.File\nimport com.example.logkeeper.TheLogKeeper\n")

old_textDictLauncher = """    val textDictLauncher = rememberLauncherForActivityResult(
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
    }"""

new_textDictLauncher = """    val textDictLauncher = rememberLauncherForActivityResult(
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
                    Toast.makeText(context, "Text dictionary imported. Reopen keyboard to apply!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    TheLogKeeper.getInstance(context).log("INFO", "DictionarySettingsScreen", "DICT_IMPORT_FAILED | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                    Toast.makeText(context, "Failed to import dict: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }"""

if old_textDictLauncher in content:
    content = content.replace(old_textDictLauncher, new_textDictLauncher)
    print("Replaced textDictLauncher successfully")
else:
    print("Failed to replace textDictLauncher")

with open("app/src/main/java/com/example/keyboard/DictionarySettingsScreen.kt", "w") as f:
    f.write(content)

