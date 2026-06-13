package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface KeyboardListener {
        fun onKeyPress(key: String)
        fun onLongPressKey(key: String, keyRect: RectF, keyboardView: View)
        fun onLongPressEnter()
        fun onLongPressBackspace()
    }

    var listener: KeyboardListener? = null
    fun getKeyboard(): Keyboard? = keyboard
    private var keyboard: Keyboard? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPressRunnable = Runnable { triggerLongPress() }

    private var isAccentPopupVisible = false
    private var accentOptions = emptyList<String>()
    private var accentOptionRects = mutableListOf<Pair<String, RectF>>()
    private var hoveredAccentIndex = -1
    private var accentPopupRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C5CAD1")
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 55f
    }

    private val accentPopupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    
    private val accentPopupShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.LTGRAY
        strokeWidth = 2f
    }

    private val accentHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#777777")
        textAlign = Paint.Align.RIGHT
        textSize = 26f
    }

    private val keyMarginHorizontal = 8f
    private val keyMarginVertical = 10f
    private val cornerRadius = 14f

    fun setKeyboard(kbd: Keyboard) {
        this.keyboard = kbd
        if (width > 0 && height > 0) {
            calculateKeyRects(width, height)
        }
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyRects(w, h)
    }

    private fun calculateKeyRects(w: Int, h: Int) {
        val kbd = keyboard ?: return
        if (kbd.rows.isEmpty()) return
        
        val rowHeight = h / kbd.rows.size.toFloat()
        
        for ((rowIndex, row) in kbd.rows.withIndex()) {
            val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            var currentX = 0f
            
            for (key in row.keys) {
                val keyWidth = if (totalWeight > 0f) (key.widthWeight / totalWeight) * w else 0f
                key.x = currentX
                key.y = rowIndex * rowHeight
                key.width = keyWidth
                key.height = rowHeight
                
                currentX += keyWidth
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kbd = keyboard ?: return
        
        for (row in kbd.rows) {
            for (key in row.keys) {
                if (key.codes.isEmpty() && key.widthWeight > 0) continue // It's a spacer

                val rect = RectF(
                    key.x + keyMarginHorizontal,
                    key.y + keyMarginVertical,
                    key.x + key.width - keyMarginHorizontal,
                    key.y + key.height - keyMarginVertical
                )
                
                // Draw Shadow
                val shadowRect = RectF(rect.left, rect.top, rect.right, rect.bottom + 6f)
                canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
                
                // Draw Key Background
                bgPaint.color = if (key == pressedKey && !isAccentPopupVisible) Color.LTGRAY 
                                else if (key.isFunctional) Color.parseColor("#B4BACC") 
                                else Color.WHITE
                
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Draw text
                val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(key.label, rect.centerX(), textY, textPaint)
                
                // Draw hint
                if (key.codes.length == 1 && key.label.length == 1) {
                    val hints = getAccentsForKey(key.codes)
                    if (hints.size > 1) {
                        val hintChar = hints[1]
                        if (hintChar != key.codes && hintChar.length == 1) {
                            val hintX = rect.right - 12f
                            val hintY = rect.top + 32f
                            canvas.drawText(hintChar, hintX, hintY, hintPaint)
                        }
                    }
                }
            }
        }

        if (isAccentPopupVisible) {
            // Draw Popup shadow/background
            canvas.drawRoundRect(accentPopupRect, cornerRadius, cornerRadius, accentPopupPaint)
            canvas.drawRoundRect(accentPopupRect, cornerRadius, cornerRadius, accentPopupShadowPaint)

            // Draw options
            for ((index, optionData) in accentOptionRects.withIndex()) {
                val (option, rect) = optionData
                if (index == hoveredAccentIndex) {
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, accentHoverPaint)
                }

                val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(getLabelForAccentCode(option), rect.centerX(), textY, textPaint)
            }
        }
    }

    private var pressedKey: Key? = null
    private var isPressedValid = false

    private fun getLabelForAccentCode(code: String): String {
        return when (code) {
            "MODE_NUMPAD" -> "123"
            "MODE_EMOJI" -> "🙂"
            "MODE_NAVIGATION" -> "⇦"
            "MODE_DESKTOP" -> "PC"
            "MODE_SYMBOLS_SHIFT" -> "=\\<"
            "SETTINGS" -> "⚙️"
            "ONE_HAND" -> "🗗"
            "CLIPBOARD" -> "📋"
            else -> code
        }
    }

    private fun getAccentsForKey(key: String): List<String> {
        return when (key.lowercase()) {
            "q" -> listOf("q", "1", "!")
            "w" -> listOf("w", "2", "@")
            "e" -> listOf("e", "3", "é", "è", "ê")
            "r" -> listOf("r", "4", "#")
            "t" -> listOf("t", "5", "%")
            "y" -> listOf("y", "6", "^")
            "u" -> listOf("u", "7", "ú", "ù", "û")
            "i" -> listOf("i", "8", "í", "ì", "î")
            "o" -> listOf("o", "9", "ó", "ò", "ô")
            "p" -> listOf("p", "0", "*")
            "a" -> listOf("a", "@", "á", "à", "â")
            "s" -> listOf("s", "#", "ß", "ś", "š")
            "d" -> listOf("d", "$")
            "f" -> listOf("f", "%")
            "g" -> listOf("g", "&")
            "h" -> listOf("h", "-")
            "j" -> listOf("j", "+")
            "k" -> listOf("k", "(")
            "l" -> listOf("l", ")")
            "z" -> listOf("z", "*")
            "x" -> listOf("x", "\"")
            "c" -> listOf("c", "'", "ç", "ć", "č")
            "v" -> listOf("v", ":")
            "b" -> listOf("b", ";")
            "n" -> listOf("n", "!", "ñ", "ń", "ň")
            "m" -> listOf("m", "?")
            "!" -> listOf("!", "¡")
            "?" -> listOf("?", "¿")
            "mode_symbols" -> listOf("MODE_NUMPAD", "MODE_EMOJI", "MODE_NAVIGATION", "MODE_SYMBOLS_SHIFT", "MODE_DESKTOP")
            "." -> listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", ",", "?", "]", "[")
            "," -> listOf("ONE_HAND", "SETTINGS", "CLIPBOARD")
            else -> emptyList()
        }
    }

    private fun triggerLongPress() {
        val key = pressedKey ?: return
        if (!isPressedValid) return

        if (key.codes == "ENTER") {
            listener?.onLongPressEnter()
            pressedKey = null
            isPressedValid = false
            invalidate()
        } else if (key.codes == "DEL") {
            listener?.onLongPressBackspace()
            pressedKey = null
            isPressedValid = false
            invalidate()
        } else if (key.codes == "SHIFT") {
            listener?.onLongPressKey("SHIFT", RectF(key.x, key.y, key.x + key.width, key.y + key.height), this)
            pressedKey = null
            isPressedValid = false
            invalidate()
        } else {
            val options = getAccentsForKey(key.codes)
            if (options.isNotEmpty()) {
                isAccentPopupVisible = true
                accentOptions = options
                hoveredAccentIndex = -1
                
                val maxCols = 6
                val cols = if (options.size > maxCols) maxCols else options.size
                val rows = (options.size + cols - 1) / cols
                
                val defaultKeyWidth = width / 10f
                val popupItemWidth = defaultKeyWidth * 0.95f
                val popupItemHeight = key.height * 1.15f
                
                val totalWidth = popupItemWidth * cols
                val totalHeight = popupItemHeight * rows
                
                var startX = key.x + (key.width / 2) - (totalWidth / 2)
                if (startX < 0) startX = 10f
                if (startX + totalWidth > width) startX = width - totalWidth - 10f
                
                // make it pop up slightly above the key
                var popupY = key.y - totalHeight - 20f
                if (popupY < 10f) {
                    popupY = 10f // avoid clipping at the top
                }
                
                accentPopupRect = RectF(startX, popupY, startX + totalWidth, popupY + totalHeight)
                
                accentOptionRects.clear()
                for ((i, opt) in options.withIndex()) {
                    val row = i / cols
                    val col = i % cols
                    val currentX = startX + col * popupItemWidth
                    val currentY = popupY + row * popupItemHeight
                    accentOptionRects.add(
                        Pair(opt, RectF(currentX, currentY, currentX + popupItemWidth, currentY + popupItemHeight))
                    )
                }
                invalidate()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(x, y)
                if (key != null && key.codes.isNotEmpty()) {
                    pressedKey = key
                    isPressedValid = true
                    handler.postDelayed(longPressRunnable, 400)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isAccentPopupVisible) {
                    hoveredAccentIndex = -1
                    for ((index, optionData) in accentOptionRects.withIndex()) {
                        val rect = optionData.second
                        // Expand touch target slightly for easier dragging
                        val touchRect = RectF(rect.left - 10, rect.top - 50, rect.right + 10, rect.bottom + 50)
                        if (touchRect.contains(x, y)) {
                            hoveredAccentIndex = index
                            break
                        }
                    }
                    invalidate()
                } else if (isPressedValid) {
                    // Check if finger moved too far from the key
                    val key = pressedKey
                    if (key != null) {
                        val rect = RectF(key.x, key.y, key.x + key.width, key.y + key.height)
                        // If moved significantly out of bounds, cancel long press
                        if (!rect.contains(x, y)) {
                            isPressedValid = false
                            pressedKey = findKey(x, y)
                            handler.removeCallbacks(longPressRunnable)
                            invalidate()
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (isAccentPopupVisible) {
                    if (hoveredAccentIndex != -1) {
                        val option = accentOptionRects[hoveredAccentIndex].first
                        listener?.onKeyPress(option)
                    }
                    isAccentPopupVisible = false
                    hoveredAccentIndex = -1
                } else if (isPressedValid && pressedKey != null) {
                    pressedKey?.codes?.let { listener?.onKeyPress(it) }
                }
                
                pressedKey = null
                isPressedValid = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                isAccentPopupVisible = false
                pressedKey = null
                isPressedValid = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Key? {
        val kbd = keyboard ?: return null
        for (row in kbd.rows) {
            for (key in row.keys) {
                val rect = RectF(key.x, key.y, key.x + key.width, key.y + key.height)
                if (rect.contains(x, y)) {
                    return key
                }
            }
        }
        return null
    }
}
