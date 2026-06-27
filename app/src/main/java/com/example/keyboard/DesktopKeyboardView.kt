package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

interface DesktopKeyListener {
    fun onDesktopKey(code: String)
}

data class DesktopKey(
    val code: String,
    val label: String,
    val sublabel: String = "",
    val longPressCode: String = "",
    val longPressLabel: String = "",
    val longPressOptions: List<String> = emptyList(),
    val isFunctional: Boolean = false
) {
    var rect: RectF = RectF()
    var isPressed: Boolean = false
}

class DesktopKeyboardView(context: Context) : View(context) {
    var listener: DesktopKeyListener? = null
    var isSelectMode = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val lpTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    
    private val keys = mutableListOf<List<DesktopKey>>()
    
    private var pressedKey: DesktopKey? = null
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { triggerLongPress() }
    
    private var popupWindow: PopupWindow? = null
    private var isLongPressTriggered = false
    
    init {
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        
        subTextPaint.color = Color.parseColor("#888888")
        subTextPaint.textAlign = Paint.Align.CENTER
        
        lpTextPaint.color = Color.parseColor("#666666")
        lpTextPaint.textAlign = Paint.Align.RIGHT

        setupKeys()
    }

    private fun setupKeys() {
        keys.add(listOf(
            DesktopKey("1", "1", "", "", "", listOf("¹", "½")),
            DesktopKey("2", "2", "", "", "", listOf("²", "⅔")),
            DesktopKey("3", "3", "", "", "", listOf("³", "¾")),
            DesktopKey("4", "4", "", "", "", listOf("⁴")),
            DesktopKey("5", "5", "", "", "", listOf("⁵", "⅝")),
            DesktopKey("6", "6", "", "", "", listOf("⁶")),
            DesktopKey("7", "7", "", "", "", listOf("⁷", "⅞")),
            DesktopKey("8", "8", "", "", "", listOf("⁸")),
            DesktopKey("9", "9", "", "", "", listOf("⁹")),
            DesktopKey("0", "0", "", "", "", listOf("⁰", "∅"))
        ))
        
        keys.add(listOf(
            DesktopKey("DSK_ESC", "ESC", "Esc", isFunctional = true),
            DesktopKey("DSK_TAB", "TAB", "Tab", isFunctional = true),
            DesktopKey("DSK_STABF", "S+TAB", "Sh+Tab", isFunctional = true),
            DesktopKey("DSK_F1", "F1", "F1", isFunctional = true),
            DesktopKey("DSK_FINDREPLACE", "C+H", "Ctrl+H", isFunctional = true),
            DesktopKey("DSK_UP", "↑", "Up", "DSK_PGUP", "PgUp", isFunctional = true),
            DesktopKey("DSK_HOME", "HOME", "Home", isFunctional = true),
            DesktopKey("DSK_END", "END", "End", isFunctional = true),
            DesktopKey("DSK_UNDO", "UNDO", "Ctrl+Z", isFunctional = true),
            DesktopKey("DSK_REDO", "REDO", "Ctrl+Y", isFunctional = true)
        ))
        
        keys.add(listOf(
            DesktopKey("DSK_SELWORD", "SELWRD", "Ct+Sh+→", "DSK_SELALL", "SelAll ↗", isFunctional = true),
            DesktopKey("DSK_COPY", "COPY", "Ctrl+C", isFunctional = true),
            DesktopKey("DSK_CUT", "CUT", "Ctrl+X", isFunctional = true),
            DesktopKey("DSK_PASTE", "PASTE", "Ctrl+V", isFunctional = true),
            DesktopKey("DSK_LEFT", "←", "Left", isFunctional = true),
            DesktopKey("DSK_SEL", "SEL", "Shift", isFunctional = true),
            DesktopKey("DSK_RIGHT", "→", "Right", isFunctional = true),
            DesktopKey("DSK_DUPLINE", "C+D", "Ctrl+D", isFunctional = true),
            DesktopKey("DSK_COMMENT", "C+/", "Ctrl+/", isFunctional = true),
            DesktopKey("DSK_BKSP", "BKSP", "Bksp", "DSK_DEL", "FwdDel ↗", isFunctional = true) // long press FwdDel
        ))
        
        keys.add(listOf(
            DesktopKey("DSK_INDENT", "INDENT", "Tab", isFunctional = true),
            DesktopKey("DSK_DEDENT", "DEDENT", "Sh+Tab", isFunctional = true),
            DesktopKey("DSK_GOTOLINE", "C+G", "Ctrl+G", isFunctional = true),
            DesktopKey("DSK_COMMENT2", "REM", "Ctrl+/", isFunctional = true),
            DesktopKey("DSK_GOTOLINE2", "GOTO", "Ctrl+G", isFunctional = true),
            DesktopKey("DSK_DOWN", "↓", "Down", "DSK_PGDN", "PgDn ↗", isFunctional = true),
            DesktopKey("DSK_DELLINE", "C+K", "Ctrl+Sh+K", isFunctional = true),
            DesktopKey("DSK_DEL", "DEL", "Del", "DSK_DELLINE", "DelLine ↗", isFunctional = true) // long press DelLine
        ))
        
        keys.add(listOf(
            DesktopKey("MODE_ALPHABET", "ABC", isFunctional = true),
            DesktopKey(",", ","),
            DesktopKey("SPACE", "Space", isFunctional = true),
            DesktopKey(".", ".", "", "", "", listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", ",", "?", "]", "[")),
            DesktopKey("DSK_ENTER", "ENTER", "Enter", "DSK_SAVE", "Save ↗", isFunctional = true)
        ))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyRects(w, h)
    }

    private fun calculateKeyRects(w: Int, h: Int) {
        val rowHeight = h / 5f
        val padding = 4f
        
        for (rowIndex in 0..4) {
            val row = keys[rowIndex]
            val keyCount = row.size
            
            if (rowIndex < 4) {
                val keyWidth = w / keyCount.toFloat()
                for (i in 0 until keyCount) {
                    val key = row[i]
                    key.rect.set(
                        i * keyWidth + padding,
                        rowIndex * rowHeight + padding,
                        (i + 1) * keyWidth - padding,
                        (rowIndex + 1) * rowHeight - padding
                    )
                }
            } else {
                // Row 5
                val standardKeyWidth = w / 10f * 1.5f
                var currentX = 0f
                for (i in 0 until keyCount) {
                    val key = row[i]
                    val kw = if (key.code == "SPACE") {
                        w - (keyCount - 1) * standardKeyWidth
                    } else {
                        standardKeyWidth
                    }
                    key.rect.set(
                        currentX + padding,
                        rowIndex * rowHeight + padding,
                        currentX + kw - padding,
                        (rowIndex + 1) * rowHeight - padding
                    )
                    currentX += kw
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val density = resources.displayMetrics.scaledDensity
        val normalBgColor = Color.parseColor("#333333")
        val functionalBgColor = Color.parseColor("#262626")
        val pressedBgColor = Color.parseColor("#555555")
        val selActiveColor = Color.parseColor("#4CAF50")
        
        for (row in keys) {
            for (key in row) {
                // Background
                bgPaint.color = when {
                    key.isPressed -> pressedBgColor
                    key.code == "DSK_SEL" && isSelectMode -> selActiveColor
                    key.isFunctional -> functionalBgColor
                    else -> normalBgColor
                }
                canvas.drawRoundRect(key.rect, 12f, 12f, bgPaint)
                
                // Main label
                textPaint.textSize = 13f * density
                val maxLabelWidth = key.rect.width() - 8f * density
                
                var labelText = key.label
                var lines = listOf(labelText)
                
                if (textPaint.measureText(labelText) > maxLabelWidth && labelText.contains(" ")) {
                    val parts = labelText.split(" ", limit = 2)
                    lines = parts
                    textPaint.textSize = 11f * density
                } else if (textPaint.measureText(labelText) > maxLabelWidth) {
                    val mid = labelText.length / 2
                    lines = listOf(labelText.substring(0, mid), labelText.substring(mid))
                    textPaint.textSize = 11f * density
                }
                
                val textHeight = textPaint.descent() - textPaint.ascent()
                
                if (lines.size == 1) {
                    val textY = key.rect.top + key.rect.height() * 0.4f - ((textPaint.descent() + textPaint.ascent()) / 2)
                    canvas.drawText(lines[0], key.rect.centerX(), textY, textPaint)
                } else {
                    val startY = key.rect.top + key.rect.height() * 0.4f - textHeight
                    canvas.drawText(lines[0], key.rect.centerX(), startY, textPaint)
                    canvas.drawText(lines[1], key.rect.centerX(), startY + textHeight, textPaint)
                }
                
                // Sublabel
                if (key.sublabel.isNotEmpty()) {
                    subTextPaint.textSize = 9f * density
                    var sublabelToDraw = key.sublabel
                    if (subTextPaint.measureText(sublabelToDraw) > maxLabelWidth) {
                        sublabelToDraw = TextUtils.ellipsize(sublabelToDraw, subTextPaint, maxLabelWidth, TextUtils.TruncateAt.END).toString()
                    }
                    val subY = key.rect.top + key.rect.height() * 0.75f - ((subTextPaint.descent() + subTextPaint.ascent()) / 2)
                    canvas.drawText(sublabelToDraw, key.rect.centerX(), subY, subTextPaint)
                }
                
                // Long press indicator
                if (key.longPressCode.isNotEmpty() || key.longPressOptions.isNotEmpty()) {
                    lpTextPaint.textSize = 8f * density
                    val lpX = key.rect.right - 4f * density
                    val lpY = key.rect.bottom - 4f * density
                    var lpText = key.longPressLabel
                    if (lpText.isEmpty()) lpText = "↘"
                    canvas.drawText(lpText, lpX, lpY, lpTextPaint)
                }
            }
        }
    }
    
    fun toggleSelectMode() {
        isSelectMode = !isSelectMode
        invalidate()
    }
    
    fun disarmSelectMode() {
        if (isSelectMode) {
            isSelectMode = false
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(x, y)
                if (key != null) {
                    pressedKey = key
                    key.isPressed = true
                    isLongPressTriggered = false
                    handler.postDelayed(longPressRunnable, 500)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                pressedKey?.let {
                    it.isPressed = false
                    if (!isLongPressTriggered) {
                        fireKey(it)
                    }
                }
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                pressedKey?.isPressed = false
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                pressedKey?.let {
                    if (!it.rect.contains(x, y)) {
                        handler.removeCallbacks(longPressRunnable)
                        it.isPressed = false
                        pressedKey = null
                        invalidate()
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): DesktopKey? {
        for (row in keys) {
            for (key in row) {
                if (key.rect.contains(x, y)) {
                    return key
                }
            }
        }
        return null
    }

    private fun fireKey(key: DesktopKey) {
        if (key.code == "DSK_SEL") {
            toggleSelectMode()
            listener?.onDesktopKey(key.code)
            return
        }
        
        var codeToSend = key.code
        
        if (isSelectMode) {
            when (key.code) {
                "DSK_LEFT" -> codeToSend = "DSK_SEL_LEFT"
                "DSK_RIGHT" -> codeToSend = "DSK_SEL_RIGHT"
                "DSK_UP" -> codeToSend = "DSK_SEL_UP"
                "DSK_DOWN" -> codeToSend = "DSK_SEL_DOWN"
            }
        }
        
        listener?.onDesktopKey(codeToSend)
        
        if (key.code == "DSK_COPY" || key.code == "DSK_CUT" || key.code == "DSK_BKSP") {
            disarmSelectMode()
        }
    }

    private fun triggerLongPress() {
        val key = pressedKey ?: return
        isLongPressTriggered = true
        
        if (key.longPressOptions.isNotEmpty()) {
            showPopup(key)
        } else if (key.longPressCode.isNotEmpty()) {
            listener?.onDesktopKey(key.longPressCode)
        }
        
        key.isPressed = false
        pressedKey = null
        invalidate()
    }

    private fun showPopup(key: DesktopKey) {
        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#444444"))
            setPadding(10, 10, 10, 10)
        }
        
        for (opt in key.longPressOptions) {
            val tv = TextView(context).apply {
                text = opt
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(30, 20, 30, 20)
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener {
                    listener?.onDesktopKey(opt)
                    popupWindow?.dismiss()
                }
            }
            popupView.addView(tv)
        }
        
        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = (location[0] + key.rect.centerX() - (key.longPressOptions.size * 100) / 2).toInt().coerceAtLeast(0)
        val y = (location[1] + key.rect.top - 150).toInt()
        
        popupWindow?.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }
}
