with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

import re

old_tail = """        // Combine, prioritize personal, ensure unique
        for (word in personalWords + engineWords) {
            if (seen.add(word)) {
                results.add(word)
                if (results.size >= limit) break
            }
        }
        
        return results
    }"""

new_tail = """        // Combine, prioritize personal, ensure unique
        for (word in personalWords + engineWords) {
            if (seen.add(word)) {
                results.add(word)
                if (results.size >= limit) return results
            }
        }
        
        // 3. Fallback to main database if we still need more suggestions
        if (results.size < limit) {
            try {
                val dbWords = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    ClipboardDatabase.getDatabase(context).dictionaryWordDao().getSuggestions(lowerPrefix, limit)
                }
                for (dbWord in dbWords) {
                    if (seen.add(dbWord.word)) {
                        results.add(dbWord.word)
                        if (results.size >= limit) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return results
    }"""

if old_tail in content:
    content = content.replace(old_tail, new_tail)
    print("Patched getSuggestions tail")
else:
    print("Could not find tail")
    
with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)
