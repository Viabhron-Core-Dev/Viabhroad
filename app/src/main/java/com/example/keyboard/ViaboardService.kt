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
    
    private lateinit var dictionaryEngine: DictionaryEngine
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
    private var prevPrevWord: String? = null
    private var currentSuggestions = emptyList<String>()
    
    private var tvSuggestion1: android.widget.TextView? = null
    private var tvSuggestion2: android.widget.TextView? = null
    private var tvSuggestion3: android.widget.TextView? = null
    private var suggestionDivider1: android.view.View? = null
    private var suggestionDivider2: android.view.View? = null
    private var suggestionPaste: android.view.View? = null
    private var suggestionPasteDivider: android.view.View? = null
    private var btnIncognito: android.widget.ImageButton? = null
    private var toolbarContainer: android.view.View? = null
    
    private var tvSuggestionPasteText: android.widget.TextView? = null
    private var btnSuggestionPasteClose: android.widget.ImageView? = null
    private var clipboardLastDismissedText: String? = null
    private var clipboardLastObservedText: String? = null
    private var clipboardObservedTime: Long = 0L
    
    // Clipboard feature
    private var isClipboardModalOpen = false
    private lateinit var clipboardRepository: ClipboardRepository
    private var clipboardAdapter: ClipboardAdapter? = null
    
    // Emoji feature
    private var isEmojiModalOpen = false
    private var emojiAdapter: com.example.keyboard.EmojiAdapter? = null
    private var clipboardManager: android.content.ClipboardManager? = null
    private val clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        onPrimaryClipChanged()
    }

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
        
        dictionaryEngine = DictionaryEngine(this)
        clipboardRepository = ClipboardRepository(ClipboardDatabase.getDatabase(this).clipboardDao())
    }

    private fun switchKeyboardLayout(xmlResId: Int) {
        val root = mainView ?: return
        val keyboardView = root.findViewById<KeyboardView>(R.id.keyboard_view) ?: return
        
        keyboardView.visibility = View.VISIBLE
        
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
        setupClipboard(root)
        
        return root
    }
    
    private fun setupClipboard(root: View) {
        val recycler = root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.clipboard_recycler)
        recycler.layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL)
        
        clipboardAdapter = ClipboardAdapter(
            onItemClicked = { item ->
                currentInputConnection?.commitText(item.text, 1)
                toggleClipboardModal()
            },
            onItemLongClicked = { item ->
                coroutineScope.launch(Dispatchers.IO) {
                    clipboardRepository.togglePin(item)
                }
            }
        )
        recycler.adapter = clipboardAdapter
        
        // Bottom/Sidebar actions
        root.findViewById<android.view.View>(R.id.btn_clipboard_abc)?.setOnClickListener {
            toggleClipboardModal()
        }
        root.findViewById<android.view.View>(R.id.btn_clipboard_space)?.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_SPACE)
        }
        root.findViewById<android.view.View>(R.id.btn_clipboard_clear_pinned)?.setOnClickListener {
            coroutineScope.launch(Dispatchers.IO) {
                clipboardRepository.deleteAllUnpinned()
            }
        }
        root.findViewById<android.view.View>(R.id.btn_clipboard_enter)?.setOnClickListener {
            handleEnterAction()
        }
        root.findViewById<android.view.View>(R.id.btn_clipboard_backspace)?.setOnClickListener {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
        }

        clipboardManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        
        // Observe clipboard history
        coroutineScope.launch {
            clipboardRepository.allItems.collect { items ->
                clipboardAdapter?.setItems(items)
            }
        }
    }
    
    private fun onPrimaryClipChanged() {
        val clip = clipboardManager?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                clipboardLastObservedText = text
                clipboardObservedTime = System.currentTimeMillis()
                clipboardLastDismissedText = null
                
                updateSuggestions()
                coroutineScope.launch(Dispatchers.IO) {
                    clipboardRepository.insert(text)
                }
            }
        }
    }
    
    private fun toggleEmojiModal(open: Boolean? = null) {
        if (open != null) {
            isEmojiModalOpen = open
        } else {
            isEmojiModalOpen = !isEmojiModalOpen
        }
        
        val keyboardView = mainView?.findViewById<KeyboardView>(R.id.keyboard_view) ?: return
        val emojiContainer = mainView?.findViewById<android.view.View>(R.id.emoji_container) ?: return
        
        if (isEmojiModalOpen) {
            // Hide keyboard, show emoji container
            keyboardView.visibility = android.view.View.GONE
            emojiContainer.visibility = android.view.View.VISIBLE
            
            // Set up recycler view
            val recycler = mainView?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.emoji_recycler)
            if (recycler?.adapter == null) {
                recycler?.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 7)
                emojiAdapter = EmojiAdapter(getSmileysEmojis()) { emoji ->
                    currentInputConnection?.commitText(emoji, 1)
                }
                recycler?.adapter = emojiAdapter
                
                mainView?.findViewById<android.view.View>(R.id.btn_emoji_abc)?.setOnClickListener {
                    toggleEmojiModal(false)
                }
                mainView?.findViewById<android.view.View>(R.id.btn_emoji_backspace)?.setOnClickListener {
                    sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
                }
                
                setupEmojiCategories()
            }
        } else {
            // Show keyboard, hide emoji container
            keyboardView.visibility = android.view.View.VISIBLE
            emojiContainer.visibility = android.view.View.GONE
        }
    }
    
    private fun getSmileysEmojis(): List<String> {
        return listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤫", "🤭", "🥱", "🤗", "🫣", "🤔", "🫡", "🤐", "🤨", "😐", "😑", "😶", "😶‍🌫️", "😏", "😒", "🙄", "😬", "😮", "😦", "😧", "😲", "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
        )
    }
    
    private fun setupEmojiCategories() {
        val container = mainView?.findViewById<android.widget.LinearLayout>(R.id.emoji_category_container) ?: return
        container.removeAllViews()
        
        val categories = listOf(
            Pair("ic_emoji_smileys_emotion", "Smileys")
            // Can add more later
        )
        
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        
        for (cat in categories) {
            val resId = resources.getIdentifier(cat.first, "drawable", packageName)
            val ib = android.widget.ImageButton(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(120, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setImageResource(resId)
                setBackgroundResource(typedValue.resourceId)
                setPadding(16, 16, 16, 16)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                contentDescription = cat.second
            }
            container.addView(ib)
        }
    }
    
    private fun toggleClipboardModal() {
        isClipboardModalOpen = !isClipboardModalOpen
        val keyboardView = mainView?.findViewById<KeyboardView>(R.id.keyboard_view) ?: return
        val clipboardContainer = mainView?.findViewById<android.view.View>(R.id.clipboard_container) ?: return
        val btnClearPinned = mainView?.findViewById<android.view.View>(R.id.btn_clipboard_clear_pinned)
        
        if (isClipboardModalOpen) {
            coroutineScope.launch(Dispatchers.IO) {
                clipboardRepository.cleanup()
            }
            keyboardView.visibility = View.GONE
            clipboardContainer.visibility = View.VISIBLE
            btnClearPinned?.visibility = View.VISIBLE
        } else {
            clipboardContainer.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            btnClearPinned?.visibility = View.GONE
        }
    }

    private fun setupToolbar(root: View) {
        val btnChevron = root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)
        val suggestionContent = root.findViewById<android.view.View>(R.id.suggestion_content)
        val expandedScroll = root.findViewById<android.view.View>(R.id.toolbar_expanded_scroll)
        val expandedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_expanded_content)
        val pinnedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_pinned)
        
        toolbarContainer = root.findViewById(R.id.toolbar_container)
        
        tvSuggestion1 = root.findViewById(R.id.suggestion_1)
        tvSuggestion2 = root.findViewById(R.id.suggestion_2)
        tvSuggestion3 = root.findViewById(R.id.suggestion_3)
        suggestionDivider1 = root.findViewById(R.id.suggestion_divider_1)
        suggestionDivider2 = root.findViewById(R.id.suggestion_divider_2)
        suggestionPaste = root.findViewById(R.id.suggestion_paste)
        suggestionPasteDivider = root.findViewById(R.id.suggestion_paste_divider)
        tvSuggestionPasteText = root.findViewById(R.id.tv_suggestion_paste_text)
        btnSuggestionPasteClose = root.findViewById(R.id.btn_suggestion_paste_close)
        
        tvSuggestion1?.setOnClickListener { onSuggestionClicked(tvSuggestion1?.text.toString()) }
        tvSuggestion2?.setOnClickListener { onSuggestionClicked(tvSuggestion2?.text.toString()) }
        tvSuggestion3?.setOnClickListener { onSuggestionClicked(tvSuggestion3?.text.toString()) }
        
        // When clicking the main paste chip, we commit the text directly.
        suggestionPaste?.setOnClickListener {
            val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrEmpty()) {
                currentInputConnection?.commitText(text, 1)
            }
            // Once pasted, dismiss it so it doesn't stay
            clipboardLastDismissedText = text
            updateSuggestions()
        }
        
        btnSuggestionPasteClose?.setOnClickListener {
            val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
            clipboardLastDismissedText = text
            updateSuggestions()
        }
        
        btnChevron.setOnClickListener {
            isToolbarExpanded = !isToolbarExpanded
            if (isToolbarExpanded) {
                btnChevron.setImageResource(R.drawable.ic_chevron_left)
                suggestionContent.visibility = View.GONE
                expandedScroll.visibility = View.VISIBLE
            } else {
                btnChevron.setImageResource(R.drawable.ic_chevron_right)
                suggestionContent.visibility = View.VISIBLE
                expandedScroll.visibility = View.GONE
            }
        }
        
        populateToolbar(root, expandedContent, pinnedContent)
    }

    private fun populateToolbar(root: View, expandedContent: android.widget.LinearLayout, pinnedContent: android.widget.LinearLayout) {
        expandedContent.removeAllViews()
        pinnedContent.removeAllViews()
        
        val context = expandedContent.context
        val pinnedKeys = com.example.keyboard.toolbar.ToolbarSettingsManager.getPinnedKeys(context).toMutableList()
        val expandedKeys = com.example.keyboard.toolbar.ToolbarSettingsManager.getToolbarKeys(context).toMutableList()
        
        // Remove pinned keys from expanded keys so they don't duplicate
        expandedKeys.removeAll(pinnedKeys)
        
        btnIncognito = null // Reset ref
        
        val buttonSize = (36 * context.resources.displayMetrics.density).toInt()
        val marginEnd = 0
        
        fun createButton(actionId: String, isPinned: Boolean): android.widget.ImageButton? {
            val action = com.example.keyboard.toolbar.ToolbarSettingsManager.ALL_ACTIONS.find { it.id == actionId } ?: return null
            val btn = android.widget.ImageButton(context)
            val params = android.widget.LinearLayout.LayoutParams(buttonSize, buttonSize)
            params.marginEnd = marginEnd
            btn.layoutParams = params
            
            // Set simple transparent background
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            btn.setBackgroundResource(typedValue.resourceId)
            btn.contentDescription = action.name
            
            // Map icon
            val resId = action.iconResId
            btn.setImageResource(resId)
            
            // Register specific references
            if (actionId == "INCOGNITO") {
                btnIncognito = btn
                if (isManualIncognito || (currentInputEditorInfo?.imeOptions?.and(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)) {
                    btn.setImageResource(R.drawable.ic_incognito_on)
                }
            }
            
            btn.setOnClickListener { handleToolbarAction(actionId) }
            
            btn.setOnLongClickListener {
                when (actionId) {
                    "SELECT_WORD" -> {
                        handleToolbarAction("SELECT_ALL")
                        true
                    }
                    "PASTE" -> {
                        handleToolbarAction("CLIPBOARD")
                        true
                    }
                    else -> false
                }
            }
            return btn
        }
        
        expandedKeys.forEach { actionId ->
            createButton(actionId, false)?.let { expandedContent.addView(it) }
        }
        
        pinnedKeys.forEach { actionId ->
            createButton(actionId, true)?.let { pinnedContent.addView(it) }
        }
    }
    
    private fun handleToolbarAction(actionId: String) {
        when (actionId) {
            "SETTINGS" -> {
                val intent = android.content.Intent(this, com.example.keyboard.SettingsActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "SELECT_ALL" -> sendSelectAll()
            "PASTE" -> currentInputConnection?.performContextMenuAction(android.R.id.paste)
            "CLIPBOARD" -> toggleClipboardModal()
            "ENTER" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
            "COPY" -> currentInputConnection?.performContextMenuAction(android.R.id.copy)
            "CUT" -> currentInputConnection?.performContextMenuAction(android.R.id.cut)
            "UNDO" -> sendUndo()
            "REDO" -> sendRedo()
            "LEFT" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            "RIGHT" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            "UP" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_UP)
            "DOWN" -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            "CLEAR_CLIP" -> {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    clipboardRepository.deleteAllUnpinned()
                }
                android.widget.Toast.makeText(this, "Unpinned clipboard items cleared", android.widget.Toast.LENGTH_SHORT).show()
                if (isClipboardModalOpen) {
                    toggleClipboardModal()
                }
            }
            "SELECT_WORD" -> {
                val ic = currentInputConnection
                if (ic != null) {
                    val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    if (extracted != null && extracted.text != null) {
                        val pos = extracted.selectionStart
                        val text = extracted.text
                        var start = pos
                        var end = pos
                        while (start > 0 && text[start - 1].isLetterOrDigit()) {
                            start--
                        }
                        while (end < text.length && text[end].isLetterOrDigit()) {
                            end++
                        }
                        ic.setSelection(start, end)
                    }
                }
            }
            "VOICE_INPUT" -> {
                try {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Voice input not supported", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            "EMOJI" -> {
                android.widget.Toast.makeText(this, "Emoji coming soon", android.widget.Toast.LENGTH_SHORT).show()
            }
            "INCOGNITO" -> {
                isManualIncognito = !isManualIncognito
                updateIncognitoStateUI()
                currentWord.clear()
                previousWord = null
                clearSuggestions()
                val stateText = if (isIncognitoActive()) "Incognito Mode ON" else "Incognito Mode OFF"
                android.widget.Toast.makeText(this, stateText, android.widget.Toast.LENGTH_SHORT).show()
            }
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
        
        inputConnection.deleteSurroundingText(wordLengthBeforeCursor, wordLengthAfterCursor)
        inputConnection.commitText(finalWord + " ", 1)
        commitWord(word)
        wordLengthBeforeCursor = 0
        wordLengthAfterCursor = 0
    }

    private fun commitWord(word: String) {
        val finalWord = word.lowercase()
        val prevToSave = previousWord
        val prevPrevToSave = prevPrevWord
        prevPrevWord = previousWord
        previousWord = finalWord
        currentWord.clear()
        
        if (isIncognitoActive()) {
            updateSuggestions()
            return
        }
        
        coroutineScope.launch {
            // Learn new word dynamically
            dictionaryEngine.insertWord(finalWord, prevWord = prevToSave, prevPrevWord = prevPrevToSave)
            updateSuggestions()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Refresh Toolbar configuration to catch Settings changes
        mainView?.let { root ->
            val expandedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_expanded_content)
            val pinnedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_pinned)
            if (expandedContent != null && pinnedContent != null) {
                populateToolbar(root, expandedContent, pinnedContent)
            }
        }

        val clipboardContainer = mainView?.findViewById<android.view.View>(R.id.clipboard_container)
        val emojiContainer = mainView?.findViewById<android.view.View>(R.id.emoji_container)
        val keyboardView = mainView?.findViewById<com.example.keyboard.KeyboardView>(R.id.keyboard_view)
        clipboardContainer?.visibility = android.view.View.GONE
        emojiContainer?.visibility = android.view.View.GONE
        keyboardView?.visibility = android.view.View.VISIBLE
        isClipboardModalOpen = false
        isEmojiModalOpen = false

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

    private var wordLengthBeforeCursor = 0
    private var wordLengthAfterCursor = 0

    private fun extractWordAtCursor() {
        val ic = currentInputConnection ?: return
        
        if (isIncognitoActive()) {
            currentWord.clear()
            wordLengthBeforeCursor = 0
            wordLengthAfterCursor = 0
            clearSuggestions()
            return
        }

        val textBefore = ic.getTextBeforeCursor(50, 0) ?: ""
        val textAfter = ic.getTextAfterCursor(50, 0) ?: ""
        
        var beforeIndex = textBefore.length - 1
        while (beforeIndex >= 0 && textBefore[beforeIndex].isLetter()) {
            beforeIndex--
        }
        val wordBefore = textBefore.substring(beforeIndex + 1)
        
        var afterIndex = 0
        while (afterIndex < textAfter.length && textAfter[afterIndex].isLetter()) {
            afterIndex++
        }
        val wordAfter = textAfter.substring(0, afterIndex)
        
        val fullWord = wordBefore + wordAfter
        
        if (fullWord.isNotEmpty()) {
            val hasChanged = fullWord != currentWord.toString()
            currentWord.clear()
            currentWord.append(fullWord)
            wordLengthBeforeCursor = wordBefore.length
            wordLengthAfterCursor = wordAfter.length
            if (hasChanged) {
                updateSuggestions()
            }
        } else {
            if (currentWord.isNotEmpty()) {
                currentWord.clear()
                wordLengthBeforeCursor = 0
                wordLengthAfterCursor = 0
                clearSuggestions()
            }
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShiftState()
        
        if (newSelStart != newSelEnd) {
            currentWord.clear()
            wordLengthBeforeCursor = 0
            wordLengthAfterCursor = 0
            clearSuggestions()
            return
        }
        
        extractWordAtCursor()
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
            mainView?.findViewById<android.view.ViewGroup>(R.id.suggestion_paste)?.let { 
                (it.getChildAt(1) as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
                (it.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(android.graphics.Color.WHITE)
            }
            
            // Set button icons to white tint
            val whiteColor = android.graphics.Color.WHITE
            mainView?.let { root ->
                root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)?.setColorFilter(whiteColor)
                
                val expandedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_expanded_content)
                val pinnedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_pinned)
                
                for (i in 0 until (expandedContent?.childCount ?: 0)) {
                    (expandedContent?.getChildAt(i) as? android.widget.ImageButton)?.setColorFilter(whiteColor)
                }
                for (i in 0 until (pinnedContent?.childCount ?: 0)) {
                    (pinnedContent?.getChildAt(i) as? android.widget.ImageButton)?.setColorFilter(whiteColor)
                }
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
            mainView?.findViewById<android.view.ViewGroup>(R.id.suggestion_paste)?.let { 
                (it.getChildAt(1) as? android.widget.TextView)?.setTextColor(darkGray)
                (it.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(darkGray)
            }
            
            // Reset button icons to dark gray tint
            mainView?.let { root ->
                root.findViewById<android.widget.ImageButton>(R.id.btn_toolbar_chevron)?.setColorFilter(darkGray)
                
                val expandedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_expanded_content)
                val pinnedContent = root.findViewById<android.widget.LinearLayout>(R.id.toolbar_pinned)
                
                for (i in 0 until (expandedContent?.childCount ?: 0)) {
                    (expandedContent?.getChildAt(i) as? android.widget.ImageButton)?.setColorFilter(darkGray)
                }
                for (i in 0 until (pinnedContent?.childCount ?: 0)) {
                    (pinnedContent?.getChildAt(i) as? android.widget.ImageButton)?.setColorFilter(darkGray)
                }
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

    override fun onWindowShown() {
        super.onWindowShown()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        coroutineScope.launch {
             // cancel all in scope
        } // or just let it die. It's tied to service lifecycle.
        logKeeper.log("INFO", "ViaboardService", "Service Destroyed")
    }
    
    private fun updateSuggestions() {
        val prefix = currentWord.toString()
        suggestionJob?.cancel()
        
        var showPaste = false
        val clip = clipboardManager?.primaryClip
        if (clip != null && clip.itemCount > 0 && prefix.isEmpty()) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty() && text != clipboardLastDismissedText) {
                // Determine if clip was caught recently
                if (text != clipboardLastObservedText) {
                    // Clipboard changed while we weren't listening, or this is a new service instance
                    clipboardLastObservedText = text
                    clipboardObservedTime = System.currentTimeMillis()
                }
                
                // Show only if within the last 5 minutes (300,000 ms)
                if (System.currentTimeMillis() - clipboardObservedTime < 300_000L) {
                    showPaste = true
                    tvSuggestionPasteText?.text = text.replace("\n", " ")
                }
            }
        }
        
        if (showPaste) {
            suggestionPaste?.visibility = View.VISIBLE
        } else {
            suggestionPaste?.visibility = View.GONE
            suggestionPasteDivider?.visibility = View.GONE
        }
        
        if (prefix.isBlank()) {
            suggestionJob = coroutineScope.launch {
                val list = dictionaryEngine.getSuggestions(prefix, previousWord, prevPrevWord, 3)
                currentSuggestions = list
                
                tvSuggestion1?.text = list.getOrNull(0) ?: ""
                tvSuggestion1?.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                
                tvSuggestion2?.text = list.getOrNull(1) ?: ""
                tvSuggestion2?.visibility = if (list.size > 1) View.VISIBLE else View.GONE
                
                tvSuggestion3?.text = list.getOrNull(2) ?: ""
                tvSuggestion3?.visibility = if (list.size > 2) View.VISIBLE else View.GONE
            }
            if (showPaste) {
                suggestionPasteDivider?.visibility = View.GONE
            }
            return
        }

        suggestionJob = coroutineScope.launch {
            val list = dictionaryEngine.getSuggestions(prefix, previousWord, prevPrevWord, 3)
            currentSuggestions = list
            
            tvSuggestion1?.text = list.getOrNull(0) ?: ""
            tvSuggestion1?.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            
            tvSuggestion2?.text = list.getOrNull(1) ?: ""
            tvSuggestion2?.visibility = if (list.size > 1) View.VISIBLE else View.GONE
            
            tvSuggestion3?.text = list.getOrNull(2) ?: ""
            tvSuggestion3?.visibility = if (list.size > 2) View.VISIBLE else View.GONE
            
            suggestionDivider1?.visibility = if (list.size > 1) View.VISIBLE else View.GONE
            suggestionDivider2?.visibility = if (list.size > 2) View.VISIBLE else View.GONE
            
            if (showPaste) {
                suggestionPasteDivider?.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun clearSuggestions() {
        suggestionJob?.cancel()
        currentSuggestions = emptyList()
        tvSuggestion1?.text = ""
        tvSuggestion1?.visibility = View.GONE
        tvSuggestion2?.text = ""
        tvSuggestion2?.visibility = View.GONE
        tvSuggestion3?.text = ""
        tvSuggestion3?.visibility = View.GONE
        suggestionDivider1?.visibility = View.GONE
        suggestionDivider2?.visibility = View.GONE
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
                    if (currentWord.isNotEmpty() && wordLengthBeforeCursor > 0) {
                        currentWord.deleteCharAt(wordLengthBeforeCursor - 1)
                        wordLengthBeforeCursor--
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

                if (isAutocorrectEnabled && currentSuggestions.isNotEmpty() && currentWord.isNotEmpty() && currentSuggestions[0].lowercase() != currentWord.toString().lowercase()) {
                    val topWordText = currentSuggestions[0]
                    val isCapitalized = currentWord[0].isUpperCase()
                    val topWord = if (isCapitalized) {
                        topWordText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    } else {
                        topWordText
                    }
                    inputConnection.deleteSurroundingText(wordLengthBeforeCursor, wordLengthAfterCursor)
                    inputConnection.commitText(topWord + " ", 1)
                    commitWord(topWordText)
                    lastSpaceTime = now
                    wordLengthBeforeCursor = 0
                    wordLengthAfterCursor = 0
                } else {
                    inputConnection.commitText(" ", 1)
                    if (currentWord.isNotEmpty()) {
                        commitWord(currentWord.toString())
                    }
                    lastSpaceTime = now
                }
                updateShiftState()
            }
            "ENTER" -> handleEnterAction()
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
            "MODE_NUMPAD" -> switchKeyboardLayout(R.xml.kbd_numpad)
            "MODE_EMOJI" -> {
                isEmojiModalOpen = true
                toggleEmojiModal(true)
            }
            "SETTINGS" -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            "ONE_HAND" -> { /* TODO */ }
            "CLIPBOARD" -> { /* TODO */ }
            "SELECT_ALL" -> sendSelectAll()
            "COPY" -> inputConnection.performContextMenuAction(android.R.id.copy)
            "PASTE" -> inputConnection.performContextMenuAction(android.R.id.paste)
            "CUT" -> inputConnection.performContextMenuAction(android.R.id.cut)
            "UNDO" -> sendUndo()
            "REDO" -> sendRedo()
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
                    currentWord.insert(wordLengthBeforeCursor, finalKey)
                    wordLengthBeforeCursor += finalKey.length
                    updateSuggestions()
                } else {
                    currentWord.clear()
                    wordLengthBeforeCursor = 0
                    wordLengthAfterCursor = 0
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
        val currentText = inputConnection.getTextBeforeCursor(100000, 0)
        if (!currentText.isNullOrEmpty()) {
            inputConnection.deleteSurroundingText(currentText.length, 0)
        }
    }

    override fun onSwipeCursor(dx: Int) {
        val count = kotlin.math.abs(dx)
        for (i in 0 until count) {
            if (dx > 0) {
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            } else if (dx < 0) {
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            }
        }
    }

    override fun onSwipeDelete(deleteCount: Int) {
        val inputConnection = currentInputConnection ?: return
        if (deleteCount > 0) {
            inputConnection.deleteSurroundingText(deleteCount, 0)
            if (currentWord.isNotEmpty()) {
                val deleteFromWord = kotlin.math.min(deleteCount, wordLengthBeforeCursor)
                currentWord.delete(wordLengthBeforeCursor - deleteFromWord, wordLengthBeforeCursor)
                wordLengthBeforeCursor -= deleteFromWord
                updateSuggestions()
            }
        }
    }

    private fun handleEnterAction() {
        val ic = currentInputConnection ?: return
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
        }
        
        if (currentWord.isNotEmpty()) {
            commitWord(currentWord.toString())
        }
        previousWord = null
        updateSuggestions()
        updateShiftState()
    }

    private fun sendUndo() {
        val ic = currentInputConnection
        ic?.performContextMenuAction(android.R.id.undo)
        
        // Desktop Shortcut Undo (Ctrl+Z)
        val metaState = android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON
        val eventTime = android.os.SystemClock.uptimeMillis()
        val downEvent = android.view.KeyEvent(
            eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_Z, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        val upEvent = android.view.KeyEvent(
            eventTime, android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_Z, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        ic?.sendKeyEvent(downEvent)
        ic?.sendKeyEvent(upEvent)
    }

    private fun sendRedo() {
        val ic = currentInputConnection
        ic?.performContextMenuAction(android.R.id.redo)
        
        // Desktop Shortcut Redo (Ctrl+Y / Ctrl+Shift+Z, let's use Ctrl+Shift+Z)
        val metaState = android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON or android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON
        val eventTime = android.os.SystemClock.uptimeMillis()
        val downEvent = android.view.KeyEvent(
            eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_Z, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        val upEvent = android.view.KeyEvent(
            eventTime, android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_Z, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        ic?.sendKeyEvent(downEvent)
        ic?.sendKeyEvent(upEvent)
    }

    private fun sendSelectAll() {
        val ic = currentInputConnection
        ic?.performContextMenuAction(android.R.id.selectAll)
        
        // Desktop Shortcut Select All (Ctrl+A)
        val metaState = android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON
        val eventTime = android.os.SystemClock.uptimeMillis()
        val downEvent = android.view.KeyEvent(
            eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_A, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        val upEvent = android.view.KeyEvent(
            eventTime, android.os.SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_A, 0, metaState,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        ic?.sendKeyEvent(downEvent)
        ic?.sendKeyEvent(upEvent)
    }

    override fun onLongPressEnter() {
        logKeeper.log("USER_ACTION", "ViaboardService", "Triggering Log Keeper via Long-Press Enter")
        logKeeper.exportLogsToDownloads()
        android.widget.Toast.makeText(this, "Exporting Logs...", android.widget.Toast.LENGTH_SHORT).show()
    }
}
