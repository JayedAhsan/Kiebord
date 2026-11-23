# Kiebord Keyboard Layout Development Guide

This guide describes how to create, test, and package custom keyboard layouts for Kiebord using the JSON layout format.

---

## 1. Overview

Kiebord supports dynamic and bundled keyboard layouts defined in JSON. Layouts can be:
- Bundled with the application under `assets/layouts/<layout_id>.json` (shipped within the APK).
- Loaded dynamically by users from the device's custom layouts folder.
- Authored and referenced in the repository under [`assets-src/layouts/`](../assets-src/layouts/).

Layouts are parsed with compile-time serializers and cached in memory to ensure zero latency during typing and language switching.

---

## 2. File Placement and Naming

- File extension: `.json`
- File naming: `<language_code>_<layout_name>.json` (example: `ar_arabic.json`, `fr_azerty.json`, `de_qwertz.json`)
- Character encoding: UTF-8 (without BOM)

---

## 3. Schema Reference

### Top-Level Properties

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique identifier for the layout (e.g., `ar_arabic`, `en_qwerty`, `bn_khipro`). |
| `name` | String | Yes | Name shown in Settings (e.g., `Arabic`, `English (QWERTY)`). |
| `displayName` | String | No | Concise display name for space bar and quick switchers (e.g., `English`). Defaults to `name`. |
| `description` | String | No | Subtitle description displayed under the layout in Settings (e.g., `"Standard Latin layout with gesture typing"`). |
| `author` | String | No | Author or creator of the layout or port. |
| `credit` | String | No | Attribution / acknowledgement note (aliases: `acknowledgement`, `credits`). |
| `license` | String | No | License governing the layout (e.g. `Apache-2.0`, `MIT`, `GPLv3`). |
| `sourceUrl` | String | No | Link to upstream repository, port source, or standard (aliases: `source`, `source_url`). |
| `language` | String | Yes | ISO 639-1 two-letter language code (e.g., `en`, `bn`, `ar`, `es`). |
| `direction` | String | No | Text and keyboard flow direction: `"LTR"` (default) or `"RTL"`. |
| `gesture_mode` | String | No | Gesture typing model identifier (e.g., `"qwerty"`). |
| `flag` / `flags` | String or Array of String | No | One or more behavioral flags (e.g., `"NoAutoCapitalization"`, `["NoAutoCapitalization", "NoSuggestions"]`). See Supported Layout Flags below. |
| `processor` | String | No | Transliteration engine processor (e.g., `"m17n"`). |
| `rules` | String | No | Path to the transliteration ruleset file located in `assets/rules/` (e.g., `"bn-khipro.classic.mim"`). |
| `extraCompositionChars` | Array of String or String | No | Characters that remain part of the active composing word rather than triggering an auto-commit (e.g., `[",", "।"]`). |
| `extraRows` | Array of `LayoutExtraRow` | No | Optional feature rows that appear above the main alphabet keys. |
| `numberRow` | `KeyRow` | No | Optional layout-specific number row override. |
| `shiftedNumberRow` | `KeyRow` | No | Optional layout-specific number row displayed when Shift or Caps Lock is active. |
| `symbolsLayout` | `KeyboardLayoutDef` | No | Optional layout-specific symbols page (`?123`). Used to match row count and supply language-specific symbols. Falls back to default symbols if omitted (`symbols` is also accepted). |
| `moreSymbolsLayout` | `KeyboardLayoutDef` | No | Optional secondary symbols page (`=\<`). Falls back to default more symbols if omitted. |
| `rows` | Array of `KeyRow` | Yes | List of main keyboard rows from top to bottom. |

---

### Supported Layout Flags (`flag` / `flags`)

Flags can be specified as a single string (`"flag": "NoAutoCapitalization"`), a delimited string (`"flag": "NoAutoCapitalization, NoSuggestions"`), or a JSON array (`"flags": ["NoAutoCapitalization", "NoSuggestions"]`).

