import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

new_functions = """    private fun readTrieNode(input: java.io.DataInputStream): TrieNode {
        val node = TrieNode()
        node.isWord = input.readBoolean()
        node.frequency = input.readInt()
        val childCount = input.readInt()
        repeat(childCount) {
            val char = input.readChar()
            node.children[char] = readTrieNode(input)
        }
        return node
    }

    private fun loadCacheFromDisk(expectedSourceFileName: String, expectedSourceFileSize: Long): Boolean {
        val cacheFile = getCacheFile()
        if (!cacheFile.exists()) return false
        val startTime = System.currentTimeMillis()
        return try {
            java.io.DataInputStream(java.io.BufferedInputStream(cacheFile.inputStream())).use { input ->
                val version = input.readInt()
                if (version != CACHE_FORMAT_VERSION) {
                    android.util.Log.d("DictionaryEngine", "CACHE_MISS | reason=version_mismatch")
                    return false
                }
                val savedName = input.readUTF()
                val savedSize = input.readLong()
                if (savedName != expectedSourceFileName || savedSize != expectedSourceFileSize) {
                    android.util.Log.d("DictionaryEngine", "CACHE_MISS | reason=source_mismatch")
                    return false
                }

                val loadedTrie = readTrieNode(input)

                val loadedBigrams = mutableMapOf<String, MutableMap<String, Int>>()
                val bigramCount = input.readInt()
                repeat(bigramCount) {
                    val word = input.readUTF()
                    val nextCount = input.readInt()
                    val map = mutableMapOf<String, Int>()
                    repeat(nextCount) {
                        map[input.readUTF()] = input.readInt()
                    }
                    loadedBigrams[word] = map
                }

                val loadedWords = mutableSetOf<String>()
                val wordCount = input.readInt()
                repeat(wordCount) {
                    loadedWords.add(input.readUTF())
                }

                // Only commit after everything above succeeded with no exception
                trie.children.clear()
                trie.children.putAll(loadedTrie.children)
                trie.isWord = loadedTrie.isWord
                trie.frequency = loadedTrie.frequency

                bigrams.clear()
                bigrams.putAll(loadedBigrams)

                allWordsSet.clear()
                allWordsSet.addAll(loadedWords)

                val elapsed = System.currentTimeMillis() - startTime
                android.util.Log.d("DictionaryEngine", "CACHE_HIT | words=${loadedWords.size} | bigram_entries=${loadedBigrams.size} | time_ms=$elapsed")
                true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            android.util.Log.w("DictionaryEngine", "CACHE_LOAD_FAILED | ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    init {"""

content = content.replace("    init {", new_functions)

old_load_logic = """                                if (firstLine.startsWith("dictionary=")) {
                                    loadCombinedDictionary(file.inputStream(), file.name, file.length())
                                } else {"""

new_load_logic = """                                if (firstLine.startsWith("dictionary=")) {
                                    if (!loadCacheFromDisk(file.name, file.length())) {
                                        loadCombinedDictionary(file.inputStream(), file.name, file.length())
                                    } else {
                                        checkIfReady()
                                    }
                                } else {"""

content = content.replace(old_load_logic, new_load_logic)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)
print("Patched successfully")
