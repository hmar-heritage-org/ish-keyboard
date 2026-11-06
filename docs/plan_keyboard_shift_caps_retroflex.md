# Implementation Plan: Free-Form Shift, Punctuation Fix & Retroflex Toggle

**Target Release**: `v1.3.0`  
**Components**: `android-host.js`, `hmar_ish.kmn`, `hmar_ish.keyman-touch-layout`, `ISHInputMethodService.java`, `PreferencesActivity.java`, `fragment_settings.xml`

---

## 1. Executive Summary of Investigated Issues

### Issue A: Shift & Caps Lock Hijacking Mid-Word Capitals (`OpenAI`)
- **Problem**: When a word is active before the cursor (`curToken`), tapping Shift intercepts the keystroke and forces a whole-word casing cycle (`lowercase` ➔ `TitleCase` ➔ `ALL CAPS`).
- **Impact**: `TitleCase` forces char 0 uppercase and *every subsequent char lowercase* (`toTitleCase`). Typing mid-word capitals like `OpenAI`, `iPhone`, `eBay`, `YouTube`, or `camelCase` is completely blocked.
- **Solution**: Shift must be **Free-Form**. Single tap shifts the keyboard for the **next single character** without altering preceding text, then auto-reverts to lowercase. Double tap activates **Caps Lock**.

### Issue B: Comma and Period Outputting `<` and `>` in Caps Mode
- **Problem**: Keyman maps `Shift + K_COMMA` $\rightarrow$ `<` and `Shift + K_PERIOD` $\rightarrow$ `>`. In Caps Lock or Shift, typing a period produces `>` (`HELLO>` instead of `HELLO.`).
- **Solution**: Explicitly lock `,` and `.` to output literal `,` and `.` across `default`, `shift`, and Caps Lock layers. `<` and `>` remain exclusively on the Symbols layer (`123` ➔ `=/<`) and parentheses long-press.

### Issue C: Mid-Word `tr -> ṭ` Auto-Conversion (`string`, `retribution`)
- **Problem**: When typing English loanwords (`string`, `retribution`, `destroy`, `country`, `electric`), `t` + `r` immediately mutates to `ṭ` (`sṭing`, `reṭibution`).
- **Escape Mechanism**: The keyboard supports double-r escape (`ṭ` + `r` $\rightarrow$ `tr`), meaning typing `strring` expands back to `string`. We need to ensure mid-word boundary checks in `insertText` do not block `dn = 1` replacement of `ṭ` with `tr`.
- **Case Variations in KMN**: Ensure `Ṭ + r` and `ṭ + R` also cleanly escape to `Tr` / `tr`.

### Issue D: Settings Toggle to Disable `tr -> ṭ` Auto-Conversion & Tonal `ṭ` Shortcut
- **Context**: In standard Internet Standard Hmar (ISH), `tr` and `thr` are valid first-class ASCII digraphs. Writers of pure ISH and multilingual typers should be able to disable auto-conversion so `tr` always stays `tr`.
- **The Requirement**:
  1. Add a toggle in Settings: **"Auto-convert 'tr' to 'ṭ'"** (Enabled by default).
  2. When disabled, typing `tr` types `t-r` without converting.
  3. When disabled, users need an intuitive way to type `ṭ` when desired: **Add `ṭ` to the Tonal Mode (`^` / `♪`) shortcut!**
  4. In Tonal Mode: Key `T` is highlighted (not dimmed), displays `ṭ` (or `Ṭ` on Shift), and tapping it outputs `ṭ` / `Ṭ`.

---

