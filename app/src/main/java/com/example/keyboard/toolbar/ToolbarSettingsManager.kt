package com.example.keyboard.toolbar

import android.content.Context
import androidx.core.content.edit

data class ToolbarAction(
    val id: String,
    val name: String,
    // Using string IDs to lookup Icons later, standard icons can map via a utility later
)

object ToolbarSettingsManager {
    private const val PREFS_NAME = "ToolbarPrefs"
    private const val PREF_PINNED_KEYS = "pinned_keys"
    private const val PREF_TOOLBAR_KEYS = "toolbar_keys"
    
    val ALL_ACTIONS = listOf(
        ToolbarAction("VOICE_INPUT", "Voice input"),
        ToolbarAction("CLIPBOARD", "Clipboard"),
        ToolbarAction("NUMPAD", "Numpad"),
        ToolbarAction("UNDO", "Undo"),
        ToolbarAction("REDO", "Redo"),
        ToolbarAction("SETTINGS", "Settings"),
        ToolbarAction("SELECT_ALL", "Select all"),
        ToolbarAction("SELECT_WORD", "Select word"),
        ToolbarAction("COPY", "Copy"),
        ToolbarAction("CUT", "Cut"),
        ToolbarAction("PASTE", "Paste"),
        ToolbarAction("ENTER", "Enter"),
        ToolbarAction("ONE_HANDED", "One-handed mode"),
        ToolbarAction("SPLIT", "Split keyboard"),
        ToolbarAction("INCOGNITO", "Force incognito mode"),
        ToolbarAction("EMOJI", "Emoji"),
        ToolbarAction("LEFT", "Left"),
        ToolbarAction("RIGHT", "Right"),
        ToolbarAction("UP", "Up"),
        ToolbarAction("DOWN", "Down"),
        ToolbarAction("CLEAR_CLIP", "Clear clipboard")
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
