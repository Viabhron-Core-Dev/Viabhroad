with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

# I want to add it before the closing brace of DictionaryEngine.
# Let's find the last '}'
idx = content.rfind('}')
if idx != -1:
    new_method = """

    suspend fun migrateWordsToDatabase(inputStream: InputStream) {
        withContext(Dispatchers.IO) {
            val dao = ClipboardDatabase.getDatabase(context).dictionaryWordDao()
            val batch = mutableListOf<DictionaryWordEntity>()
            var totalInserted = 0
            inputStream.bufferedReader().useLines { lines ->
                for (rawLine in lines) {
                    val trimmedLine = rawLine.trim()
                    if (!trimmedLine.startsWith("word=")) continue
                    val parts = trimmedLine.removePrefix("word=").split(",")
                    val word = parts.getOrNull(0)?.trim()
                    if (word.isNullOrBlank()) continue
                    val freq = parts.getOrNull(1)?.removePrefix("f=")?.trim()?.toIntOrNull() ?: 1
                    batch.add(DictionaryWordEntity(word, freq, "imported"))
                    if (batch.size >= 2000) {
                        dao.insertAll(batch.toList())
                        totalInserted += batch.size
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) {
                dao.insertAll(batch)
                totalInserted += batch.size
            }
            val finalCount = dao.count()
            TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "DB_MIGRATION_COMPLETE | rows_inserted=[$totalInserted] | final_table_count=[$finalCount]")
        }
    }
}"""
    content = content[:idx] + new_method

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)
