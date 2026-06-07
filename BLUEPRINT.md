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
- **Base Repo**: Fork `roalyr/CustomKeyboardEngine` (Pure Kotlin, no NDK, Cloud AI Studio friendly).
- **Layouts**: JSON-based keyboard layouts.
- **Visual Reference**: HeliBoard (styling, toolbar) & Futo Keyboard (suggestion bar).

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

### Phase 2: Base Engine Import & Cleanup
- [x] 1. Import `roalyr/CustomKeyboardEngine` source (Created minimalistic programmatic Compose Jetpack service core instead of pulling legacy repo).
- [x] 2. Strip out bloated or unnecessary demo components.
- [x] 3. Hook Log Keeper into the Keyboard service (temp: mapping long-press Enter to triggering logs).
- [x] 4. Verify baseline QWERTY typing works cleanly.

### Phase 3: Basic UX Tweaks & Suggestion UI
1. Build the basic Suggestion Bar Row / Toolbar UI above the keyboard (HeliBoard styling).
2. Implement "Backspace erase all behind cursor" (long-press).
3. Implement "Select All" (Ctrl+A mimic) in toolbar.

### Phase 4: Vocabulary SQLite & Autocorrect Foundation
1. Set up the main SQLite `Divided Library`.
2. Integrate static English word list (pure Kotlin).
3. Implement n-gram next-word prediction logic in the suggestion bar.

### Phase 5: Learning & Personalization
1. Implement dynamic word learning (adding to `words_phrases` table).
2. Implement priority weighting for personal words over static dictionary.
3. Build "Quick Phrases" SQLite inserts and suggestion UI.

### Phase 6: Incognito & Context Handling
1. Monitor `EditorInfo` flags to auto-detect passwords and sensitive fields.
2. Build Incognito Mode (manual Toggle + auto-trigger).
3. Wire Incognito to instantly disable dictionary learning and clipboard history.

### Phase 7: The Double Clipboard
1. Regular Rolling Clipboard UI Modal & SQLite logic.
2. Pinned Clipboard UI Modal & pinning behavior (long-press move).
3. Image clip support (max 2 images limit) within regular clipboard.

### Phase 8: Privacy Vault
1. Build Pure Kotlin encrypted key-value store database module.
2. Implement suggestion bar masking logic for partial matches.
3. Implement biometric/PIN gate to unlock and insert full text.

### Phase 9: Security Vault (KeePassDX)
1. Integrate the `KeePassDX` pure Kotlin library.
2. Provide isolated access UI (Modal with folder/entry select).
3. Ensure strictly sandboxed architecture (no crossover to main SQLite).

### Phase 10: Dynamic Layouts & Coding Mode
1. Hook up the JSON layout parser (for dynamic keys).
2. Build full Coding Mode toggle (shifts UI, distinct key mappings).
3. Build logic to suppress generic English autocorrect when coding, loading custom tech-dictionary JSONs.

### Phase 11: JS Sandbox Extensibility
1. Integrate QuickJS engine.
2. Build utility toolbar UI mapping.
3. Add base scripts (calc, base64, hash, formatters) with 1-second timeout limits.

### Phase 12: Data Modals & Sync
1. Build separate Modals (Notes, tasks, expenses, reminders) interacting with `Divided Library`.
2. Construct shared storage JSON export/sync for PWA desktop mirroring.

### Phase 13: Whisper Voice Integration
1. Integrate ~50MB Whisper on-device model.
2. Trigger voice-to-text via dedicated long-press (remapping the Log Keeper trigger used in early phases back to its final state).
