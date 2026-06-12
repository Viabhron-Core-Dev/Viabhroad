# Viaboard: Sovereign Android Keyboard — Master Blueprint
**Version**: 3.1
**Status**: Initial Setup Phase

## 1. Core Philosophy
Viaboard is a **privacy-first, local-only Android keyboard**. It is keyboard-first. Every other feature is secondary to being a great keyboard.
- **Local-First**: All data lives in SQLite locally. No cloud. No sync.
- **Privacy by Design**: No internet permission. Incognito auto-activates on sensitive fields.
- **No Heavy AI**: SmolLM is removed. Focus is on pure utility and speed.

## 2. The Log Keeper Standard (Global)
A strict logging system active from Phase 1. 
- **Trigger**: Accessible via a FAB across the Welcome App UI, and temporarily mapped to **long-press Enter/Next line** on the keyboard.
- **Timeframes**: Tap provides options to view logs from the last 1, 6, 12, 24 hours, or chronological rest.
- **Actions**: Tap an option to Download or Copy to clipboard.
- **Master Switch**: Contains a Master On/Off switch.
- **Constraints**: Logs ONLY Error types/codes, failed components, timestamps, code-path stack traces, and crash states. **Never** records visual content, typed sentences, credentials, or PII. Inherently blocks passwords and personal vault data.

## 3. Base & Approach
- **Architecture**: The "Goldilocks" Approach. Traditional View-based `InputMethodService` (Jetpack Compose removed).
- **UI Layers**: Two distinct layers:
  - **Toolbar**: Standard XML layouts on top for ease of building the suggestion bar, settings, and UI components.
  - **Key Grid**: Lightweight, custom-drawn Canvas underneath for extreme rendering performance where it matters most (the keys).
- **Engine**: Proven mathematical logic, touch handling, and XML/JSON layout parsing adapted from established open-source apps (HeliBoard reference) to ensure it feels naturally responsive to type on.

## 4. Core Features
- **Dictionaries & Autocorrect**: Pure Kotlin word list + personal learned SQLite dictionary. N-gram prediction.
- **Double Clipboard**: Regular rolling clipboard + Pinned clipboard. Max 2 image clips.
- **Privacy Vault**: Private info (emails, addresses) masked in suggestion bar. Pure Kotlin crypto.
- **Security Vault**: Integration with **KeePassDX** (pure Kotlin) for passwords. Hard-blocked from all data sync.
- **Coding Mode**: Full keyboard layout shift. JSON defined. Suppresses English autocorrect.

## 5. Build Roadmap (Granular Phasing for Stability)

*Each phase strictly requires full stability, testing, and UI verification before proceeding to the next. No combined jumps.*

### Phase 1: Project Skeleton & Telemetry
- [x] 1. Setup Android app structure and Welcome Page UI.
- [x] 2. Build "The Log Keeper" (DB, Master Switch, FAB UI on Welcome Page for timeframes/download/copy).
- [x] 3. Configure GitHub Actions workflow for automatic remote APK building (Release build removed to ensure workflow stability for Debug APK drops).

### Phase 2: The Core Goldilocks Engine (Immediate Priority)
*Building the foundational layout parser and canvas math before any other feature.*
- [x] 1. Rip out Compose and set up the traditional `InputMethodService` (XML Toolbar + Canvas Grid layout).
- [x] 2. **Custom Layout Parser**: Build the engine to parse specialized keyboard XML files (QWERTY, symbols) into dynamic grid coordinates.
- [x] 3. **Canvas Touch Math**: Implement the HeliBoard-inspired math for touch hitboxes, multi-touch handling, and high-performance Canvas rendering.
- [x] 4. Connect the new parser to the `KeyboardView`, verifying fluid, baseline QWERTY typing.
- [x] 5. Hook Log Keeper into the Keyboard service.
- [x] 6. **Visual & Layout Baseline**: Sync colors, keyboard proportions, drop shadows, and add the persistent Number Row according to the HeliBoard reference UI.
- [x] 7. **Navigation Bar Insets**: Implement `fitsSystemWindows="true"` on the root view and `onComputeInsets` in InputMethodService to correctly apply bottom padding and system top insets.
- [x] 8. **Size Calibration**: Shrink the total layout height (calibrated to 260dp) to match the real-world scale of HeliBoard on mobile screens, and add a test TextField mapping to the setup page.

