package com.example.keyboard.toolbar

import android.content.Context
import androidx.core.content.edit
import com.example.R

data class ToolbarAction(
    val id: String,
    val name: String,
    val iconResId: Int
)

object ToolbarSettingsManager {
    private const val PREFS_NAME = "ToolbarPrefs"
    private const val PREF_PINNED_KEYS = "pinned_keys"
    private const val PREF_TOOLBAR_KEYS = "toolbar_keys"
    
    val ALL_ACTIONS = listOf(
        ToolbarAction("VOICE_INPUT", "Voice input", R.drawable.ic_settings_advanced), // Placeholder
        ToolbarAction("CLIPBOARD", "Clipboard", R.drawable.ic_clipboard),
        ToolbarAction("NUMPAD", "Numpad", R.drawable.ic_settings_advanced), // Placeholder
        ToolbarAction("UNDO", "Undo", R.drawable.ic_undo),
        ToolbarAction("REDO", "Redo", R.drawable.ic_redo),
        ToolbarAction("SETTINGS", "Settings", R.drawable.ic_settings),
        ToolbarAction("SELECT_ALL", "Select all", R.drawable.ic_select_all),
        ToolbarAction("SELECT_WORD", "Select word", R.drawable.ic_select),
        ToolbarAction("COPY", "Copy", R.drawable.ic_copy),
        ToolbarAction("CUT", "Cut", R.drawable.ic_cut),
        ToolbarAction("PASTE", "Paste", R.drawable.ic_clipboard), // Placeholder
        ToolbarAction("ENTER", "Enter", R.drawable.ic_enter),
        ToolbarAction("ONE_HANDED", "One-handed mode", R.drawable.ic_settings_gesture), // Placeholder
        ToolbarAction("SPLIT", "Split keyboard", R.drawable.ic_settings_appearance), // Placeholder
        ToolbarAction("INCOGNITO", "Force incognito mode", R.drawable.ic_incognito_on),
        ToolbarAction("EMOJI", "Emoji", R.drawable.ic_emoji_emoticons),
        ToolbarAction("LEFT", "Left", R.drawable.ic_arrow_left),
        ToolbarAction("RIGHT", "Right", R.drawable.ic_chevron_right), // Use chevron_right
        ToolbarAction("UP", "Up", R.drawable.ic_arrow_horizontal), // Placeholder
        ToolbarAction("DOWN", "Down", R.drawable.ic_arrow_horizontal), // Placeholder
        ToolbarAction("CLEAR_CLIP", "Clear clipboard", R.drawable.ic_bin)
    )
    
    // Default Pinned
    private val DEFAULT_PINNED = listOf("ENTER", "PASTE")
    // Default Expanded (the items in the toolbar popup)
    private val DEFAULT_TOOLBAR = listOf("VOICE_INPUT", "CLIPBOARD", "NUMPAD", "UNDO", "REDO", "SETTINGS", "SELECT_ALL", "SELECT_WORD", "COPY", "CUT", "PASTE", "ONE_HANDED")

    fun getPinnedKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_PINNED_KEYS, null)
        return saved?.split(",")?.filter { it.isNotEmpty() } ?: DEFAULT_PINNED
    }

    fun savePinnedKeys(context: Context, keys: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_PINNED_KEYS, keys.joinToString(",")) }
    }

    fun getToolbarKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_TOOLBAR_KEYS, null)
        return saved?.split(",")?.filter { it.isNotEmpty() } ?: DEFAULT_TOOLBAR
    }

    fun saveToolbarKeys(context: Context, keys: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_TOOLBAR_KEYS, keys.joinToString(",")) }
    }
}
