package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.R

class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface KeyboardListener {
        fun onKeyPress(key: String)
        fun onLongPressKey(key: String, keyRect: RectF, keyboardView: View)
        fun onLongPressEnter()
        fun onLongPressBackspace()
        fun onSwipeCursor(dx: Int)
        fun onSwipeDelete(deleteCount: Int)
    }

    var listener: KeyboardListener? = null
    fun getKeyboard(): Keyboard? = keyboard
    private var keyboard: Keyboard? = null
    private var isDesktopLayout = false

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var isAccentPopupVisible = false
    private var accentOptions = emptyList<String>()
    private var accentOptionRects = mutableListOf<Pair<String, RectF>>()
    private var hoveredAccentIndex = -1
    private var accentPopupRect = RectF()

    private var accentPopupWindow: AccentPopupWindow? = null

    private inner class AccentPopupView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isAccentPopupVisible) return
            
            val dx = -accentPopupRect.left
            val dy = -accentPopupRect.top

            val localRect = RectF(0f, 0f, accentPopupRect.width(), accentPopupRect.height())
            canvas.drawRoundRect(localRect, cornerRadius, cornerRadius, accentPopupShadowPaint)
            canvas.drawRoundRect(localRect, cornerRadius, cornerRadius, accentPopupPaint)

            for ((index, optionData) in accentOptionRects.withIndex()) {
                val (option, rect) = optionData
                val localOptionRect = RectF(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)

                if (index == hoveredAccentIndex) {
                    canvas.drawRoundRect(localOptionRect, cornerRadius, cornerRadius, accentHoverPaint)
                }

                val textY = localOptionRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                
                val originalTextSize = textPaint.textSize
                val label = getLabelForAccentCode(option)
                var textSize = 32f // use absolute pixels to match textPaint original setup
                textPaint.textSize = textSize
                val maxTextWidth = localOptionRect.width() - 8f
                while (textPaint.measureText(label) > maxTextWidth && textSize > 10f) {
                    textSize -= 1f
                    textPaint.textSize = textSize
                }
                
                canvas.drawText(label, localOptionRect.centerX(), textY, textPaint)
                textPaint.textSize = originalTextSize
            }
        }
    }

    private inner class AccentPopupWindow(context: Context) : android.widget.PopupWindow(context) {
        val view = AccentPopupView(context)
        init {
            contentView = view
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            isClippingEnabled = false
            setBackgroundDrawable(null)
            elevation = 10f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                windowLayoutType = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
        }
        
        fun showForOptions() {
            width = Math.ceil(accentPopupRect.width().toDouble()).toInt() + 10
            height = Math.ceil(accentPopupRect.height().toDouble()).toInt() + 10
            
            val location = IntArray(2)
            getLocationOnScreen(location)
            var winX = location[0] + accentPopupRect.left.toInt()
            var winY = location[1] + accentPopupRect.top.toInt()
            
            view.invalidate()
            if (!isShowing) {
                showAtLocation(this@KeyboardView, android.view.Gravity.NO_GRAVITY, winX, winY)
            } else {
                update(winX, winY, width, height)
            }
        }
    }

    private var desktopSelectMode = false

    fun setDesktopSelectMode(active: Boolean) {
        desktopSelectMode = active
        invalidate()
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D0D3D8")
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1E21")
        textAlign = Paint.Align.CENTER
        textSize = 54f
        typeface = android.graphics.Typeface.SANS_SERIF
    }

    private val accentPopupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    
    private val accentPopupShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    private val accentHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#DCDFE3")
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6A7179")
        textAlign = Paint.Align.RIGHT
        textSize = 16f
        typeface = android.graphics.Typeface.DEFAULT
    }



    private val keyMarginHorizontal = 5f
    private val keyMarginVertical = 6f
    private val cornerRadius = 14f

    fun setKeyboard(kbd: Keyboard) {
        this.keyboard = kbd
        this.isDesktopLayout = kbd.rows.any { row -> row.keys.any { it.codes == "DSK_ESC" } }
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

    override fun invalidate() {
        super.invalidate()
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
                val shadowRect = RectF(rect.left, rect.top, rect.right, rect.bottom + 3f)
                canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
                
                // Draw Key Background
                val isPressed = isKeyPressed(key)
                bgPaint.color = if (isPressed && !isAccentPopupVisible) Color.parseColor("#D0D3D8")
                                else if (key.codes == "DSK_SEL" && desktopSelectMode) Color.parseColor("#4285F4")
                                else if (key.isFunctional) Color.parseColor("#DDE1E5") 
                                else Color.WHITE
                
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Draw icon or text
                val drawableRes = when (key.codes) {
                    "SHIFT" -> R.drawable.ic_shift
                    "DEL" -> R.drawable.ic_backspace
                    "ENTER" -> R.drawable.ic_enter
                    else -> null
                }
                
                if (drawableRes != null) {
                    val drawable = context.getDrawable(drawableRes)
                    if (drawable != null) {
                        val iconWidth = drawable.intrinsicWidth * 1.5f
                        val iconHeight = drawable.intrinsicHeight * 1.5f
                        val left = rect.centerX() - iconWidth / 2
                        val top = rect.centerY() - iconHeight / 2
                        drawable.setBounds(left.toInt(), top.toInt(), (left + iconWidth).toInt(), (top + iconHeight).toInt())
                        drawable.draw(canvas)
                    }
                } else {
                    val originalSize = textPaint.textSize
                    if (!isDesktopLayout && key.label.length > 1) {
                        textPaint.textSize = originalSize * 0.65f
                    }
                    if (isDesktopLayout && textPaint.measureText(key.label) > rect.width() - 8f && key.label.contains(" ")) {
                        val spaceIndex = key.label.indexOf(" ")
                        val line1 = key.label.substring(0, spaceIndex)
                        val line2 = key.label.substring(spaceIndex + 1)
                        val textHeight = textPaint.descent() - textPaint.ascent()
                        val textY1 = rect.centerY() - textHeight / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)
                        val textY2 = rect.centerY() + textHeight / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)
                        canvas.drawText(line1, rect.centerX(), textY1, textPaint)
                        canvas.drawText(line2, rect.centerX(), textY2, textPaint)
                    } else if (isDesktopLayout && textPaint.measureText(key.label) > rect.width() - 8f) {
                        // Force split in half if no space
                        val mid = key.label.length / 2
                        val line1 = key.label.substring(0, mid)
                        val line2 = key.label.substring(mid)
                        val textHeight = textPaint.descent() - textPaint.ascent()
                        val textY1 = rect.centerY() - textHeight / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)
                        val textY2 = rect.centerY() + textHeight / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)
                        canvas.drawText(line1, rect.centerX(), textY1, textPaint)
                        canvas.drawText(line2, rect.centerX(), textY2, textPaint)
                    } else {
                        val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                        canvas.drawText(key.label, rect.centerX(), textY, textPaint)
                    }
                    textPaint.textSize = originalSize
                }
                
                key.sublabel?.let {
                    val originalAlign = hintPaint.textAlign
                    val originalSize = hintPaint.textSize
                    hintPaint.textAlign = Paint.Align.CENTER
                    var currentSize = 14f * (resources.configuration.fontScale * resources.displayMetrics.density)
                    hintPaint.textSize = currentSize
                    val maxWidth = rect.width() - 8f
                    var textToDraw = it
                    if (isDesktopLayout) {
                        if (hintPaint.measureText(it) > maxWidth) {
                            textToDraw = android.text.TextUtils.ellipsize(it, android.text.TextPaint(hintPaint), maxWidth, android.text.TextUtils.TruncateAt.END).toString()
                        }
                    } else {
                        while (hintPaint.measureText(it) > maxWidth && currentSize > 8f) {
                            currentSize -= 1f
                            hintPaint.textSize = currentSize
                        }
                    }
                    var hintY = rect.bottom - 10f
                    if (hintY + hintPaint.descent() > rect.bottom) {
                        hintY = rect.bottom - hintPaint.descent() - 2f
                    }
                    canvas.drawText(textToDraw, rect.centerX(), hintY, hintPaint)
                    hintPaint.textAlign = originalAlign
                    hintPaint.textSize = originalSize
                }

                key.sublabel2?.let {
                    val originalAlign = hintPaint.textAlign
                    val originalSize = hintPaint.textSize
                    hintPaint.textAlign = Paint.Align.RIGHT
                    var currentSize = 14f * (resources.configuration.fontScale * resources.displayMetrics.density)
                    hintPaint.textSize = currentSize
                    val maxWidth = rect.width() / 2f - 4f
                    var textToDraw = it
                    if (isDesktopLayout) {
                        if (hintPaint.measureText(it) > maxWidth) {
                            textToDraw = android.text.TextUtils.ellipsize(it, android.text.TextPaint(hintPaint), maxWidth, android.text.TextUtils.TruncateAt.END).toString()
                        }
                    } else {
                        while (hintPaint.measureText(it) > maxWidth && currentSize > 8f) {
                            currentSize -= 1f
                            hintPaint.textSize = currentSize
                        }
                    }
                    val hintX = rect.right - 8f
                    var hintY = rect.top + 22f
                    if (hintY + hintPaint.ascent() < rect.top) {
                        hintY = rect.top - hintPaint.ascent() + 2f
                    }
                    canvas.drawText(textToDraw, hintX, hintY, hintPaint)
                    hintPaint.textAlign = originalAlign
                    hintPaint.textSize = originalSize
                }

                if (key.longPress != null) {
                    val triangleSize = 8f
                    val path = android.graphics.Path()
                    path.moveTo(rect.right - 6f, rect.bottom - 6f)
                    path.lineTo(rect.right - 6f - triangleSize, rect.bottom - 6f)
                    path.lineTo(rect.right - 6f, rect.bottom - 6f - triangleSize)
                    path.close()
                    canvas.drawPath(path, hintPaint)
                }
                
                // Draw hint
                if (key.codes.length == 1 && key.label.length == 1 && key.sublabel == null && key.sublabel2 == null) {
                    val hints = getAccentsForKey(key.codes)
                    if (hints.isNotEmpty()) {
                        val hintChar = hints[0]
                        if (hintChar.length == 1) {
                            val hintX = rect.right - 10f
                            val hintY = rect.top + 24f
                            canvas.drawText(hintChar, hintX, hintY, hintPaint)
                        }
                    }
                }
            }
        }

        // Now handled in PopupLayerView
    }



    inner class PointerTracker(val pointerId: Int) {
        var pressedKey: Key? = null
        var isPressedValid = false
        var isSpaceScrubbing = false
        var isDeleteScrubbing = false
        var swipeStartX = 0f
        var lastScrubCursorDiff = 0
        
        private val longPressRunnable = Runnable { triggerLongPress(this) }
        
        fun onDown(x: Float, y: Float) {
            val key = findKey(x, y)
            if (key != null && key.codes.isNotEmpty()) {
                pressedKey = key
                isPressedValid = true
                swipeStartX = x
                lastScrubCursorDiff = 0
                isSpaceScrubbing = false
                isDeleteScrubbing = false
                handler.postDelayed(longPressRunnable, 400)
                invalidate()
            }
        }
        
        fun onMove(x: Float, y: Float) {
            if (isAccentPopupVisible && activePopupTracker == this) {
                var prevHoverIndex = hoveredAccentIndex
                hoveredAccentIndex = -1
                for ((index, optionData) in accentOptionRects.withIndex()) {
                    val rect = optionData.second
                    val touchRect = RectF(rect.left - 10, rect.top - 50, rect.right + 10, rect.bottom + 50)
                    if (touchRect.contains(x, y)) {
                        hoveredAccentIndex = index
                        break
                    }
                }
                if (prevHoverIndex != hoveredAccentIndex) {
                    this@KeyboardView.invalidate()
                    accentPopupWindow?.view?.invalidate()
                }
                return
            }
            if (isPressedValid) {
                val key = pressedKey
                if (key != null && key.codes == "SPACE") {
                    val dx = x - swipeStartX
                    val diff = (dx / scrubThreshold).toInt()
                    if (kotlin.math.abs(dx) > scrubThreshold) {
                        if (!isSpaceScrubbing) {
                            isSpaceScrubbing = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        if (diff != lastScrubCursorDiff) {
                            val moveDiff = diff - lastScrubCursorDiff
                            listener?.onSwipeCursor(moveDiff)
                            lastScrubCursorDiff = diff
                        }
                        return
                    }
                } else if (key != null && key.codes == "DEL") {
                    val dx = x - swipeStartX
                    // Swipe left to delete
                    if (dx < -scrubThreshold) {
                        if (!isDeleteScrubbing) {
                            isDeleteScrubbing = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        // For every 30px moved left, delete 1 word or char (let's say 1 step)
                        val diff = (-dx / scrubThreshold).toInt()
                        if (diff > lastScrubCursorDiff) {
                            val moveDiff = diff - lastScrubCursorDiff
                            listener?.onSwipeDelete(moveDiff)
                            lastScrubCursorDiff = diff
                        }
                        return
                    }
                }

                if (!isSpaceScrubbing && !isDeleteScrubbing && key != null) {
                    val rect = RectF(key.x, key.y, key.x + key.width, key.y + key.height)
                    if (!rect.contains(x, y)) {
                        handler.removeCallbacks(longPressRunnable)
                        val newKey = findKey(x, y)
                        if (newKey != null && newKey.codes.isNotEmpty()) {
                            pressedKey = newKey
                            isPressedValid = true
                            handler.postDelayed(longPressRunnable, 400)
                        } else {
                            pressedKey = null
                            isPressedValid = false
                        }
                        invalidate()
                    }
                }
            }
        }
        
        private fun dismissAccentPopup() {
            isAccentPopupVisible = false
            hoveredAccentIndex = -1
            activePopupTracker = null
            accentPopupWindow?.dismiss()
            this@KeyboardView.invalidate()
        }

        fun onUp() {
            handler.removeCallbacks(longPressRunnable)
            if (isAccentPopupVisible && activePopupTracker == this) {
                if (hoveredAccentIndex != -1 && hoveredAccentIndex < accentOptionRects.size) {
                    val option = accentOptionRects[hoveredAccentIndex].first
                    listener?.onKeyPress(option)
                }
                dismissAccentPopup()
            } else if (isPressedValid && pressedKey != null) {
                if (!isSpaceScrubbing && !isDeleteScrubbing) {
                    pressedKey?.codes?.let { listener?.onKeyPress(it) }
                }
            }
            pressedKey = null
            isPressedValid = false
            isSpaceScrubbing = false
            isDeleteScrubbing = false
            invalidate()
        }
        
        fun onCancel() {
            handler.removeCallbacks(longPressRunnable)
            if (activePopupTracker == this) {
                dismissAccentPopup()
            }
            pressedKey = null
            isPressedValid = false
            isSpaceScrubbing = false
            isDeleteScrubbing = false
            invalidate()
        }
    }

    private val activePointers = android.util.SparseArray<PointerTracker>()
    private var activePopupTracker: PointerTracker? = null
    private val scrubThreshold = 30f

    private fun getTracker(id: Int): PointerTracker {
        var tracker = activePointers.get(id)
        if (tracker == null) {
            tracker = PointerTracker(id)
            activePointers.put(id, tracker)
        }
        return tracker
    }

    private fun isKeyPressed(key: Key): Boolean {
        for (i in 0 until activePointers.size()) {
            val tracker = activePointers.valueAt(i)
            if (tracker.pressedKey == key && tracker.isPressedValid) return true
        }
        return false
    }

    private fun getLabelForAccentCode(code: String): String {
        return when (code) {
            "MODE_NUMPAD" -> "1234"
            "MODE_EMOJI" -> "🙂"
            "MODE_NAVIGATION" -> "⇦"
            "MODE_DESKTOP" -> "PC"
            "MODE_SYMBOLS" -> "?123"
            "MODE_SYMBOLS_SHIFT" -> "=\\<"
            "SETTINGS" -> "⚙️"
            "ONE_HAND" -> "🗗"
            "CLIPBOARD" -> "📋"
            "DSK_PGUP" -> "PgUp"
            "DSK_PGDN" -> "PgDn"
            "DSK_DELLINE" -> "DelLine"
            "DSK_SAVE" -> "Save"
            "DSK_SELALL" -> "SelAll"
            else -> code
        }
    }

    private fun getAccentsForKey(key: String): List<String> {
        return when (key.lowercase()) {
            "1" -> listOf("¹", "½", "⅓", "¼", "⅛")
            "2" -> listOf("²", "⅔")
            "3" -> listOf("³", "¾", "⅜")
            "4" -> listOf("⁴")
            "5" -> listOf("⁵", "⅝")
            "6" -> listOf("⁶")
            "7" -> listOf("⁷", "⅞")
            "8" -> listOf("⁸")
            "9" -> listOf("⁹")
            "0" -> listOf("⁰", "ⁿ", "∅")
            "q" -> listOf("%")
            "w" -> listOf("\\")
            "e" -> listOf("|", "é", "è", "ê")
            "r" -> listOf("=")
            "t" -> listOf("[")
            "y" -> listOf("]")
            "u" -> listOf("<", "ú", "ù", "û")
            "i" -> listOf(">", "í", "ì", "î")
            "o" -> listOf("{", "ó", "ò", "ô")
            "p" -> listOf("}")
            "a" -> listOf("@", "á", "à", "â")
            "s" -> listOf("#", "ß", "ś", "š")
            "d" -> listOf("₹", "$", "¢", "€", "£", "¥")
            "f" -> listOf("_")
            "g" -> listOf("&")
            "h" -> listOf("-")
            "j" -> listOf("+")
            "k" -> listOf("(")
            "l" -> listOf(")")
            "z" -> listOf("*")
            "x" -> listOf("\"")
            "c" -> listOf("'", "ç", "ć", "č")
            "v" -> listOf(":")
            "b" -> listOf(";")
            "n" -> listOf("!", "ñ", "ń", "ň")
            "m" -> listOf("?")
            "!" -> listOf("¡")
            "?" -> listOf("¿")
            "mode_symbols" -> listOf("MODE_NUMPAD", "MODE_EMOJI", "MODE_NAVIGATION", "MODE_SYMBOLS_SHIFT", "MODE_DESKTOP")
            "+" -> listOf("(")
            "-" -> listOf(")")
            "*" -> listOf("/")
            "=" -> listOf("#")
            "%" -> listOf("₹", "$", "¢", "€", "£", "¥")
            ":" -> listOf(";")
            "_" -> listOf("|")
            "." -> listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", ",", "?", "]", "[")
            "," -> listOf("MODE_EMOJI", "SETTINGS", "CLIPBOARD")
            else -> emptyList()
        }
    }

    private fun triggerLongPress(tracker: PointerTracker) {
        val key = tracker.pressedKey ?: return
        if (!tracker.isPressedValid) return

        if (key.codes == "ENTER") {
            listener?.onLongPressEnter()
            tracker.pressedKey = null
            tracker.isPressedValid = false
            invalidate()
        } else if (key.codes == "DEL") {
            listener?.onLongPressBackspace()
            tracker.pressedKey = null
            tracker.isPressedValid = false
            invalidate()
        } else if (key.codes == "SHIFT") {
            listener?.onLongPressKey("SHIFT", RectF(key.x, key.y, key.x + key.width, key.y + key.height), this)
            tracker.pressedKey = null
            tracker.isPressedValid = false
            invalidate()
        } else {
            if (key.longPress != null && key.longPress!!.startsWith("DSK_")) {
                listener?.onKeyPress(key.longPress!!)
                tracker.pressedKey = null
                tracker.isPressedValid = false
                invalidate()
                return
            }

            val options = if (key.longPress != null) {
                if (key.longPress!!.startsWith("ACCENT_")) {
                    getAccentsForKey(key.codes)
                } else {
                    listOf(key.longPress!!)
                }
            } else {
                getAccentsForKey(key.codes)
            }
            if (options.isNotEmpty()) {
                if (options.size == 1 && key.longPress == null) {
                    listener?.onKeyPress(options.first())
                    tracker.pressedKey = null
                    tracker.isPressedValid = false
                    invalidate()
                    return
                }
                
                isAccentPopupVisible = true
                activePopupTracker = tracker
                accentOptions = options
                hoveredAccentIndex = -1
                
                val maxCols = 6
                val cols = if (options.size > maxCols) maxCols else options.size
                val rows = (options.size + cols - 1) / cols
                
                val defaultKeyWidth = width / 10f
                val popupItemWidth = defaultKeyWidth * 0.9f
                val popupItemHeight = key.height * 0.95f
                
                val totalWidth = popupItemWidth * cols
                val totalHeight = popupItemHeight * rows
                
                var startX = key.x + (key.width / 2) - (totalWidth / 2)
                if (startX < 0f) startX = 5f
                if (startX + totalWidth > width) startX = width - totalWidth - 5f
                
                var popupY = key.y - totalHeight - 40f
                
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

                if (accentPopupWindow == null) {
                    accentPopupWindow = AccentPopupWindow(context)
                }
                accentPopupWindow?.showForOptions()
                invalidate()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                getTracker(pointerId).onDown(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    getTracker(id).onMove(x, y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                getTracker(pointerId).onUp()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until activePointers.size()) {
                    activePointers.valueAt(i).onCancel()
                }
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
