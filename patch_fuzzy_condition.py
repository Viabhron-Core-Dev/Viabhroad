import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_block = """        if (results.isEmpty()) {
            val len = lowerTyped.length
            var foundFallback: String? = null
            
            // 1. Transpositions
            for (i in 0 until len - 1) {
                val p = lowerTyped.substring(0, i) + lowerTyped[i + 1] + lowerTyped[i] + lowerTyped.substring(i + 2)
                if (allWordsSet.contains(p)) { foundFallback = p; break }
            }
            
            // 2. Deletions
            if (foundFallback == null) {
                for (i in 0 until len) {
                    val p = lowerTyped.substring(0, i) + lowerTyped.substring(i + 1)
                    if (allWordsSet.contains(p)) { foundFallback = p; break }
                }
            }
            
            // 3. Substitutions
            if (foundFallback == null) {
                for (i in 0 until len) {
                    for (c in 'a'..'z') {
                        if (c != lowerTyped[i]) {
                            val p = lowerTyped.substring(0, i) + c + lowerTyped.substring(i + 1)
                            if (allWordsSet.contains(p)) { foundFallback = p; break }
                        }
                    }
                    if (foundFallback != null) break
                }
            }
            
            // 4. Insertions
            if (foundFallback == null) {
                for (i in 0..len) {
                    for (c in 'a'..'z') {
                        val p = lowerTyped.substring(0, i) + c + lowerTyped.substring(i)
                        if (allWordsSet.contains(p)) { foundFallback = p; break }
                    }
                    if (foundFallback != null) break
                }
            }
            
            if (foundFallback != null) {
                results.add(foundFallback)
            }
        }
        
        if (!isIncognito) {
            val elapsed = System.currentTimeMillis() - startTime
            val source = if (candidates.isEmpty() && results.isNotEmpty()) "fallback" else "trie"
            TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "FUZZY_SEARCH | typed=$typed | candidates_found=${results.size} | time_ms=$elapsed | source=$source")
        }"""

new_block = """        var usedFallback = false
        if (results.isEmpty() || editDistance(lowerTyped, results[0]) > 1) {
            val len = lowerTyped.length
            var foundFallback: String? = null
            
            // 1. Transpositions
            for (i in 0 until len - 1) {
                val p = lowerTyped.substring(0, i) + lowerTyped[i + 1] + lowerTyped[i] + lowerTyped.substring(i + 2)
                if (allWordsSet.contains(p)) { foundFallback = p; break }
            }
            
            // 2. Deletions
            if (foundFallback == null) {
                for (i in 0 until len) {
                    val p = lowerTyped.substring(0, i) + lowerTyped.substring(i + 1)
                    if (allWordsSet.contains(p)) { foundFallback = p; break }
                }
            }
            
            // 3. Substitutions
            if (foundFallback == null) {
                for (i in 0 until len) {
                    for (c in 'a'..'z') {
                        if (c != lowerTyped[i]) {
                            val p = lowerTyped.substring(0, i) + c + lowerTyped.substring(i + 1)
                            if (allWordsSet.contains(p)) { foundFallback = p; break }
                        }
                    }
                    if (foundFallback != null) break
                }
            }
            
            // 4. Insertions
            if (foundFallback == null) {
                for (i in 0..len) {
                    for (c in 'a'..'z') {
                        val p = lowerTyped.substring(0, i) + c + lowerTyped.substring(i)
                        if (allWordsSet.contains(p)) { foundFallback = p; break }
                    }
                    if (foundFallback != null) break
                }
            }
            
            if (foundFallback != null) {
                usedFallback = true
                results.remove(foundFallback)
                results.add(0, foundFallback)
                while (results.size > limit) {
                    results.removeAt(results.size - 1)
                }
            }
        }
        
        if (!isIncognito) {
            val elapsed = System.currentTimeMillis() - startTime
            val source = if (usedFallback) "fallback" else "trie"
            TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "FUZZY_SEARCH | typed=$typed | candidates_found=${results.size} | time_ms=$elapsed | source=$source")
        }"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Old block not found!")
