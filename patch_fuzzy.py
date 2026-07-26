import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_regex = r'        val firstNode = trie\.children\[firstChar\] \?: return emptyList\(\).*?return results\n    \}'

new_block = """        val firstNode = trie.children[firstChar]
        
        if (firstNode != null) {
            // BFS through words starting with same first letter only
            // Collect up to 3000 candidates from trie then compute edit distance
            val wordCandidates = mutableListOf<Pair<String, Int>>()
            collectWords(firstNode, firstChar.toString(), wordCandidates, 3000, startTime, timeCapMs)
            
            val elapsedCollect = System.currentTimeMillis() - startTime
            if (elapsedCollect > timeCapMs) {
                if (!isIncognito) TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "FUZZY_TIMEOUT | typed=$typed | time_ms=$elapsedCollect | partial_results=${wordCandidates.size}")
            }
            
            if (wordCandidates.isNotEmpty()) {
                // Compute edit distance for each candidate
                for ((word, freq) in wordCandidates) {
                    if (System.currentTimeMillis() - startTime > timeCapMs) {
                        if (!isIncognito && elapsedCollect <= timeCapMs) { // log timeout if it happened during edit distance phase
                            TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "FUZZY_TIMEOUT | typed=$typed | time_ms=${System.currentTimeMillis() - startTime} | partial_results=${wordCandidates.size}")
                        }
                        break
                    }
                    if (Math.abs(word.length - lowerTyped.length) > maxEditDistance) continue
                    val dist = editDistance(lowerTyped, word)
                    if (dist in 1..maxEditDistance && dist > 0) {
                        // Exclude candidates that are significantly shorter than typed word
                        // Prevents "th" from being suggested for "teh"
                        if (word.length >= lowerTyped.length - 1) {
                            candidates.add(Pair(word, freq))
                        }
                    }
                }
            }
        }
        
        val results = candidates
            .sortedWith(compareBy(
                { editDistance(lowerTyped, it.first) },
                { -it.second }
            ))
            .take(limit)
            .map { it.first }
            .toMutableList()
            
        if (results.isEmpty()) {
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
        }
        
        return results
    }"""

if re.search(old_regex, content, flags=re.DOTALL):
    content = re.sub(old_regex, new_block, content, flags=re.DOTALL)
    with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Regex not found!")
