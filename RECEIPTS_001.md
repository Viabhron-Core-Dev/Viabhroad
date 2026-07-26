
2026-07-26T10:22:00
* Request: Update getFuzzyCorrections fallback trigger condition to run if results are empty OR if the best trie match has edit distance > 1. Insert fallback match at index 0 and trim to limit.
* Files touched: app/src/main/java/com/example/keyboard/DictionaryEngine.kt
* Executed: Updated `getFuzzyCorrections` to track if the fallback was used. Changed the `if (results.isEmpty())` check to `if (results.isEmpty() || editDistance(lowerTyped, results[0]) > 1)`. Added logic to insert the fallback result at index 0 and remove elements beyond `limit`.
* Verified: Local compilation succeeded (gradle clean assembleDebug).
* Deviations: None.
* Issues: None.