### Phase 3: Basic UX Tweaks & Suggestion UI
- [x] 1. Build the basic Suggestion Bar Row / Toolbar UI above the keyboard (HeliBoard styling) with chevron expand/collapse toggles.
- [x] 2. Implement "Backspace erase all behind cursor" (long-press).
- [x] 3. Implement "Select All" (Ctrl+A mimic) and "Paste" functionality in the expanded toolbar.
- [x] 4. Fix Backspace/DEL to properly delete selected text (Select All + Delete fix).

### Phase 4: Vocabulary SQLite & Autocorrect Foundation
- [x] 1. Set up the main SQLite `Divided Library`.
- [x] 2. Integrate static English word list (pure Kotlin).
- [x] 3. Implement n-gram next-word prediction logic in the suggestion bar.

### Phase 5: Learning & Personalization
- [x] 1. Implement dynamic word learning (upsert into `words` and `bigrams` table).
- [x] 2. Auto-capitalize the first letter of sentences.
- [x] 3. Handle long-press UI adjustments (symbols, accents preview).

### Phase 6: Incognito & Context Handling
- [x] 1. Monitor `EditorInfo` flags to auto-detect passwords and sensitive fields.
- [x] 2. Build Incognito Mode (manual Toggle + auto-trigger).
- [x] 3. Wire Incognito to instantly disable dictionary learning and clipboard history.

### Phase 7: Core Keyboard Features
1. **Capitalization & Shift States**: Shift button to toggle between lowercase, uppercase (Shift), and Caps Lock (Long press).
2. **Numbers & Symbols Modal**: Shift key toggles to number and symbol modifier layouts.
3. **Key Popups**: Visual popups for symbols and alternative characters on long press (e.g., holding 'a' for 'á').
4. **Emoji Support**: Emoji keyboard layout, selection, and integration.
5. **Multi-language Support & Dictionaries**: Support for loading and managing dictionary files per language (similar to HeliBoard).
6. **Advanced Prediction**: Next word prediction refinement, support for quick phrases, and adding entries to personal dictionary.
7. **Toolbar & Settings Customization**: Main layout settings page. Ability to pin and manage standard and advanced toolbar keys (Voice input, Clipboard history, Numpad, Undo/Redo, Settings, Select tools, Copy/Cut/Paste, One-handed mode, Auto-correction, Emoji, Navigation keys, etc.).

### Phase 8: The Double Clipboard
1. Regular Rolling Clipboard UI Modal & SQLite logic.
2. Pinned Clipboard UI Modal & pinning behavior (long-press move).
3. Image clip support (max 2 images limit) within regular clipboard.

### Phase 9: Privacy Vault
1. Build Pure Kotlin encrypted key-value store database module.
2. Implement suggestion bar masking logic for partial matches.
3. Implement biometric/PIN gate to unlock and insert full text.

### Phase 10: Security Vault (KeePassDX)
1. Integrate the `KeePassDX` pure Kotlin library.
2. Provide isolated access UI (Modal with folder/entry select).
3. Ensure strictly sandboxed architecture (no crossover to main SQLite).

### Phase 11: Dynamic Layouts & Coding Mode
1. Hook up the JSON layout parser (for dynamic keys).
2. Build full Coding Mode toggle (shifts UI, distinct key mappings).
3. Build logic to suppress generic English autocorrect when coding, loading custom tech-dictionary JSONs.

### Phase 12: JS Sandbox Extensibility
1. Integrate QuickJS engine.
2. Build utility toolbar UI mapping.
3. Add base scripts (calc, base64, hash, formatters) with 1-second timeout limits.

### Phase 13: Data Modals & Sync
1. Build separate Modals (Notes, tasks, expenses, reminders) interacting with `Divided Library`.
2. Construct shared storage JSON export/sync for PWA desktop mirroring.

### Phase 14: Whisper Voice Integration
1. Integrate ~50MB Whisper on-device model.
2. Trigger voice-to-text via dedicated long-press (remapping the Log Keeper trigger used in early phases back to its final state).
