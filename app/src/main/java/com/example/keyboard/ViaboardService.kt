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
    private var isManualIncognito = false
    
    enum class ShiftState {
        LOWERCASE, UPPERCASE, CAPS_LOCK
    }
    private var shiftState = ShiftState.LOWERCASE
    
    private var lastSpaceTime = 0L
    private var currentWord = StringBuilder()
    private var previousWord: String? = null
    private var currentSuggestions = emptyList<com.example.data.WordEntity>()
    
    private var tvSuggestion1: android.widget.TextView? = null
    private var tvSuggestion2: android.widget.TextView? = null
    private var tvSuggestion3: android.widget.TextView? = null
    private var btnIncognito: android.widget.ImageButton? = null
    private var toolbarContainer: android.view.View? = null

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

    private fun switchKeyboardLayout(xmlResId: Int) {
        val root = mainView ?: return
        val keyboardView = root.findViewById<KeyboardView>(R.id.keyboard_view) ?: return
        val parser = KeyboardParser(this)
        val keyboard = parser.parse(xmlResId)
        keyboardView.setKeyboard(keyboard)
        keyboardView.invalidate()
        if (xmlResId == R.xml.kbd_qwerty) {
            updateShiftState()
        }
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
        
        btnIncognito = root.findViewById(R.id.btn_incognito)
        toolbarContainer = root.findViewById(R.id.toolbar_container)
        
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
        
        btnIncognito?.setOnClickListener {
            isManualIncognito = !isManualIncognito
            updateIncognitoStateUI()
            
            // Clear current/previous word on toggle to prevent leaking incognito words into normal dictionary
            currentWord.clear()
            previousWord = null
            clearSuggestions()
            
            val stateText = if (isIncognitoActive()) "Incognito Mode ON" else "Incognito Mode OFF"
            android.widget.Toast.makeText(this, stateText, android.widget.Toast.LENGTH_SHORT).show()
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
        
        if (isIncognitoActive()) {
            updateSuggestions()
            return
        }
        
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

        // Select initial layout based on input type
        val inputType = info?.inputType ?: android.text.InputType.TYPE_CLASS_TEXT
        when (inputType and android.text.InputType.TYPE_MASK_CLASS) {
            android.text.InputType.TYPE_CLASS_NUMBER,
            android.text.InputType.TYPE_CLASS_DATETIME,
            android.text.InputType.TYPE_CLASS_PHONE -> {
                switchKeyboardLayout(R.xml.kbd_numpad)
            }
            else -> {
                switchKeyboardLayout(R.xml.kbd_qwerty)
            }
        }

        currentWord.clear()
        previousWord = null
        clearSuggestions()
        updateIncognitoStateUI()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShiftState()
    }

    private fun isIncognitoActive(): Boolean {
        return isManualIncognito || isSensitiveField(currentInputEditorInfo)
    }

    private fun isSensitiveField(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val classType = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        
        // Password types check
        if (classType == android.text.InputType.TYPE_CLASS_NUMBER && 
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
            return true
        }
        if (classType == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) {
            return true
        }
        
        // Check personalized learning flag in imeOptions (IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000)
        if (info.imeOptions and 0x1000000 != 0) {
            return true
        }
        
        // Check standard sensitive classifications like email
        if (classType == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )) {
            return true
        }
        
        return false
    }

    private fun updateIncognitoStateUI() {
        val active = isIncognitoActive()
        
        // Update the toggle button icon
        if (active) {
            btnIncognito?.setImageResource(R.drawable.ic_incognito_on)
            // Change toolbar background to deep private dark color
            toolbarContainer?.setBackgroundColor(android.graphics.Color.parseColor("#202124"))
            
            // Set suggestions text color to white for contrast
            tvSuggestion1?.setTextColor(android.graphics.Color.WHITE)
            tvSuggestion2?.setTextColor(android.graphics.Color.WHITE)
            tvSuggestion3?.setTextColor(android.graphics.Color.WHITE)
            
            // Set button icons to white tint
            val whiteColor = android.graphics.Color.WHITE
            mainView?.let { root ->
                root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)?.setColorFilter(whiteColor)
                root.findViewById<android.widget.ImageButton>(R.id.btn_select_all)?.setColorFilter(whiteColor)
                root.findViewById<android.widget.ImageButton>(R.id.btn_clipboard)?.setColorFilter(whiteColor)
                root.findViewById<android.widget.ImageButton>(R.id.btn_settings)?.setColorFilter(whiteColor)
            }
            btnIncognito?.setColorFilter(whiteColor)
        } else {
            btnIncognito?.setImageResource(R.drawable.ic_incognito_off)
            // Reset toolbar background to standard light gray
            toolbarContainer?.setBackgroundColor(android.graphics.Color.parseColor("#E8EAED"))
            
            // Reset suggestions text color to dark gray
            val darkGray = android.graphics.Color.parseColor("#333333")
            tvSuggestion1?.setTextColor(darkGray)
            tvSuggestion2?.setTextColor(darkGray)
            tvSuggestion3?.setTextColor(darkGray)
            
            // Reset button icons to dark gray tint
            mainView?.let { root ->
                root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)?.setColorFilter(darkGray)
                root.findViewById<android.widget.ImageButton>(R.id.btn_select_all)?.setColorFilter(darkGray)
                root.findViewById<android.widget.ImageButton>(R.id.btn_clipboard)?.setColorFilter(darkGray)
                root.findViewById<android.widget.ImageButton>(R.id.btn_settings)?.setColorFilter(darkGray)
            }
            btnIncognito?.setColorFilter(darkGray)
        }
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

    private fun updateShiftState(force: ShiftState? = null) {
        if (force != null) {
            shiftState = force
        } else {
            if (shiftState == ShiftState.CAPS_LOCK) return
            shiftState = if (shouldAutoCapitalize()) ShiftState.UPPERCASE else ShiftState.LOWERCASE
        }
        
        val kv = mainView?.findViewById<com.example.keyboard.KeyboardView>(R.id.keyboard_view) ?: return
        val kbd = kv.getKeyboard() ?: return
        
        for (row in kbd.rows) {
            for (key in row.keys) {
                if (key.codes.length == 1 && key.codes[0].isLetter()) {
                    key.label = if (shiftState == ShiftState.LOWERCASE) key.codes.lowercase() else key.codes.uppercase()
                } else if (key.codes == "SHIFT") {
                    key.label = when (shiftState) {
                        ShiftState.LOWERCASE -> "⇧"
                        ShiftState.UPPERCASE -> "⬆"
                        ShiftState.CAPS_LOCK -> "⇪"
                    }
                }
            }
        }
        kv.invalidate()
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
                updateShiftState()
            }
            "SPACE" -> {
                val now = System.currentTimeMillis()
                val textBeforeCursor = inputConnection.getTextBeforeCursor(2, 0) ?: ""
                
                if (now - lastSpaceTime < 500 && currentWord.isEmpty() && textBeforeCursor.endsWith(" ")) {
                    // Double space detected! Replace previous space with period and space
                    inputConnection.deleteSurroundingText(1, 0)
                    inputConnection.commitText(". ", 1)
                    lastSpaceTime = 0L
                    updateShiftState()
                    return
                }

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
                    lastSpaceTime = now
                } else {
                    inputConnection.commitText(" ", 1)
                    if (currentWord.isNotEmpty()) {
                        commitWord(currentWord.toString())
                    }
                    lastSpaceTime = now
                }
                updateShiftState()
            }
            "ENTER" -> {
                inputConnection.commitText("\n", 1)
                if (currentWord.isNotEmpty()) {
                    commitWord(currentWord.toString())
                }
                previousWord = null
                updateSuggestions()
                updateShiftState()
            }
            "SHIFT" -> {
                when (shiftState) {
                    ShiftState.LOWERCASE -> updateShiftState(ShiftState.UPPERCASE)
                    ShiftState.UPPERCASE -> updateShiftState(ShiftState.LOWERCASE)
                    ShiftState.CAPS_LOCK -> updateShiftState(ShiftState.LOWERCASE)
                }
            }
            "MODE_SYMBOLS" -> switchKeyboardLayout(R.xml.kbd_symbols)
            "MODE_SYMBOLS_SHIFT" -> switchKeyboardLayout(R.xml.kbd_symbols_shift)
            "MODE_ALPHABET" -> switchKeyboardLayout(R.xml.kbd_qwerty)
            "MODE_NAVIGATION" -> switchKeyboardLayout(R.xml.kbd_navigation)
            "MODE_DESKTOP" -> switchKeyboardLayout(R.xml.kbd_desktop)
            "SELECT_ALL" -> inputConnection.performContextMenuAction(android.R.id.selectAll)
            "COPY" -> inputConnection.performContextMenuAction(android.R.id.copy)
            "PASTE" -> inputConnection.performContextMenuAction(android.R.id.paste)
            "CUT" -> inputConnection.performContextMenuAction(android.R.id.cut)
            "UNDO" -> inputConnection.performContextMenuAction(android.R.id.undo)
            "REDO" -> inputConnection.performContextMenuAction(android.R.id.redo)
            "DIR_UP" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_UP)
            "DIR_DOWN" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            "DIR_LEFT" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            "DIR_RIGHT" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            "TAB" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_TAB)
            "ESC" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ESCAPE)
            "HOME" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_MOVE_HOME)
            "END" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_MOVE_END)
            "PGUP" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_PAGE_UP)
            "PGDN" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_PAGE_DOWN)
            else -> {
                var finalKey = key
                if (key.length == 1 && key[0].isLetter()) {
                    finalKey = if (shiftState == ShiftState.LOWERCASE) key.lowercase() else key.uppercase()
                }
                
                inputConnection.commitText(finalKey, 1)
                
                if (shiftState == ShiftState.UPPERCASE && finalKey.length == 1 && finalKey[0].isLetter()) {
                    updateShiftState(ShiftState.LOWERCASE)
                }
                
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

    override fun onLongPressKey(key: String, keyRect: android.graphics.RectF, keyboardView: View) {
        if (key == "SHIFT") {
            updateShiftState(ShiftState.CAPS_LOCK)
        }
        // Accents are handled completely in inline KeyboardView logic now.
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