## 2. Technical Architecture & Changes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             KEYBOARD INPUT PIPELINE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [ Shift Key Tap ]                                                          │
│       │                                                                     │
│       ├── Double-Tap (<350ms) ──► Caps Lock ON (_ishCapsLocked = true)       │
│       │                           - Stays in uppercase until tapped again   │
│       │                           - Period/Comma stay '.' and ','           │
│       │                                                                     │
│       └── Single-Tap          ──► Free-Form Shift ON (layer = 'shift')      │
│                                   - Does NOT mutate curToken before cursor  │
│                                   - Types 1 uppercase character             │
│                                   - Auto-reverts to 'default' lowercase     │
│                                   - Enables: "Open" + Shift + "A" + ...     │
│                                                                             │
│  [ Tone Key (^) / (♪) ]                                                     │
│       │                                                                     │
│       ├── Vowel Key (A, E, I, O, U) ──► Â, Ê, Î, Ô, Û                       │
│       └── T Key (t, T)              ──► ṭ, Ṭ (Highlighted in Tonal Mode!)   │
│                                                                             │
│  [ 'tr' / 'thr' Keystrokes ]                                                │
│       │                                                                     │
│       ├── If Auto-Convert = ON  ──► 'tr' ➔ 'ṭ', 'thr' ➔ 'ṭh'                │
│       │                             - Escape: 'r' again ➔ 'tr' (strring)    │
│       └── If Auto-Convert = OFF ──► 'tr' ➔ 'tr', 'thr' ➔ 'thr'              │
│                                     - To type ṭ: use Tone Key or Long-Press │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation Plan

### Phase 1: Free-Form Shift & Caps Lock Refactor (`android-host.js`)
1. **Remove intrusive word casing mutation on Shift tap**:
   - In the Shift key touch interceptor (`[id$="-K_SHIFT"]`), remove the block that rewrites `curToken` using `applyCasing`.
   - Single tap on Shift switches `vkbd.layerId = 'shift'` without touching existing text in the editor.
   - Set flag `_ishSingleShiftActive = true`.
2. **Auto-drop Shift layer after 1 character**:
   - In `insertText(dn, s, dr)`, when a single non-space character is inserted while `_ishSingleShiftActive` is true (and `_ishCapsLocked` is false):
   - Switch `layerId` back to `'default'` and reset `_ishSingleShiftActive = false`.
3. **Double-tap Caps Lock**:
   - Retain 350ms double-tap window to toggle `_ishCapsLocked = true/false`.
   - Single tap while `_ishCapsLocked` is active releases Caps Lock and returns to `'default'`.
4. **Preserve Mixed-Case in Suggestions**:
   - Update `filterSuggestionsByCasing` so that if `curToken` contains mixed case (e.g. `OpenA`), suggestions do not get flattened by `toTitleCase()`.

### Phase 2: Comma & Period Punctuation Guard
1. **Touch Layout Key IDs**:
   - In `android/app/src/main/assets/packages/hmar_ish/hmar_ish.keyman-touch-layout` and `engine/hmar_ish.keyman-touch-layout`:
   - Change Shift layer comma key ID from `K_COMMA` to `U_002C` (Unicode comma `,`).
   - Change Shift layer period key ID from `K_PERIOD` to `U_002E` (Unicode period `.`).
   - Update subkeys in `sk` so comma and period do not invoke shifted virtual codes.
2. **Keyman Engine Rules (`engine/hmar_ish.kmn`)**:
   - Add explicit keyboard rules:
     ```
     + [SHIFT K_COMMA] > ','
     + [SHIFT K_PERIOD] > '.'
     ```
3. **`android-host.js` Fallback Interceptor**:
   - In `insertText(dn, s, dr)`:
     ```javascript
     var curLayer = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd.layerId : 'default';
     if (curLayer === 'default' || curLayer === 'shift' || _ishCapsLocked) {
       if (s === '<') s = ',';
       if (s === '>') s = '.';
     }
     ```

### Phase 3: Retroflex `tr` Mid-Word Escapes & Rules
1. **Complete Case Matrix in `engine/hmar_ish.kmn`**:
   - Ensure all combinations of `t/T` + `r/R` and their escapes exist:
     ```
     c Escape matrix
     'ṭ' + 'r' > 'tr'
     'ṭ' + 'R' > 'tr'
     'Ṭ' + 'r' > 'Tr'
     'Ṭ' + 'R' > 'TR'
     'ṭh' + 'r' > 'thr'
     'Ṭh' + 'r' > 'Thr'
     'ṬH' + 'R' > 'THR'
     ```
2. **Verify Mid-Word Boundary Guard**:
   - Verify that when `ṭ` is in the middle of a word (e.g. `sṭ` or `reṭ`), typing `r` calls `insertText(1, 'tr', 0)` and deletes exactly `ṭ` and inserts `tr` without being clamped to 0.

