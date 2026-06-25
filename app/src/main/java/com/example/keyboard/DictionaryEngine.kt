package com.example.keyboard

import android.content.Context
import kotlinx.coroutines.*
import java.io.InputStream
import com.example.R

class DictionaryEngine(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trie = TrieNode()
    private val bigrams = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()
    private val trigrams = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Int>>()
    
    private val personalDao: PersonalDictionaryDao by lazy {
        ClipboardDatabase.getDatabase(context).personalDictionaryDao()
    }

    class TrieNode {
        val children = java.util.concurrent.ConcurrentHashMap<Char, TrieNode>()
        var isWord = false
        var frequency = 0
    }

    init {
        loadDefaultDictionary()
    }
    
    private fun loadDefaultDictionary() {
        // Load the basic dict from raw resources
        try {
            val basicStream = context.resources.openRawResource(R.raw.basic_dict)
            loadTextDictionary(basicStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load Google 10k English Words
        try {
            val google10kStream = context.resources.openRawResource(R.raw.google_10k_english)
            loadTextDictionary(google10kStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Load Hermit Dave's Frequency Words (50k)
        try {
            val hermitDaveStream = context.resources.openRawResource(R.raw.hermit_dave_en_50k)
            loadTextDictionary(hermitDaveStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Also load any imported text dictionaries from internal storage
        loadImportedDictionaries()
    }

    private fun loadImportedDictionaries() {
        scope.launch {
            try {
                val importsDir = java.io.File(context.filesDir, "imported_dicts")
                if (importsDir.exists() && importsDir.isDirectory) {
                    importsDir.listFiles()?.forEach { file ->
                        if (file.extension == "txt" && file.isFile) {
                            loadTextDictionary(file.inputStream())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Parses a generic text dictionary file. 
    // Supports either plain words (one per line) or word + frequency.
    fun loadTextDictionary(inputStream: InputStream) {
        scope.launch {
            try {
                var maxFreq = 50000 // Assuming lines are sorted by frequency descending (e.g. Google 10k)
                inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val cleanLine = line.trim()
                        if (cleanLine.isBlank() || cleanLine.startsWith("#")) continue
                        
                        val parts = cleanLine.split("\\s+".toRegex())
                        val word = parts[0]
                        val freq = if (parts.size > 1) {
                            parts[1].toIntOrNull() ?: maxFreq
                        } else {
                            maxFreq
                        }
                        
                        insertWord(word, frequency = freq)
                        if (maxFreq > 1) maxFreq--
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun insertWord(word: String, frequency: Int = 1, prevWord: String? = null, prevPrevWord: String? = null) {
        var current = trie
        for (char in word) {
            val c = char.lowercaseChar()
            if (!current.children.containsKey(c)) {
                current.children[c] = TrieNode()
            }
            current = current.children[c]!!
        }
        current.isWord = true
        current.frequency += frequency
        
        // Update bigrams
        if (prevWord != null) {
            val nextWords = bigrams.getOrPut(prevWord) { java.util.concurrent.ConcurrentHashMap() }
            nextWords[word] = nextWords.getOrDefault(word, 0) + frequency
        }
        
        // Update trigrams
        if (prevWord != null && prevPrevWord != null) {
            val context = "$prevPrevWord $prevWord"
            val nextWords = trigrams.getOrPut(context) { java.util.concurrent.ConcurrentHashMap() }
            nextWords[word] = nextWords.getOrDefault(word, 0) + frequency
        }
    }

    suspend fun forgetWord(word: String) {
        val lowerWord = word.lowercase()
        // 1. Delete from personal dictionary if it exists
        try {
            personalDao.getByShortcut(lowerWord)?.let { item ->
                personalDao.delete(item)
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Reduce frequency or mark as not a word in Trie
        var current = trie
        var found = true
        for (char in lowerWord) {
            if (!current.children.containsKey(char)) {
                found = false
                break
            }
            current = current.children[char]!!
        }
        if (found && current.isWord) {
            current.frequency = -10000 // Effectively blacklist it
            current.isWord = false
        }
    }

    suspend fun getSuggestions(prefix: String, prevWord: String? = null, prevPrevWord: String? = null, limit: Int = 3): List<String> {
        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val lowerPrefix = prefix.lowercase()
        
        if (lowerPrefix.isNotEmpty()) {
            // Check for Quick Phrase (shortcut)
            val quickPhrase = try {
                personalDao.getByShortcut(lowerPrefix)
            } catch (e: Exception) { null }
            
            if (quickPhrase != null && quickPhrase.word.isNotBlank()) {
                results.add(quickPhrase.word)
                seen.add(quickPhrase.word)
            }
        }
        
        if (lowerPrefix.isEmpty()) {
            // Next-word prediction using trigrams and bigrams
            if (prevWord != null) {
                if (prevPrevWord != null) {
                    val context = "$prevPrevWord $prevWord"
                    val triMatches = trigrams[context]?.entries?.sortedByDescending { it.value }?.map { it.key }
                    if (triMatches != null) {
                        for (w in triMatches) {
                            if (seen.add(w)) results.add(w)
                            if (results.size >= limit) return results
                        }
                    }
                }
                val biMatches = bigrams[prevWord]?.entries?.sortedByDescending { it.value }?.map { it.key }
                if (biMatches != null) {
                    for (w in biMatches) {
                        if (seen.add(w)) results.add(w)
                        if (results.size >= limit) return results
                    }
                }
            }
            return results
        }
        
        // 1. Fetch personal suggestions
        val personalWords = try {
            personalDao.getSuggestions(lowerPrefix, limit).map { it.word }
        } catch (e: Exception) {
            emptyList()
        }
        
        // 2. Fetch standard engine suggestions
        val engineWords = getPrefixSuggestions(lowerPrefix, limit)
        
        // Combine, prioritize personal, ensure unique
        for (word in personalWords + engineWords) {
            if (seen.add(word)) {
                results.add(word)
                if (results.size >= limit) break
            }
        }
        
        return results
    }

    private fun getPrefixSuggestions(prefix: String, limit: Int): List<String> {
        var current = trie
        for (char in prefix) {
            val c = char.lowercaseChar()
            if (!current.children.containsKey(c)) return emptyList()
            current = current.children[c]!!
        }
        
        val results = mutableListOf<Pair<String, Int>>()
        findWordsDeep(current, prefix, results)
        
        return results
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun findWordsDeep(node: TrieNode, prefix: String, results: MutableList<Pair<String, Int>>) {
        if (node.isWord) {
            results.add(Pair(prefix, node.frequency))
        }
        for ((char, childNode) in node.children) {
            findWordsDeep(childNode, prefix + char, results)
        }
    }
    
    // HeliBoard dictionary parsing blueprint (placeholder for actual implementation)
    fun loadHeliBoardDictionary(inputStream: InputStream) {
        scope.launch {
            try {
                // To be implemented: parsing binary/text HeliBoard format
                // 1. Read header (magic bytes, version)
                // 2. Read word list + frequency
                // 3. For each word, insertWord(word, frequency)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Load lightweight Transformer Model (placeholder)
    fun loadTransformerModel(inputStream: InputStream) {
        scope.launch {
            try {
                // To be implemented: load TFLite model from input stream
                // Configure tensor shapes, setup interpreter
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
