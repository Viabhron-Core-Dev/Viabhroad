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
        inputConnection.deleteSurroundingText(currentWord.length, 0)
        inputConnection.commitText(word + " ", 1)
        commitWord(word)
    }

    private fun commitWord(word: String) {
        val finalWord = word.lowercase()
        coroutineScope.launch {
            wordRepository.addWord(finalWord)
            previousWord?.let { prev ->
                wordRepository.addBigram(prev, finalWord)
            }
            previousWord = finalWord
        }
        currentWord.clear()
        updateSuggestions()
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
                    val topWord = currentSuggestions[0].word
                    inputConnection.deleteSurroundingText(currentWord.length, 0)
                    inputConnection.commitText(topWord + " ", 1)
                    commitWord(topWord)
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
                inputConnection.commitText(key, 1)
                if (key.length == 1 && key[0].isLetter()) {
                    currentWord.append(key)
                    updateSuggestions()
                } else {
                    currentWord.clear()
                    clearSuggestions()
                }
            }
        }
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
        android.widget.Toast.makeText(this, "Log Keeper Triggered", android.widget.Toast.LENGTH_SHORT).show()
    }
}
