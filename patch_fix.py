import re
with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

# I will find the whole block:
pattern = r'    private fun loadDefaultDictionary\(\) \{[\s\S]*?fun loadCombinedDictionary\([\s\S]*?loadImportedDictionaries\(\)\s*\}'
# wait, currently it's messed up. 

