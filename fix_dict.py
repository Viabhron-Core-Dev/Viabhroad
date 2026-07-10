import re

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

# Fix loadTextDictionary
old_text = """                        if (maxFreq > 1) maxFreq--
                    }
                }
                saveCacheToDisk(sourceFileName, sourceFileSize)
            } catch (e: Exception) {"""

new_text = """                        if (maxFreq > 1) maxFreq--
                    }
                }
            } catch (e: Exception) {"""

content = content.replace(old_text, new_text)

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

