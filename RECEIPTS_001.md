
2026-07-26T10:22:00
* Request: Update getFuzzyCorrections fallback trigger condition to run if results are empty OR if the best trie match has edit distance > 1. Insert fallback match at index 0 and trim to limit.
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Updated `getFuzzyCorrections` to track if the fallback was used. Changed the `if (results.isEmpty())` check to `if (results.isEmpty() || editDistance(lowerTyped, results[0]) > 1)`. Added logic to insert the fallback result at index 0 and remove elements beyond `limit`.
* Verified: Local compilation succeeded (gradle clean assembleDebug).
* Deviations: None.
* Issues: None.

2026-07-27T00:03:00
* Request: Extend the dictionary cache to include `allWordsSet`, and confirm it saves automatically after import (Step 1 of 2).
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Added `out.writeInt(allWordsSet.size)` and `out.writeUTF(word)` to the `saveCacheToDisk` function after the bigrams serialization. Confirmed `saveCacheToDisk` is being called correctly in `loadCombinedDictionary`. Retained the `try/catch(e: Throwable)` block.
* Verified: Local compilation succeeded (gradle clean assembleDebug).
* Deviations: None.
* Issues: None.

2026-07-29T01:36:00
* Request: Read the dictionary cache back on startup instead of re-parsing raw text (Step 2 of 2).
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Added `readTrieNode` to recursively load trie nodes. Added `loadCacheFromDisk` to load the trie, bigrams, and allWordsSet, checking format version and source file match before clearing and replacing the existing structures. Modified `loadImportedDictionaries` to call `loadCacheFromDisk` before `loadCombinedDictionary`, skipping the full parse and calling `checkIfReady()` directly if a valid cache is found.
* Verified: Local compilation succeeded (gradle clean assembleDebug).
* Deviations: None.
* Issues: None.

2026-07-31T10:46:00
* Request: Fix cache logging visibility and add timing breakdown — Viaboard.
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Replaced all `android.util.Log.d` and `android.util.Log.w` calls in `loadCacheFromDisk` and `saveCacheToDisk` with `TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", ...)`. Added timing measurements (`CACHE_TIMING | stage=trie`, `stage=bigrams`, `stage=words`) inside `loadCacheFromDisk` after reading each respective structure.
* Verified: Local compilation succeeded (`compile_applet`).
* Deviations: None.
* Issues: None.

2026-08-01T23:18:00
* Request: Add length-normalized penalty to fuzzy correction ranking — Viaboard
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Updated the `sortedWith(compareBy(...))` block in `getFuzzyCorrections` to normalize the edit distance by dividing it by the candidate word's length (`editDistance(lowerTyped, it.first).toDouble() / it.first.length`). The frequency tiebreaker was retained unchanged.
* Verified: Local compilation in progress...
* Deviations: None.
* Issues: None.

2026-08-04T14:55:00
* Request: Add spatial key-proximity weighting as a secondary ranking factor — Viaboard
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Added a `keyAdjacency` map and `spatialPenalty` function to `DictionaryEngine.kt`. Updated the `sortedWith(compareBy(...))` block in `getFuzzyCorrections` to use `{ spatialPenalty(lowerTyped, it.first) }` as the secondary sort key, immediately after length-normalized edit distance and before frequency. `editDistance` and candidate filtering logic remained completely untouched.
* Verified: Local compilation succeeded.
* Deviations: None.
* Issues: None.

2026-08-10T08:42:00
* Request: Add log-probability scoring to fuzzy correction ranking — Viaboard
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Replaced the 3-key `sortedWith` block in `getFuzzyCorrections` with a combined penalty score calculation: `(LengthNormalizedEditDistance + SpatialPenalty) - (alpha * ln(frequency))` where `alpha` = 0.05. Re-sorted based on `totalPenalty`, mapped safe frequency via `maxOf(1, freq)`, and added `TheLogKeeper` candidate diagnostic logging before truncating to the `limit`.
* Verified: Local compilation succeeded.
* Deviations: None.
* Issues: None.
