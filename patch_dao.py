with open("app/src/main/java/com/example/keyboard/DictionaryWordDao.kt", "r") as f:
    content = f.read()

import re
content = re.sub(r'suspend fun getExact\(word: String\): DictionaryWordEntity\?', r'suspend fun getExact(word: String): DictionaryWordEntity?\n\n    @Query("SELECT * FROM dictionary_words WHERE word LIKE :prefix || \'%\' ORDER BY frequency DESC LIMIT :limit")\n    suspend fun getSuggestions(prefix: String, limit: Int): List<DictionaryWordEntity>', content)

with open("app/src/main/java/com/example/keyboard/DictionaryWordDao.kt", "w") as f:
    f.write(content)
print("Patched DAO")
