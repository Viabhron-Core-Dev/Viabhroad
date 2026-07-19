import re

with open("app/src/main/java/com/example/keyboard/ViaboardService.kt", "r") as f:
    content = f.read()

bad_func = """    private fun decideAutocorrect(typed: String, aggressiveness: Float): AutocorrectDecision {
        if (typed.isEmpty()) return AutocorrectDecision.None
        if (dictionaryEngine.wordExists(typed.lowercase())) return AutocorrectDecision.None

        var isProperNoun = typed[0].isUpperCase()
        if (isProperNoun) {
            val ic = currentInputConnection
            if (ic != null) {
                val beforeCursor = ic.getTextBeforeCursor(typed.length + 20, 0)?.toString() ?: ""
                if (beforeCursor.length >= typed.length) {
                    val beforeWord = beforeCursor.dropLast(typed.length)
                    val trailingWhitespace = beforeWord.takeLastWhile { it.isWhitespace() }
                    val trimmed = beforeWord.trimEnd()
                    if (beforeWord.isBlank() || 
                        trailingWhitespace.contains('\n') || 
                        trimmed.endsWith(".") || 
                        trimmed.endsWith("!") || 
                        trimmed.endsWith("?")) {
                        isProperNoun = false
                    }
                } else {
                    isProperNoun = false
                }
            }
        }
        val isIncognito = isIncognitoActive()"""

original_func = """    private fun decideAutocorrect(typed: String, aggressiveness: Float): AutocorrectDecision {
        if (typed.isEmpty()) return AutocorrectDecision.None
        if (dictionaryEngine.wordExists(typed.lowercase())) return AutocorrectDecision.None

        val isProperNoun = typed[0].isUpperCase()
        val isIncognito = isIncognitoActive()"""

if bad_func in content:
    content = content.replace(bad_func, original_func)
    with open("app/src/main/java/com/example/keyboard/ViaboardService.kt", "w") as f:
        f.write(content)
    print("Reverted successfully")
else:
    print("Could not find bad_func")
