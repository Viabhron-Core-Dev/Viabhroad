package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.logkeeper.TheLogKeeper
import com.example.R
import com.example.data.AppDatabase
import com.example.data.WordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class ViaboardService : InputMethodService(), KeyboardView.KeyboardListener {
    private lateinit var logKeeper: TheLogKeeper
    private var mainView: View? = null
    
    private lateinit var wordRepository: WordRepository
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var suggestionJob: Job? = null
    
    private var isToolbarExpanded = false
    private var isAutocorrectEnabled = true
    private var currentWord = StringBuilder()
    private var previousWord: String? = null
    private var currentSuggestions = emptyList<com.example.data.WordEntity>()
    
    private var tvSuggestion1: android.widget.TextView? = null
    private var tvSuggestion2: android.widget.TextView? = null
    private var tvSuggestion3: android.widget.TextView? = null

    override fun onCreate() {
        super.onCreate()
        
        // Trap all fatals and ensure they go to LogKeeper before the process dies
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val _logKeeper = TheLogKeeper.getInstance(this.applicationContext)
                // Use runBlocking or direct block to ensure log is saved before death
                kotlinx.coroutines.runBlocking {
                    _logKeeper.logDao.insertLog(com.example.logkeeper.data.LogEntry(
                        type = "FATAL",
                        component = "CRASH",
                        message = throwable.message ?: "Unknown crash",
                        stackTrace = throwable.stackTraceToString()
                    ))
                }
            } catch (e: Exception) {
                // Ignore if we can't log
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        logKeeper = TheLogKeeper.getInstance(this)
        logKeeper.log("INFO", "ViaboardService", "Service Created (View-based)")
        
        wordRepository = WordRepository(AppDatabase.getDatabase(this).wordDao())
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        mainView = root
        
        val keyboardView = root.findViewById<KeyboardView>(R.id.keyboard_view)
        keyboardView.listener = this
        
        // Parse and set the XML layout
        val parser = KeyboardParser(this)
        val keyboard = parser.parse(R.xml.kbd_qwerty)
        keyboardView.setKeyboard(keyboard)
        
        setupToolbar(root)
        
        return root
    }

    private fun setupToolbar(root: View) {
        val btnChevron = root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)
        val suggestionContent = root.findViewById<android.view.View>(R.id.suggestion_content)
        val expandedContent = root.findViewById<android.view.View>(R.id.toolbar_expanded_content)
        val btnSelectAll = root.findViewById<android.widget.ImageButton>(R.id.btn_select_all)
        val btnClipboard = root.findViewById<android.widget.ImageButton>(R.id.btn_clipboard)
        val btnSettings = root.findViewById<android.widget.ImageButton>(R.id.btn_settings)
        
        tvSuggestion1 = root.findViewById(R.id.suggestion_1)
        tvSuggestion2 = root.findViewById(R.id.suggestion_2)
        tvSuggestion3 = root.findViewById(R.id.suggestion_3)
        
        tvSuggestion1?.setOnClickListener { onSuggestionClicked(tvSuggestion1?.text.toString()) }
        tvSuggestion2?.setOnClickListener { onSuggestionClicked(tvSuggestion2?.text.toString()) }
        tvSuggestion3?.setOnClickListener { onSuggestionClicked(tvSuggestion3?.text.toString()) }
        
        btnChevron.setOnClickListener {
            isToolbarExpanded = !isToolbarExpanded
            if (isToolbarExpanded) {
                btnChevron.setImageResource(R.drawable.ic_chevron_left)
                suggestionContent.visibility = View.GONE
                expandedContent.visibility = View.VISIBLE
            } else {
                btnChevron.setImageResource(R.drawable.ic_chevron_right)
                suggestionContent.visibility = View.VISIBLE
                expandedContent.visibility = View.GONE
            }
        }
        
        btnSelectAll.setOnClickListener {
            val inputConnection = currentInputConnection ?: return@setOnClickListener
            inputConnection.performContextMenuAction(android.R.id.selectAll)
        }
        
        btnClipboard.setOnClickListener {
            val inputConnection = currentInputConnection ?: return@setOnClickListener
            inputConnection.performContextMenuAction(android.R.id.paste)
        }
        
        // Toggle Autocorrect with Settings button temporarily
        btnSettings.setOnClickListener {
            isAutocorrectEnabled = !isAutocorrectEnabled
            val stateText = if (isAutocorrectEnabled) "Autocorrect ON" else "Autocorrect OFF"
            android.widget.Toast.makeText(this, stateText, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun onSuggestionClicked(word: String) {
        if (word.isBlank()) return
        val inputConnection = currentInputConnection ?: return
        
        val isCapitalized = currentWord.isNotEmpty() && currentWord[0].isUpperCase()
        val finalWord = if (isCapitalized) {
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        } else {
            word
        }
        
        inputConnection.deleteSurroundingText(currentWord.length, 0)
        inputConnection.commitText(finalWord + " ", 1)
        commitWord(word)
    }

    private fun commitWord(word: String) {
        val finalWord = word.lowercase()
        val prevToSave = previousWord
        previousWord = finalWord
        currentWord.clear()
        
        coroutineScope.launch {
            wordRepository.addWord(finalWord)
            prevToSave?.let { prev ->
                wordRepository.addBigram(prev, finalWord)
            }
            updateSuggestions()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        previousWord = null
        clearSuggestions()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = mainView ?: return
        outInsets.contentTopInsets = view.top
        outInsets.visibleTopInsets = view.top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.launch {
             // cancel all in scope
        } // or just let it die. It's tied to service lifecycle.
        logKeeper.log("INFO", "ViaboardService", "Service Destroyed")
    }
    
    private fun updateSuggestions() {
        val prefix = currentWord.toString()
        suggestionJob?.cancel()
        
        if (prefix.isBlank()) {
            val prev = previousWord
            if (prev != null) {
                suggestionJob = coroutineScope.launch {
                    wordRepository.getNextWordSuggestions(prev).collect { list ->
                        currentSuggestions = list
                        tvSuggestion1?.text = list.getOrNull(0)?.word ?: ""
                        tvSuggestion2?.text = list.getOrNull(1)?.word ?: ""
                        tvSuggestion3?.text = list.getOrNull(2)?.word ?: ""
                    }
                }
            } else {
                clearSuggestions()
            }
            return
        }

        suggestionJob = coroutineScope.launch {
            wordRepository.getSuggestions(prefix).collect { list ->
                currentSuggestions = list
                tvSuggestion1?.text = list.getOrNull(0)?.word ?: ""
                tvSuggestion2?.text = list.getOrNull(1)?.word ?: ""
                tvSuggestion3?.text = list.getOrNull(2)?.word ?: ""
            }
        }
    }

    private fun clearSuggestions() {
        suggestionJob?.cancel()
        currentSuggestions = emptyList()
        tvSuggestion1?.text = ""
        tvSuggestion2?.text = ""
        tvSuggestion3?.text = ""
    }

    private fun shouldAutoCapitalize(): Boolean {
        val ic = currentInputConnection ?: return false
        val currentInfo = currentInputEditorInfo
        if (currentInfo != null) {
            val type = currentInfo.inputType
            if (type and android.text.InputType.TYPE_CLASS_TEXT != 0) {
                val capsMode = ic.getCursorCapsMode(type)
                if (capsMode and android.text.TextUtils.CAP_MODE_SENTENCES != 0 ||
                    capsMode and android.text.TextUtils.CAP_MODE_WORDS != 0 ||
                    capsMode and android.text.TextUtils.CAP_MODE_CHARACTERS != 0) {
                    return true
                }
            }
        }
        val beforeCursor = ic.getTextBeforeCursor(3, 0) ?: return true
        if (beforeCursor.isEmpty()) return true
        val text = beforeCursor.toString()
        return text.endsWith(". ") || text.endsWith("! ") || text.endsWith("? ") || text.endsWith("\n")
    }

    override fun onKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return
        when (key) {
            "DEL" -> {
                val selectedText = inputConnection.getSelectedText(0)
                if (selectedText != null && selectedText.isNotEmpty()) {
                    inputConnection.commitText("", 1)
                    currentWord.clear()
                    clearSuggestions()
                } else {
                    sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
                    if (currentWord.isNotEmpty()) {
                        currentWord.deleteCharAt(currentWord.length - 1)
                        updateSuggestions()
                    }
                }
            }
            "SPACE" -> {
                if (isAutocorrectEnabled && currentSuggestions.isNotEmpty() && currentWord.isNotEmpty() && currentSuggestions[0].word != currentWord.toString().lowercase()) {
                    val topWordText = currentSuggestions[0].word
                    val isCapitalized = currentWord[0].isUpperCase()
                    val topWord = if (isCapitalized) {
                        topWordText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    } else {
                        topWordText
                    }
                    inputConnection.deleteSurroundingText(currentWord.length, 0)
                    inputConnection.commitText(topWord + " ", 1)
                    commitWord(topWordText)
                } else {
                    inputConnection.commitText(" ", 1)
                    if (currentWord.isNotEmpty()) {
                        commitWord(currentWord.toString())
                    }
                }
            }
            "ENTER" -> {
                inputConnection.commitText("\n", 1)
                if (currentWord.isNotEmpty()) {
                    commitWord(currentWord.toString())
                }
                previousWord = null
                updateSuggestions()
            }
            "SHIFT", "SYM" -> { /* TODO: Toggle states later */ }
            else -> {
                var finalKey = key
                if (key.length == 1 && key[0].isLetter() && currentWord.isEmpty()) {
                    if (shouldAutoCapitalize()) {
                        finalKey = key.uppercase()
                    }
                }
                
                inputConnection.commitText(finalKey, 1)
                if (finalKey.length == 1 && finalKey[0].isLetter()) {
                    currentWord.append(finalKey)
                    updateSuggestions()
                } else {
                    currentWord.clear()
                    clearSuggestions()
                }
            }
        }
    }

    private var accentPopupWindow: android.widget.PopupWindow? = null

    override fun onLongPressKey(key: String, keyRect: android.graphics.RectF, keyboardView: View) {
        val accents = when (key.lowercase()) {
            "a" -> listOf("a", "á", "à", "â")
            "e" -> listOf("e", "é", "3", "è", "ê")
            "i" -> listOf("i", "í", "8", "ì", "î")
            "o" -> listOf("o", "ó", "9", "ò", "ô")
            "u" -> listOf("u", "ú", "7", "ù", "û")
            "c" -> listOf("c", "ç", "ć", "č")
            "n" -> listOf("n", "ñ", "ń", "ň")
            "s" -> listOf("s", "ß", "ś", "š")
            "q" -> listOf("q", "1", "!")
            "w" -> listOf("w", "2", "@")
            "r" -> listOf("r", "4", "#")
            "t" -> listOf("t", "5", "%")
            "y" -> listOf("y", "6", "^")
            "p" -> listOf("p", "0", "*")
            "!" -> listOf("!", "¡")
            "?" -> listOf("?", "¿")
            else -> emptyList()
        }
        
        if (accents.isNotEmpty()) {
            showAccentPopup(accents, keyRect, keyboardView)
        } else {
            // fall back to default behavior if no accents
            onKeyPress(key)
        }
    }

    private fun showAccentPopup(accents: List<String>, keyRect: android.graphics.RectF, keyboardView: View) {
        val context = keyboardView.context
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.WHITE)
            elevation = 16f
            setPadding(16, 16, 16, 16)
        }
        
        // Add a rounded background
        val background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 24f
            setStroke(2, android.graphics.Color.LTGRAY)
        }
        linearLayout.background = background
        
        for (accent in accents) {
            val btn = android.widget.TextView(context).apply {
                text = accent
                textSize = 28f
                setPadding(32, 24, 32, 24)
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.BLACK)
                setOnClickListener {
                    onKeyPress(accent)
                    accentPopupWindow?.dismiss()
                }
            }
            linearLayout.addView(btn)
        }
        
        linearLayout.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        val popupWidth = linearLayout.measuredWidth
        val popupHeight = linearLayout.measuredHeight
        
        accentPopupWindow = android.widget.PopupWindow(linearLayout, popupWidth, popupHeight, true).apply {
            isOutsideTouchable = true
            elevation = 16f
        }
        
        val location = IntArray(2)
        keyboardView.getLocationInWindow(location)
        
        var x = location[0] + keyRect.centerX().toInt() - (popupWidth / 2)
        val y = location[1] + keyRect.top.toInt() - popupHeight - 16
        
        // Ensure it doesn't go off-screen horizontally
        val displayMetrics = context.resources.displayMetrics
        if (x < 0) x = 16
        if (x + popupWidth > displayMetrics.widthPixels) x = displayMetrics.widthPixels - popupWidth - 16
        
        accentPopupWindow?.showAtLocation(keyboardView, android.view.Gravity.NO_GRAVITY, x, y)
    }

    override fun onLongPressBackspace() {
        val inputConnection = currentInputConnection ?: return
        val currentText = inputConnection.getTextBeforeCursor(10000, 0)
        if (!currentText.isNullOrEmpty()) {
            val lastNewline = currentText.lastIndexOf('\n')
            val deleteCount = if (lastNewline != -1) {
                currentText.length - lastNewline - 1
            } else {
                currentText.length
            }
            if (deleteCount > 0) {
                inputConnection.deleteSurroundingText(deleteCount, 0)
            }
        }
    }

    override fun onLongPressEnter() {
        logKeeper.log("USER_ACTION", "ViaboardService", "Triggering Log Keeper via Long-Press Enter")
        logKeeper.exportLogsToDownloads()
        android.widget.Toast.makeText(this, "Exporting Logs...", android.widget.Toast.LENGTH_SHORT).show()
    }
}
