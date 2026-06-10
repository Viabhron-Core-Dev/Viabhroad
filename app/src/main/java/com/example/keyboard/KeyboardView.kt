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
        fun onLongPressEnter()
        fun onLongPressBackspace()
    }

    var listener: KeyboardListener? = null
    private var keyboard: Keyboard? = null

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

    private val keyMarginHorizontal = 8f
    private val keyMarginVertical = 10f
    private val cornerRadius = 14f

    fun setKeyboard(kbd: Keyboard) {
        this.keyboard = kbd
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
                bgPaint.color = if (key == pressedKey) Color.LTGRAY 
                                else if (key.isFunctional) Color.parseColor("#B4BACC") 
                                else Color.WHITE
                
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Draw text
                val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText(key.label, rect.centerX(), textY, textPaint)
            }
        }
    }

    private var pressedKey: Key? = null
    private var downTime = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val key = findKey(x, y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (key != null && key.codes.isNotEmpty()) {
                    pressedKey = key
                    downTime = System.currentTimeMillis()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (key != pressedKey && key?.codes?.isNotEmpty() == true) {
                    pressedKey = key
                    invalidate()
                } else if (key == null || key?.codes?.isEmpty() == true) {
                    pressedKey = null
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressedKey != null && pressedKey == key) {
                    val duration = System.currentTimeMillis() - downTime
                    if (key?.codes == "ENTER" && duration > 500) {
                        listener?.onLongPressEnter()
                    } else if (key?.codes == "DEL" && duration > 500) {
                        listener?.onLongPressBackspace()
                    } else {
                        key?.codes?.let { listener?.onKeyPress(it) }
                    }
                }
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
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