| Flag | Description |
|---|---|
| `NoAutoCapitalization` | Prevents automatically shifting to uppercase at sentence boundaries. Essential for transliteration layouts like Khipro. |
| `NoAutoSpace` | Disables automatically inserting a space after punctuation or suggestions. |
| `NoDoubleSpacePeriod` | Disables inserting a period (`.`) on double spacebar tap. |
| `NoSuggestions` | Disables next-word prediction and candidate completion bars for this layout. |
| `NoLearnedWords` | Prevents words typed in this layout from being learned into the dynamic user dictionary. |
| `NoGesture` | Disables gesture (swipe) typing even if enabled globally in settings. |
| `NoAutoCorrect` | Disables automatic replacement of misspelled words. |
| `NoRemoveSpaceBeforePunctuation` | Disables automatically removing space before punctuation marks. Essential for layouts where punctuation characters act as composition tokens (e.g., Khipro). |

---

### Custom Symbol Pages (`?123` and `=\<`)

Layouts can optionally provide their own `symbolsLayout` and `moreSymbolsLayout`.
- **Row Height and Count Synchronization**: Whether switching between the alphabet page and `?123` (or `=\<`), Kiebord preserves identical row heights and row counts:
  - If the top number row is enabled (`showNumberRow`), both the alphabet and symbol pages render the top number row at row index 0 with matching height `(50 * scaleFactor).dp`.
  - If an extra row (such as Arabic Harakat diacritics or custom feature rows) is enabled, both the alphabet and symbol pages render the extra row with matching standard height `keyRowHeight`.
  - If either row is disabled by the user or layout configuration, neither page renders it.
