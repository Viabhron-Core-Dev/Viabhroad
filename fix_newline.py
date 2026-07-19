with open("app/src/main/java/com/example/keyboard/ViaboardService.kt", "r") as f:
    content = f.read()

bad = "trailingWhitespace.contains('\n')"
good = "trailingWhitespace.contains('\\n')"

if bad in content:
    content = content.replace(bad, good)
    with open("app/src/main/java/com/example/keyboard/ViaboardService.kt", "w") as f:
        f.write(content)
    print("Fixed newline")
else:
    print("Bad newline not found")
