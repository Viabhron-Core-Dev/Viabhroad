import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_load = """    fun loadCombinedDictionary(inputStream: InputStream, sourceFileName: String, sourceFileSize: Long) {
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
                saveCacheToDisk(sourceFileName, sourceFileSize)
            } catch (e: Exception) {
                TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_ERROR | file=[unknown] | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                e.printStackTrace()
            } finally {
                val timeMs = System.currentTimeMillis() - startTime
                checkIfReady()
                android.util.Log.d("DictionaryEngine", "Combined dictionary loaded. bigrams.size=${bigrams.size}")
                TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_COMBINED_COMPLETE | words_inserted=[${wordsInserted}] | bigrams_inserted=[${bigramsInserted}] | time_ms=[${timeMs}]")
            }
        }
    }"""
new_load = """    suspend fun loadCombinedDictionary(inputStream: InputStream, sourceFileName: String, sourceFileSize: Long) {
        withContext(Dispatchers.IO) {
            parseMutex.withLock {
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
                    saveCacheToDisk(sourceFileName, sourceFileSize)
                } catch (e: Exception) {
                    TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_ERROR | file=[unknown] | exception=[${e.javaClass.simpleName}] | message=[${e.message}]")
                    e.printStackTrace()
                } finally {
                    val timeMs = System.currentTimeMillis() - startTime
                    checkIfReady()
                    android.util.Log.d("DictionaryEngine", "Combined dictionary loaded. bigrams.size=${bigrams.size}")
                    TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_COMBINED_COMPLETE | words_inserted=[${wordsInserted}] | bigrams_inserted=[${bigramsInserted}] | time_ms=[${timeMs}]")
                }
            }
        }
    }"""
content = content.replace(old_load, new_load)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

