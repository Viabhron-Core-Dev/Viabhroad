with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "r") as f:
    content = f.read()

content = content.replace('TheLogKeeper.logEvent("', 'TheLogKeeper.getInstance(context).log("INFO", "DictionaryEngine", "')

with open("app/src/main/java/com/example/keyboard/DictionaryEngine.kt", "w") as f:
    f.write(content)

