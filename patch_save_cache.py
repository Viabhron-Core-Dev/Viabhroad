import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

old_block = """                out.writeInt(bigrams.size)
                for ((word, nextMap) in bigrams) {
                    out.writeUTF(word)
                    out.writeInt(nextMap.size)
                    for ((nextWord, freq) in nextMap) {
                        out.writeUTF(nextWord)
                        out.writeInt(freq)
                    }
                }
            }
            if (cacheFile.exists()) cacheFile.delete()"""

new_block = """                out.writeInt(bigrams.size)
                for ((word, nextMap) in bigrams) {
                    out.writeUTF(word)
                    out.writeInt(nextMap.size)
                    for ((nextWord, freq) in nextMap) {
                        out.writeUTF(nextWord)
                        out.writeInt(freq)
                    }
                }
                
                out.writeInt(allWordsSet.size)
                for (word in allWordsSet) {
                    out.writeUTF(word)
                }
            }
            if (cacheFile.exists()) cacheFile.delete()"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Old block not found!")
