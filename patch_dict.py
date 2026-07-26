import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

# 1. Add allWordsSet
if "allWordsSet" not in content:
    content = content.replace("private val trigrams = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()",
                              "private val trigrams = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()\n    private val allWordsSet = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()")

# 2. Modify loadCombinedDictionary to add to allWordsSet
old_load = """                                currentWord = word
                                wordsParsed++

                                if (trieWordsInserted < MAX_IN_MEMORY_WORDS) {"""
new_load = """                                currentWord = word
                                wordsParsed++
                                allWordsSet.add(word.lowercase())

                                if (trieWordsInserted < MAX_IN_MEMORY_WORDS) {"""
content = content.replace(old_load, new_load)

# 3. Replace wordExists
old_wordExists_regex = r'    fun wordExists\(word: String\): Boolean \{.*?\n    \}'
new_wordExists = """    fun wordExists(word: String): Boolean {
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

        // 2. Fallback to in-memory set (synchronous, instant)
        return allWordsSet.contains(lowerWord)
    }"""
content = re.sub(old_wordExists_regex, new_wordExists, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)
print("Patch applied")
