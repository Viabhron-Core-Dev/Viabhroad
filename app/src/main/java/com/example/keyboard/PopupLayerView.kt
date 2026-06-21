package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class PopupLayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onDrawCallback: ((Canvas) -> Unit)? = null

    init {
        // Required for drawing to work in some ViewGroup contexts, though this is a View
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        onDrawCallback?.invoke(canvas)
    }
}
