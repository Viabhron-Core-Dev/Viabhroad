with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_func = """    fun wordExists(word: String): Boolean {
        val lowerWord = word.lowercase()
        // Check personal dictionary
        try {
            val exists = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                personalDao.getByShortcut(lowerWord) != null
            }
            if (exists) return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        var current = trie
        for (char in lowerWord) {
            if (!current.children.containsKey(char)) {
                return false
            }
            current = current.children[char]!!
        }
        return current.isWord
    }"""

new_func = """    fun wordExists(word: String): Boolean {
        val lowerWord = word.lowercase()
        
        // 1. Check Trie (Hot cache)
        var current: TrieNode? = trie
        var foundInTrie = true
        for (char in lowerWord) {
            if (current == null || !current.children.containsKey(char)) {
                foundInTrie = false
                break
            }
            current = current.children[char]
        }
        if (foundInTrie && current?.isWord == true) return true

        // 2. Fallback to DBs (Personal + Main)
        try {
            val exists = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (personalDao.getByShortcut(lowerWord) != null) return@runBlocking true
                ClipboardDatabase.getDatabase(context).dictionaryWordDao().getExact(lowerWord) != null
            }
            if (exists) return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }"""

if old_func in content:
    content = content.replace(old_func, new_func)
    print("Patched wordExists")
else:
    print("Could not find wordExists")
    import re
    content = re.sub(r'    fun wordExists.*?return current\.isWord\n    }', new_func, content, flags=re.DOTALL)
    print("Regex patched wordExists")

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)