### Phase 4: Tonal Mode `ṭ` & `Ṭ` Shortcut
1. **Update `VOWEL_MAP` & `TONAL_MAP` in `android-host.js`**:
   - Add `K_T` to tonal mappings:
     ```javascript
     'K_T': { lower: 't', lowerTonal: 'ṭ', upper: 'T', upperTonal: 'Ṭ' }
     ```
   - Update `TONAL_VOWELS` (or rename to `TONAL_CHARS`):
     ```javascript
     var TONAL_CHARS = {
       'a': 'â', 'e': 'ê', 'i': 'î', 'o': 'ô', 'u': 'û', 't': 'ṭ',
       'A': 'Â', 'E': 'Ê', 'I': 'Î', 'O': 'Ô', 'U': 'Û', 'T': 'Ṭ'
     };
     ```
   - Remove `K_T` from `NON_TONAL_KEYS` so the `T` key is **not** dimmed in Tonal Mode.
2. **Visual Styling for Tonal `T` Key**:
   - In `syncTonalAndCapsVisuals()`:
     - When `_ishTonalActive` is true, the `T` key text displays `ṭ` (or `Ṭ` if shifted).
     - Styled with the same vibrant white / highlight as the vowel keys.
3. **Execution in `insertText`**:
   - When `_ishTonalActive` is true and `s === 't'` or `s === 'T'`:
     - Mutates `s` to `ṭ` (or `Ṭ`).
     - Resets `_ishTonalActive = false` and syncs visuals.

### Phase 5: Settings Toggle for `tr -> ṭ` Auto-Conversion
1. **Android Preferences & UI**:
   - Add preference key `pref_retroflex_auto_convert` (default: `true`) in `KMAPreferences`.
   - Add switch item to `fragment_settings.xml` and `PreferencesActivity.java`:
     - Title: **"Convert 'tr' to 'ṭ' (Retroflex)"**
     - Subtitle: *"Automatically transform 'tr' to 'ṭ' and 'thr' to 'ṭh'. When disabled, use the Tone key (^) or long-press on 't' to type 'ṭ'."*
2. **Bridge Method**:
   - Add `@JavascriptInterface public boolean isRetroflexAutoConvertEnabled()` in `ISHInputMethodService.java`.
3. **Runtime Interception in `android-host.js`**:
   - If `!window.ishBridge.isRetroflexAutoConvertEnabled()`:
     - When Keyman produces `s === 'ṭ'` with `dn === 1`, convert `s = 'tr'`.
     - When Keyman produces `s === 'Ṭ'` with `dn === 1`, convert `s = 'Tr'`.
     - When Keyman produces `s === 'ṭh'` with `dn === 2`, convert `s = 'thr'`.
     - When Keyman produces `s === 'Ṭh'` with `dn === 2`, convert `s = 'Thr'`.
     - Keyman internal context is synchronized to `tr` / `thr`.

---

## 4. Verification & Testing Matrix

| Test Scenario | Keystroke Sequence | Expected Output |
| :--- | :--- | :--- |
| **Mid-Word Capitalization** | `O` `p` `e` `n` + `Shift` + `A` + `Shift` + `I` | `OpenAI` |
| **CamelCase** | `c` `a` `m` `e` `l` + `Shift` + `C` + `a` `s` `e` | `camelCase` |
| **Caps Lock Period** | Double-tap `Shift` + `H` `E` `L` `L` `O` + `.` | `HELLO.` (never `HELLO>`) |
| **Caps Lock Comma** | Double-tap `Shift` + `A` + `,` + `B` | `A, B` (never `A< B`) |
| **Mid-Word Retroflex Escape** | `s` `t` `r` `r` `i` `n` `g` | `string` |
| **Mid-Word Retroflex Escape 2** | `r` `e` `t` `r` `r` `i` `b` `u` `t` `i` `o` `n` | `retribution` |
| **Tonal Mode `ṭ`** | `^` (Tone) + `t` | `ṭ` (Tone visual resets) |
| **Tonal Mode `Ṭ`** | `^` (Tone) + `Shift` + `T` | `Ṭ` |
| **Auto-Convert Toggle OFF** | Turn OFF toggle in Settings ➔ type `t` `r` `a` `n` | `tran` (stays pure ISH ASCII) |
| **Auto-Convert Toggle OFF + Tonal** | Turn OFF toggle in Settings ➔ `^` + `t` `a` `n` | `ṭan` |
