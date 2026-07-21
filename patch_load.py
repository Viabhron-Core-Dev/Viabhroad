with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_func = """    suspend fun loadCombinedDictionary(inputStream: InputStream, sourceFileName: String, sourceFileSize: Long) {
        withContext(Dispatchers.IO) {
            parseMutex.withLock {
                val startTime = System.currentTimeMillis()
                var wordsInserted = 0
                var bigramsInserted = 0
                val bigramWordCap = 3000
                var wordsWithBigrams = 0
                val wordsGrantedBigrams = mutableSetOf<String>()

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

                                if (!wordsGrantedBigrams.contains(cw)) {
                                    if (wordsWithBigrams >= bigramWordCap) continue
                                    wordsGrantedBigrams.add(cw)
                                    wordsWithBigrams++
                                }
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
                    TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_COMBINED_COMPLETE | words_inserted=[${wordsInserted}] | bigrams_inserted=[${bigramsInserted}] | bigram_words_capped=[${wordsWithBigrams}] | time_ms=[${timeMs}]")
                }
            }
        }
    }"""

new_func = """    suspend fun loadCombinedDictionary(inputStream: InputStream, sourceFileName: String, sourceFileSize: Long) {
        withContext(Dispatchers.IO) {
            parseMutex.withLock {
                val startTime = System.currentTimeMillis()
                var wordsParsed = 0
                var trieWordsInserted = 0
                var bigramsInserted = 0
                val bigramWordCap = 3000
                var wordsWithBigrams = 0
                val wordsGrantedBigrams = mutableSetOf<String>()
                val MAX_IN_MEMORY_WORDS = 12000

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
                                wordsParsed++

                                if (trieWordsInserted < MAX_IN_MEMORY_WORDS) {
                                    insertWord(word, freq)
                                    trieWordsInserted++
                                }
                            } else if (trimmedLine.startsWith("bigram=")) {
                                val cw = currentWord ?: continue
                                val bParts = trimmedLine.removePrefix("bigram=").split(",")
                                val nextWord = bParts.getOrNull(0)?.trim()
                                if (nextWord.isNullOrBlank()) continue
                                val bFreq = bParts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1

                                if (!wordsGrantedBigrams.contains(cw)) {
                                    if (wordsWithBigrams >= bigramWordCap) continue
                                    wordsGrantedBigrams.add(cw)
                                    wordsWithBigrams++
                                }
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
                    TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "IMPORT_COMBINED_COMPLETE | words_parsed=[${wordsParsed}] | trie_inserted=[${trieWordsInserted}] | bigrams_inserted=[${bigramsInserted}] | bigram_words_capped=[${wordsWithBigrams}] | time_ms=[${timeMs}]")
                }
            }
        }
    }"""

if old_func in content:
    content = content.replace(old_func, new_func)
    with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Failed to find exact old function, using regex")
    import re
    # use regex to replace everything from "suspend fun loadCombinedDictionary" up to the next function
    content = re.sub(r'    suspend fun loadCombinedDictionary.*?TheLogKeeper\.getInstance.*?}\n        }\n    }', new_func, content, flags=re.DOTALL)
    with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
        f.write(content)
    print("Regex patched")