- **Dynamic Extra Row Carry-Over and Linking**: If an alphabet layout defines an enabled extra row (such as `harakat`), the symbols page can either provide its own corresponding extra row under the same `id` (e.g. Quranic stop marks for Arabic symbols), or Kiebord automatically carries over the alphabet extra row to the symbols page if row counts need matching. Toggling the feature in Settings updates both pages in lockstep.
- **Duplicate Number Avoidance**: When the top number row is enabled on a symbol layout whose first core row contains digits, Kiebord automatically replaces that core row with the alternate symbols row (`~ < > _ ` º \ = { }` or shifted symbols) so numbers are not repeated across consecutive rows.
- **Localized Symbols**: Language-specific punctuation (such as Arabic question mark `؟`, comma `،`, semicolon `؛`, Eastern Arabic numbers `١, ٢, ٣...`, or Quranic stop marks) can be positioned directly on the symbol page.
- **Automatic Fallback**: If a layout does not specify `symbolsLayout` (such as English QWERTY or Bengali layouts), Kiebord automatically falls back to the standard built-in symbols page with synchronized top number row behavior.

---

### `LayoutExtraRow` Properties

Extra rows allow layouts to provide specialized key rows (such as Arabic Harakat diacritics, accented vowel bars, or mathematical symbol strips). Kiebord automatically creates a toggle switch under the layout in Settings for each declared extra row.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique identifier for the feature row (e.g., `harakat`). |
| `title` | String | Yes | Human-readable label displayed in Settings (e.g., `Harakat row (حركات)`). |
| `defaultEnabled` | Boolean | No | Whether the row is enabled by default (`true` or `false`). Default is `true`. |
| `keys` | Array of `KeyDef` | Yes | List of key definitions for this row. |

---

### `KeyRow` Properties

| Field | Type | Required | Description |
|---|---|---|---|
| `keys` | Array of `KeyDef` | Yes | Ordered list of keys in this row. |
| `startPaddingWeight` | Float | No | Horizontal spacing weight before the first key (default: `0.0`). |
| `endPaddingWeight` | Float | No | Horizontal spacing weight after the last key (default: `0.0`). |

---

### `KeyDef` Properties

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | String | No | Primary character or symbol displayed on the key cap. |
| `shiftedLabel` | String | No | Label displayed when Shift or Caps Lock is active. |
| `commitText` | String | No | Text sent to the input field if different from `label`. Useful for combining diacritics. |
| `shiftedCommitText` | String | No | Text committed when Shift or Caps Lock is active if different from `shiftedLabel`. |
| `type` | String | No | Key functional type (see Key Types table below). Default: `"CHARACTER"`. |
| `icon` | String | No | Vector icon identifier (`"backspace"`, `"enter"`, `"globe"`, `"space"`, `"shift"`). |
| `widthWeight` | Float | No | Relative width multiplier. Standard key is `1.0`. Space is typically `4.0`. Modifiers are often `1.5`. |
| `flickUp` | String | No | Character displayed as a hint in the top-right corner and committed on flick-up gesture. |
| `shiftedFlickUp` | String | No | Flick-up character when Shift or Caps Lock is active. |
| `moreKeys` | Array of String | No | List of characters or actions shown in the long-press popup menu. |
| `moreKeysColumns` | Integer | No | Maximum number of columns in the long-press popup menu. |
| `shiftedMoreKeys` | Array of String | No | Long-press popup menu items displayed when Shift or Caps Lock is active. |
| `slidingInput` | Boolean | No | When `true`, sliding from this key seamlessly transitions to symbols mode (used on `?123`). |

---

## 4. Key Functional Types

| Key Type | Description |
|---|---|
| `CHARACTER` | Standard typing key that outputs text (default). |
| `SHIFT` | Toggles Shift (Unshifted, One-Shot, Caps Lock). |
| `DELETE` | Backspace key with tap, long-press delete, and swipe-to-delete support. |
| `ACTION` | Enter / Search / Send / Done action key. |
| `SPACE` | Space bar with gesture trackpad cursor control. |
| `SYMBOLS` | Toggles between alphanumeric and symbol layout modes. |
| `SWITCH_LANGUAGE` | Globe key that switches between enabled keyboard layouts. |

---

## 5. Built-In Actions in `moreKeys`

You can include special action codes in the `moreKeys` array to provide direct access to editor utilities:

| Action Code | Function |
|---|---|
| `ACTION_SELECT_ALL` | Selects all text in the active input field. |
| `ACTION_CUT` | Cuts selected text to the clipboard. |
| `ACTION_COPY` | Copies selected text to the clipboard. |
| `ACTION_PASTE` | Pastes current clipboard contents. |
| `ACTION_SETTINGS` | Opens Kiebord settings. |
| `ACTION_ONE_HANDED` | Toggles one-handed mode. |
| `ACTION_EMOJI` | Opens the emoji picker. |

---

## 6. Minimal Layout Template

Save the following content as `sample_layout.json` to create a starter layout:

```json
{
  "id": "sample_layout",
  "name": "Sample QWERTY",
  "description": "Sample QWERTY layout for testing",
  "language": "en",
  "direction": "LTR",
  "rows": [
    {
      "keys": [
        { "label": "q", "shiftedLabel": "Q", "flickUp": "1", "moreKeys": ["1"] },
        { "label": "w", "shiftedLabel": "W", "flickUp": "2", "moreKeys": ["2"] },
        { "label": "e", "shiftedLabel": "E", "flickUp": "3", "moreKeys": ["3", "é", "è"] },
        { "label": "r", "shiftedLabel": "R", "flickUp": "4", "moreKeys": ["4"] },
        { "label": "t", "shiftedLabel": "T", "flickUp": "5", "moreKeys": ["5"] },
        { "label": "y", "shiftedLabel": "Y", "flickUp": "6", "moreKeys": ["6"] },
        { "label": "u", "shiftedLabel": "U", "flickUp": "7", "moreKeys": ["7"] },
        { "label": "i", "shiftedLabel": "I", "flickUp": "8", "moreKeys": ["8"] },
        { "label": "o", "shiftedLabel": "O", "flickUp": "9", "moreKeys": ["9"] },
        { "label": "p", "shiftedLabel": "P", "flickUp": "0", "moreKeys": ["0"] }
      ]
    },
    {
      "startPaddingWeight": 0.5,
      "endPaddingWeight": 0.5,
      "keys": [
        { "label": "a", "shiftedLabel": "A", "flickUp": "@", "moreKeys": ["@"] },
        { "label": "s", "shiftedLabel": "S", "flickUp": "#", "moreKeys": ["#"] },
        { "label": "d", "shiftedLabel": "D", "flickUp": "$", "moreKeys": ["$"] },
        { "label": "f", "shiftedLabel": "F", "flickUp": "%", "moreKeys": ["%"] },
        { "label": "g", "shiftedLabel": "G", "flickUp": "&", "moreKeys": ["&"] },
        { "label": "h", "shiftedLabel": "H", "flickUp": "-", "moreKeys": ["-"] },
        { "label": "j", "shiftedLabel": "J", "flickUp": "+", "moreKeys": ["+"] },
        { "label": "k", "shiftedLabel": "K", "flickUp": "(", "moreKeys": ["("] },
        { "label": "l", "shiftedLabel": "L", "flickUp": ")", "moreKeys": [")"] }
      ]
    },
    {
      "keys": [
        { "type": "SHIFT", "icon": "shift", "widthWeight": 1.5 },
        { "label": "z", "shiftedLabel": "Z", "moreKeys": ["ACTION_SELECT_ALL"] },
        { "label": "x", "shiftedLabel": "X", "moreKeys": ["ACTION_CUT"] },
        { "label": "c", "shiftedLabel": "C", "moreKeys": ["ACTION_COPY"] },
        { "label": "v", "shiftedLabel": "V", "moreKeys": ["ACTION_PASTE"] },
        { "label": "b", "shiftedLabel": "B" },
        { "label": "n", "shiftedLabel": "N" },
        { "label": "m", "shiftedLabel": "M" },
        { "type": "DELETE", "icon": "backspace", "widthWeight": 1.5 }
      ]
    },
    {
      "keys": [
        { "label": "?123", "type": "SYMBOLS", "widthWeight": 1.5, "slidingInput": true },
        { "label": ",", "widthWeight": 1.0, "moreKeys": ["ACTION_SETTINGS", "ACTION_ONE_HANDED"] },
        { "type": "SWITCH_LANGUAGE", "icon": "globe", "widthWeight": 1.0 },
        { "label": " ", "type": "SPACE", "icon": "space", "widthWeight": 4.0 },
        { "label": ".", "widthWeight": 1.0, "moreKeys": ["?", "!", ":", ";", "-"] },
        { "type": "ACTION", "icon": "enter", "widthWeight": 1.5, "moreKeys": ["ACTION_EMOJI"] }
      ]
    }
  ]
}
```

---

## 7. Adding Layout-Specific Extra Rows

To provide an optional extra row (such as the Harakat row in Arabic or an accent strip in Spanish):

```json
{
  "id": "ar_arabic",
  "name": "Arabic",
  "displayName": "Arabic",
  "language": "ar",
  "direction": "RTL",
  "extraRows": [
    {
      "id": "harakat",
      "title": "Harakat row (حركات)",
      "defaultEnabled": false,
      "keys": [
        { "label": "ـَ", "commitText": "\u064E" },
        { "label": "ـً", "commitText": "\u064B" },
        { "label": "ـُ", "commitText": "\u064F" },
        { "label": "ـٌ", "commitText": "\u064C" },
        { "label": "ـِ", "commitText": "\u0650" },
        { "label": "ـٍ", "commitText": "\u064D" },
        { "label": "ـْ", "commitText": "\u0652" },
        { "label": "ـّ", "commitText": "\u0651" },
        { "label": "ـٰ", "commitText": "\u0670" },
        { "label": "ـٖ", "commitText": "\u0656" },
        { "label": "ـٗ", "commitText": "\u0657" }
      ]
    }
  ],
  "rows": [
    ...
  ]
}
```

When Kiebord loads this layout:
1. It automatically adds a **Harakat row (حركات)** switch under the layout card in **Settings > Languages & Layouts**.
2. Users can toggle the row on or off with immediate live preview updating.
3. The choice is preserved across reboots in app preferences.
4. On symbol pages (`?123` and `=\<`):
   - For each enabled extra row in the alphabet layout, if the symbol page defines a corresponding extra row (matching `id`), that row is used. If not found, the alphabet extra row is moved to the symbol page so row counts and heights match.
   - If the top number row is enabled, the symbol page uses its own defined `numberRow` or automatically places the alphabet layout's number row at the top.

---

## 8. Reference Sample Layouts in `assets-src/layouts/`

Complete, production-tested sample layouts are provided in the repository under [`assets-src/layouts/`](../assets-src/layouts/):

| File | Layout ID | Highlights & Featured Capabilities |
|---|---|---|
| [`en_qwerty.json`](../assets-src/layouts/en_qwerty.json) | `en_qwerty` | **Canonical Reference Schema**: 10-key number row with superscript fractions & Bangla numerals in `moreKeys`, dedicated embedded symbol pages (`symbols_1` and `symbols_2`), gesture typing mode (`"gesture_mode": "qwerty"`), and action shortcuts (`ACTION_SELECT_ALL`, `ACTION_CUT`, `ACTION_COPY`, `ACTION_PASTE`, `ACTION_EMOJI`). |
| [`bn_national_2004.json`](../assets-src/layouts/bn_national_2004.json) | `bn_national_2004` | National Layout: BDS 1738:2004 Layout |
| [`bn_khipro_classic.json`](../assets-src/language-packs/khipro/layouts/bn_khipro_classic.json) | `bn_khipro_classic` | **Transliteration Engine Layout (Extension)**: Packaged in `khipro.bornolang` alongside Khipro Dark. Integrates with the m17n engine (`"processor": "m17n"`, `"rules": "bn-khipro.classic.mim"`), phonetic gesture typing model (`"gesture_mode": "bn_borno"`), behavioral flags (`"flags": ["NoAutoCapitalization", "NoRemoveSpaceBeforePunctuation"]`), non-breaking composition characters (`"extraCompositionChars": [",", "।"]`), and dedicated Bangla digits number row. |
| [`bn_unijoy.json`](../assets-src/language-packs/unijoy/layouts/bn_unijoy.json) | `bn_unijoy` | **Fixed Layout (Extension)**: Packaged in `unijoy.bornolang` as a layout-only pack (no dictionary or rules). Uses the built-in fixed Bangla processor (`"processor": "bn_fixed"`), shifted labels with `flickUp` / `shiftedFlickUp` vowel forms, and layout-specific symbol pages with Bangla digits. |

Developers and contributors can use these sample files as starting blueprints when designing new layouts or adapting existing ones.

---

## 9. Language Extension Packs (.bornolang)

Language extension packs allow adding new languages, layouts, binary dictionaries, and gesture typings to Kiebord dynamically without modifying the Kotlin or Rust codebase.

### Pack Archive Structure

A `.bornolang` file is a ZIP archive containing the following layout:

```text
<pack_id>.bornolang (Standard ZIP)
|-- pack.json                  (Required: Manifest metadata)
|-- layouts/                   (Required: 1 or more layout JSON definitions)
|   |-- <layout1>.json
|   `-- <layout2>.json
|-- dictionaries/              (Optional: Binary trie dictionary file)
|   `-- <lang>.dict
|-- rules/                     (Optional: Script boundaries & word connectors)
|   `-- script_rules.json
`-- gesture/                   (Optional: Diacritic fallbacks & confusion pairs)
    `-- char_folding.json
```

### Manifest Schema (`pack.json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Unique identifier for the pack (e.g. `tr`). |
| `version` | Integer | Yes | Incremental version number (e.g. `1`). |
| `language` | String | Yes | ISO 639-1 language code (e.g. `tr`). |
| `locale` | String | No | BCP-47 language tag (e.g. `tr-TR`) used for locale-aware uppercase/lowercase transformations. |
| `displayName` | String | Yes | Display name shown in Settings (e.g. `Türkçe (Turkish)`). |
| `author` | String | Yes | Name or handle of the author/contributor. |
| `credit` | String | No | Attribution / acknowledgement note for the overall pack (aliases: `acknowledgement`, `credits`). |
| `license` | String | No | Distribution license (e.g. `MIT`, `GPLv3`). |
| `layouts` | Array of String | Yes | List of layout IDs included in the pack. Each must correspond to `layouts/<id>.json`. |
| `dictionaryFile` | String | No | Path relative to pack root pointing to the binary dictionary (e.g. `dictionaries/tr.dict`). |
| `dictionaryCredit` | String | No | Attribution for wordlist / vocabulary source (e.g. `"LibreOffice Hunspell contributors"`, aliases: `wordlistCredit`, `dictionaryAcknowledgement`). |
| `dictionaryLicense` | String | No | Wordlist license (e.g. `LGPLv2.1`, `CC-BY-SA`, aliases: `wordlistLicense`). |
| `dictionarySource` | String | No | URL to upstream dictionary repository or dataset (aliases: `wordlistSource`). |
| `scriptRulesFile` | String | No | Path relative to pack root pointing to script boundary rules (e.g. `rules/script_rules.json`). |
| `charFoldingFile` | String | No | Path relative to pack root pointing to gesture folding rules (e.g. `gesture/char_folding.json`). |
| `currencySymbol` | String | No | Default currency symbol displayed on the keyboard (e.g. `₺`). |
| `description` | String | No | Short description for catalog listings. |

### Layout Definition Guidelines in Language Packs

Layout files under `layouts/` follow the same schema described in Section 3 of this guide, with the following recommendations:
- **`gesture_mode`**: For non-English layouts using spatial gesture typing, set `"gesture_mode"` to `"spatial"`, `"direct"`, or to the language code (e.g. `"tr"`). Legacy `"qwerty"` is also accepted for backward compatibility.
- **Language tag**: Set `"language"` to match the pack's language code so candidate predictions and suggestion strip queries bind to the pack's dictionary.

### Script Rules (`rules/script_rules.json`)

Script rules define word boundaries and prevent characters from different writing systems from corrupting the composing buffer:

| Field | Type | Required | Description |
|---|---|---|---|
| `primary_script` | String | Yes | Name of the primary script (e.g. `LATIN`, `ARABIC`, `CYRILLIC`). |
| `allowed_characters` | String | Yes | String containing all valid alphabet characters (both lowercase and uppercase). |
| `word_connectors` | Array of String | No | Punctuation marks that can appear inside a word without breaking composition (e.g. `["'", "’"]`). |

Example:
```json
{
  "primary_script": "LATIN",
  "allowed_characters": "abcçdefgğhıijklmnoöprsştuüvyzABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ'’",
  "word_connectors": ["'", "’"]
}
```

### Gesture Folding Rules (`gesture/char_folding.json`)

Gesture folding maps allow compact keyboard layouts (which lack dedicated keys for certain diacritics) to resolve accented characters from base key strokes:

| Field | Type | Description |
|---|---|---|
| `version` | Integer | Schema version (currently `1`). |
| `fold_to_base` | Object | Map of accented characters to their base unaccented key on the layout. |
| `confusion_groups` | Array of Arrays | Groups of characters considered interchangeable during gesture matching. |
| `case_pairs` | Array of Arrays | Case equivalence pairs for scripts with non-standard casing (e.g. dotted and dotless I in Turkish). |

Example:
```json
{
  "version": 1,
  "fold_to_base": {
    "ğ": "g",
    "ü": "u",
    "ş": "s",
    "ö": "o",
    "ç": "c",
    "ı": "i"
  },
  "confusion_groups": [
    ["i", "ı"]
  ],
  "case_pairs": [
    ["i", "ı"]
  ]
}
```

### Packaging Packs with `tools/pack_lang.py`

Language pack source assets are maintained under `assets-src/language-packs/<pack_id>/`.

To package a pack:
```bash
# Package a single pack:
python tools/pack_lang.py turkish

# Validate without building:
python tools/pack_lang.py turkish --validate-only

# Package all packs in assets-src/language-packs/:
python tools/pack_lang.py --all
```

The script performs the following tasks:
1. Validates `pack.json`, layout schemas, script rules, and char folding definitions.
2. Compiles any `.combined` dictionary source files into `.dict` if needed.
3. Assembles the `.bornolang` ZIP archive under `language-packs/releases/<id>.bornolang`.
4. Computes the SHA-256 checksum and file size.
5. Updates or appends the entry in `language-packs/index.json`.

---

## 10. Dictionary Wordlists and Compilation (.combined)

Kiebord uses a binary trie dictionary format (`.dict`) for fast on-device candidate lookup, prefix search, and next-word prediction. Raw wordlists are authored in the `.combined` text format.

### File Format Specification

A `.combined` file consists of three sections:

1. **Header metadata**: Semicolon-delimited key-value pairs at the beginning of the file.
   ```text
   name=Sample English Dictionary;author=Kiebord;version=1;date=1700000000;license=GPLv3;
   ```
2. **Unigram entries**: Word and unigram frequency (0-255).
   ```text
   word=Hello,f=200
   ```
3. **Bigram entries**: Expected subsequent words associated with preceding words for next-word prediction.
   ```text
   word=Hello,f=200
     bigram=World,f=150
     prev_word[0]=Hello
   ```

### Reference Template

A reference sample is provided in the repository at [`assets-src/dictionaries/en_sample.combined`](../assets-src/dictionaries/en_sample.combined):

```text
name=Sample English Dictionary;author=Kiebord;version=1;date=1700000000;license=GPLv3;
word=Hello,f=200
  bigram=World,f=150
  prev_word[0]=Hello
word=World,f=180
word=I,f=220
  bigram=use,f=190
  prev_word[0]=I
word=use,f=190
  bigram=Borno,f=170
  prev_word[0]=use
word=Borno,f=180
```

### Compiling to Binary Dictionary (.dict)

To compile a `.combined` file into a `.dict` binary:

```bash
cargo run --manifest-path native/borno-engine/Cargo.toml --bin compile_dict <input.combined> <output.dict>
```

When building language extension packs, `tools/pack_lang.py` automatically invokes this compiler when `.combined` source files are present in the pack directory.
