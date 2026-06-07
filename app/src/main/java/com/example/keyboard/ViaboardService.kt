package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.logkeeper.TheLogKeeper

class ViaboardService : InputMethodService() {
    private lateinit var lifecycleOwner: ViaboardLifecycleOwner
    private var composeView: ComposeView? = null
    private lateinit var logKeeper: TheLogKeeper

    override fun onCreate() {
        super.onCreate()
        
        // Trap all fatals and ensure they go to LogKeeper before the process dies
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val _logKeeper = TheLogKeeper.getInstance(this.applicationContext)
                _logKeeper.log("FATAL", "CRASH", throwable.stackTraceToString())
            } catch (e: Exception) {
                // Ignore if we can't log
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        lifecycleOwner = ViaboardLifecycleOwner()
        lifecycleOwner.onCreate()
        logKeeper = TheLogKeeper.getInstance(this)
        logKeeper.log("INFO", "ViaboardService", "Service Created")
    }

    override fun onCreateInputView(): View {
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        composeView = ComposeView(this).apply {
            setContent {
                ViaboardKeyboardScreen(
                    onKeyPress = { key -> handleKeyPress(key) },
                    onLongPressEnter = { triggerLogKeeper() }
                )
            }
        }
        
        frameLayout.setViewTreeLifecycleOwner(lifecycleOwner)
        frameLayout.setViewTreeViewModelStoreOwner(lifecycleOwner)
        frameLayout.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        
        frameLayout.addView(composeView)
        return frameLayout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleOwner.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.disposeComposition()
        lifecycleOwner.onDestroy()
        logKeeper.log("INFO", "ViaboardService", "Service Destroyed")
    }

    private fun handleKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return
        when (key) {
            "DEL" -> inputConnection.deleteSurroundingText(1, 0)
            "SPACE" -> inputConnection.commitText(" ", 1)
            "ENTER" -> inputConnection.commitText("\n", 1)
            else -> inputConnection.commitText(key, 1)
        }
    }

    private fun triggerLogKeeper() {
        logKeeper.log("USER_ACTION", "ViaboardService", "Triggering Log Keeper via Long-Press Enter")
        // Just demonstrating the hook. As requested: "mapping long-press Enter to triggering logs"
        // Also let's show a toast to user
        android.widget.Toast.makeText(this, "Log Keeper Triggered", android.widget.Toast.LENGTH_SHORT).show()
    }
}
