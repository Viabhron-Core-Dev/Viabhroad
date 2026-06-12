# Phase 7: Core Keyboard Features - Technical Blueprint

This document outlines the detailed architecture and implementation plan for Phase 7 of the Viaboard project, focusing on core keyboard functionalities required for a complete typing experience.

## 1. Capitalization & Shift States Engine
- **State Management**: Implement a `ShiftState` enum (`LOWERCASE`, `UPPERCASE`, `CAPS_LOCK`).
- **Touch Handling**:
  - **Tap on Shift**: Toggle between `LOWERCASE` and `UPPERCASE`.
  - **Double Tap / Long Press on Shift**: Activate `CAPS_LOCK`.
- **UI Updates**:
  - Update keycap labels dynamically based on the current `ShiftState`.
  - Shift key icon reflects current state (e.g., outline for lowercase, filled for uppercase, filled with underline for caps lock).
- **Auto-Capitalization**: Hook into `InputConnection` to automatically switch to `UPPERCASE` at the start of sentences or fields demanding capitalization, reverting to `LOWERCASE` after the first character is typed.

## 2. Numbers & Symbols Layouts
- **Layout Definitions**: Define secondary XML layouts for 'Numbers & Symbols' (`?123`) and 'Advanced Symbols' (`=\<`).
- **Toggle Mechanism**:
  - The `?123` key switches the active layout view within the main keyboard container.
  - Within `?123`, a toggle key (like `=\<` and `ABC`) manages switching between advanced symbols and back to the alphabet layout.
- **Data Structure**: Use view stubs, independent XML layouts swapped dynamically, or `ViewFlipper` to manage layout rendering efficiently without inflating everything upfront.

## 3. Key Popups (Long Press Interactions)
- **Popup Anchor**: Create a scalable transparent overlay or utilize Android's `PopupWindow` anchored to individual keys when long-pressed.
- **Data Mapping**: JSON or XML mapping of base keys to their long-press variants (e.g., 'a' -> ['a', 'á', 'à', 'ä', 'â', 'æ', 'ã', 'å', 'ā']).
- **Interaction Logic**:
  - `onLongPress` triggers the popup display.
  - Continue tracking drag events (`MotionEvent.ACTION_MOVE`) to highlight the currently selected variant.
  - `onRelease` (`MotionEvent.ACTION_UP`) commits the highlighted variant to the editor and dismisses the popup.

## 4. Emoji Keyboard Integration
- **Layout**: A dedicated `ViewFlipper` slide or separate overlay covering the keyboard region.
- **Categories**: Bottom or top navigation bar for emoji categories (Smileys, Animals, Food, etc.).
- **Rendering**: Group emojis into a `RecyclerView` with a `GridLayoutManager` for lazy-loading to optimize memory usage and initial load time.
- **System Compatibility**: Use Android's `EmojiCompat` library or rely on system fonts to ensure backward compatibility and rendering of newer emojis.
- **Recents**: Maintain a local SQLite/Room table or SharedPreferences array for tracking and displaying 'Recently Used' emojis at the top or in a dedicated section.

## 5. Multi-language Support & Dictionaries (HeliBoard-style)
- **Dictionary Architecture**:
  - Expand beyond simple in-memory/SQLite caching for base vocabularies.
  - Implement a loader for standard `.dict` or standardized JSON/binary lightweight dictionary files per language.
- **Language Switching**:
  - Long press on the spacebar or tap a dedicated Globe icon to switch the active language layout and dictionary.
  - Maintain a list of user-enabled languages in shared preferences.
- **Core Engine Modification**:
  - The `WordRepository` must accept a `languageCode` context.
  - Queries for next-word prediction and autocorrect must filter by the active language dictionary.
- **Personal Dictionary**: A dedicated user-editable SQLite table for custom words, overriding or augmenting the primary language dictionary during prediction.

## 6. Advanced Prediction & Quick Phrases
- **N-Gram Engine Expansion**: Enhance the current bigram engine to support trigrams (3-word context) for more accurate context-awareness.
- **Heuristic Scoring**: Implement a scoring algorithm using frequency weights and recency logic when determining the top 3 suggestions to display.
- **Quick Phrases / Snippets**:
  - Implement a text-expansion mechanism where specific abbreviations expand into longer phrases (e.g., typing "brb" suggests or auto-expands to "be right back").
  - UI in Settings to add, edit, and delete user-defined text snippets.

## 7. Toolbar Customization Engine
- **Data Model**:
  - Maintain an ordered list representing the active/pinned toolbar slots.
  - Define a comprehensive `ToolbarAction` enum representing available tools (Voice Input, Clipboard history, Numpad, Undo/Redo, Settings, Select All, Copy/Cut/Paste, One-handed mode, Force Incognito, Auto-correction toggle, Emoji, Navigation arrows).
- **UI Implementation**:
  - **Top Row**: A horizontal `RecyclerView` or dynamically re-populated `LinearLayout` that reads the active tool list instead of hardcoded `ImageButton` elements.
  - **Settings Configuration UI**: 
    - Create an activity/fragment with a list of all tools featuring toggle switches to enable/disable them as pinned keys.
    - Support reordering via drag-and-drop (`ItemTouchHelper` with `RecyclerView`).
- **Action Dispatcher**: A centralized `handleToolbarAction(action: ToolbarAction)` method in `ViaboardService` that executes the corresponding behavior, routing properly to modals, settings intents, or input connection edits.
