with open("app/src/main/java/com/example/keyboard/ClipboardDatabase.kt", "r") as f:
    content = f.read()

old_header = "@Database(entities = [ClipboardItem::class, PersonalDictionaryItem::class], version = 2, exportSchema = false)"
new_header = "@Database(entities = [ClipboardItem::class, PersonalDictionaryItem::class, DictionaryWordEntity::class], version = 3, exportSchema = false)"

content = content.replace(old_header, new_header)

old_dao = "    abstract fun personalDictionaryDao(): PersonalDictionaryDao"
new_dao = "    abstract fun personalDictionaryDao(): PersonalDictionaryDao\n    abstract fun dictionaryWordDao(): DictionaryWordDao"

content = content.replace(old_dao, new_dao)

with open("app/src/main/java/com/example/keyboard/ClipboardDatabase.kt", "w") as f:
    f.write(content)
