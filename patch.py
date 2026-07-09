import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_loadImportedDictionaries = """    private fun loadImportedDictionaries() {
        scope.launch {
            try {
                val importsDir = java.io.File(context.filesDir, "imported_dicts")
                if (importsDir.exists() && importsDir.isDirectory) {
                    val files = importsDir.listFiles()?.filter { it.isFile }
                    if (files != null && files.isNotEmpty()) {
                        pendingLoads.addAndGet(files.size)
                        isReady = false
                        for (file in files) {
                            try {
                                val firstLine = file.useLines { lines ->
                                    lines.firstOrNull { it.isNotBlank() }
                                }?.trim() ?: ""
                                
                                if (firstLine.startsWith("dictionary=")) {
                                    loadCombinedDictionary(file.inputStream())
                                } else {
                                    loadTextDictionary(file.inputStream())
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                checkIfReady()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }"""

new_loadImportedDictionaries = """    private fun loadImportedDictionaries() {
        scope.launch {
            try {
                val importsDir = java.io.File(context.filesDir, "imported_dicts")
                val dirExists = importsDir.exists()
                val isDir = importsDir.isDirectory
                TheLogKeeper.logEvent("IMPORT_SCAN_START | dir_exists=[${dirExists}] | is_directory=[${isDir}]")
                
                if (dirExists && isDir) {
                    val files = importsDir.listFiles()?.filter { it.isFile }
                    if (files != null && files.isNotEmpty()) {
                        val names = files.joinToString(",") { it.name }
                        TheLogKeeper.logEvent("IMPORT_FILES_FOUND | count=[${files.size}] | names=[${names}]")
                        
                        pendingLoads.addAndGet(files.size)
                        isReady = false
                        for (file in files) {
                            try {
                                val firstLine = file.useLines { lines ->
                                    lines.firstOrNull { it.isNotBlank() }
                                }?.trim() ?: ""
                                
                                val truncatedFirstLine = if (firstLine.length > 80) firstLine.take(80) else firstLine
                                val routedTo = if (firstLine.startsWith("dictionary=")) "combined" else "text"
                                TheLogKeeper.logEvent("IMPORT_FILE_START | name=[${file.name}] | size_bytes=[${file.length()}] | first_line=[${truncatedFirstLine}] | routed_to=[${routedTo}]")
                                
                                if (firstLine.startsWith("dictionary=")) {
                                    loadCombinedDictionary(file.inputStream())
                                } else {
                                    loadTextDictionary(file.inputStream())
                                }
                            } catch (e: Exception) {
                                TheLogKeeper.logEvent("IMPORT_ERROR | file=[${file.name}] | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                                e.printStackTrace()
                                checkIfReady()
                            }
                        }
                    } else {
                        TheLogKeeper.logEvent("IMPORT_NO_FILES_FOUND")
                    }
                } else {
                    TheLogKeeper.logEvent("IMPORT_NO_FILES_FOUND")
                }
            } catch (e: Exception) {
                TheLogKeeper.logEvent("IMPORT_ERROR | file=[unknown] | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                e.printStackTrace()
            }
        }
    }"""

old_loadCombinedDictionary = """    fun loadCombinedDictionary(inputStream: InputStream) {
        scope.launch {
            try {
                var currentWord: String? = null
                inputStream.bufferedReader().useLines { lines ->
                    for (rawLine in lines) {
                        val trimmedLine = rawLine.trim()
                        if (trimmedLine.isBlank() || trimmedLine.startsWith("dictionary=")) continue

                        if (trimmedLine.startsWith("word=")) {
                            val parts = trimmedLine.removePrefix("word=").split(",")
                            val word = parts.getOrNull(0)?.trim()
                            if (word.isNullOrBlank()) continue
                            val freq = parts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1
                            currentWord = word
                            insertWord(word, freq)
                        } else if (trimmedLine.startsWith("bigram=")) {
                            val cw = currentWord ?: continue
                            val bParts = trimmedLine.removePrefix("bigram=").split(",")
                            val nextWord = bParts.getOrNull(0)?.trim()
                            if (nextWord.isNullOrBlank()) continue
                            val bFreq = bParts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1
                            val map = bigrams.getOrPut(cw) { mutableMapOf() }
                            map[nextWord] = bFreq
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                checkIfReady()
                android.util.Log.d("DictionaryEngine", "Combined dictionary loaded. bigrams.size=${bigrams.size}")
            }
        }
    }"""

new_loadCombinedDictionary = """    fun loadCombinedDictionary(inputStream: InputStream) {
        scope.launch {
            val startTime = System.currentTimeMillis()
            var wordsInserted = 0
            var bigramsInserted = 0
            try {
                var currentWord: String? = null
                inputStream.bufferedReader().useLines { lines ->
                    for (rawLine in lines) {
                        val trimmedLine = rawLine.trim()
                        if (trimmedLine.isBlank() || trimmedLine.startsWith("dictionary=")) continue

                        if (trimmedLine.startsWith("word=")) {
                            val parts = trimmedLine.removePrefix("word=").split(",")
                            val word = parts.getOrNull(0)?.trim()
                            if (word.isNullOrBlank()) continue
                            val freq = parts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1
                            currentWord = word
                            insertWord(word, freq)
                            wordsInserted++
                        } else if (trimmedLine.startsWith("bigram=")) {
                            val cw = currentWord ?: continue
                            val bParts = trimmedLine.removePrefix("bigram=").split(",")
                            val nextWord = bParts.getOrNull(0)?.trim()
                            if (nextWord.isNullOrBlank()) continue
                            val bFreq = bParts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1
                            val map = bigrams.getOrPut(cw) { mutableMapOf() }
                            map[nextWord] = bFreq
                            bigramsInserted++
                        }
                    }
                }
            } catch (e: Exception) {
                TheLogKeeper.logEvent("IMPORT_ERROR | file=[unknown] | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                e.printStackTrace()
            } finally {
                val timeMs = System.currentTimeMillis() - startTime
                checkIfReady()
                android.util.Log.d("DictionaryEngine", "Combined dictionary loaded. bigrams.size=${bigrams.size}")
                TheLogKeeper.logEvent("IMPORT_COMBINED_COMPLETE | words_inserted=[${wordsInserted}] | bigrams_inserted=[${bigramsInserted}] | time_ms=[${timeMs}]")
            }
        }
    }"""

if old_loadImportedDictionaries in content:
    content = content.replace(old_loadImportedDictionaries, new_loadImportedDictionaries)
    print("Replaced loadImportedDictionaries successfully")
else:
    print("Failed to replace loadImportedDictionaries")

if old_loadCombinedDictionary in content:
    content = content.replace(old_loadCombinedDictionary, new_loadCombinedDictionary)
    print("Replaced loadCombinedDictionary successfully")
else:
    print("Failed to replace loadCombinedDictionary")

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

