import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_loadCombinedDictionary_sig = "fun loadCombinedDictionary(inputStream: InputStream) {"
new_loadCombinedDictionary_sig = "fun loadCombinedDictionary(inputStream: InputStream, sourceFileName: String, sourceFileSize: Long) {"
content = content.replace(old_loadCombinedDictionary_sig, new_loadCombinedDictionary_sig)

insertion_point = """                    }
                }
            } catch (e: Exception) {"""
new_insertion = """                    }
                }
                saveCacheToDisk(sourceFileName, sourceFileSize)
            } catch (e: Exception) {"""
content = content.replace(insertion_point, new_insertion)

old_loadImported = """                                if (firstLine.startsWith("dictionary=")) {
                                    loadCombinedDictionary(file.inputStream())
                                } else {"""
new_loadImported = """                                if (firstLine.startsWith("dictionary=")) {
                                    loadCombinedDictionary(file.inputStream(), file.name, file.length())
                                } else {"""
content = content.replace(old_loadImported, new_loadImported)


additions = """    companion object {
        private const val CACHE_FORMAT_VERSION = 1
    }

    private fun getCacheFile(): java.io.File {
        val cacheDir = java.io.File(context.filesDir, "dict_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return java.io.File(cacheDir, "trie_cache.bin")
    }

    private fun writeTrieNode(out: java.io.DataOutputStream, node: TrieNode) {
        out.writeBoolean(node.isWord)
        out.writeInt(node.frequency)
        out.writeInt(node.children.size)
        for ((char, child) in node.children) {
            out.writeChar(char.code)
            writeTrieNode(out, child)
        }
    }

    private fun saveCacheToDisk(sourceFileName: String, sourceFileSize: Long) {
        val cacheFile = getCacheFile()
        val tempFile = java.io.File(cacheFile.parentFile, "trie_cache.bin.tmp")
        try {
            java.io.DataOutputStream(java.io.BufferedOutputStream(tempFile.outputStream())).use { out ->
                out.writeInt(CACHE_FORMAT_VERSION)
                out.writeUTF(sourceFileName)
                out.writeLong(sourceFileSize)

                writeTrieNode(out, trie)

                out.writeInt(bigrams.size)
                for ((word, nextMap) in bigrams) {
                    out.writeUTF(word)
                    out.writeInt(nextMap.size)
                    for ((nextWord, freq) in nextMap) {
                        out.writeUTF(nextWord)
                        out.writeInt(freq)
                    }
                }
            }
            if (cacheFile.exists()) cacheFile.delete()
            tempFile.renameTo(cacheFile)
            android.util.Log.d("DictionaryEngine", "Cache written successfully. size=${cacheFile.length()} bytes, source=$sourceFileName")
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
        }
    }

    init {"""
content = content.replace("    init {", additions)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

