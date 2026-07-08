import re
with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

new_load_default = """    private fun loadDefaultDictionary() {
        pendingLoads.set(1)
        try {
            val stream = context.resources.openRawResource(R.raw.en_wordlist)
            loadCombinedDictionary(stream)
        } catch (e: Exception) {
            e.printStackTrace()
            checkIfReady()
        }
        loadImportedDictionaries()
    }

    fun loadCombinedDictionary(inputStream: InputStream) {
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

# find loadDefaultDictionary
pattern = r'    private fun loadDefaultDictionary\(\) \{[\s\S]*?    \}'
content = re.sub(pattern, new_load_default, content, count=1)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

