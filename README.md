# Kiebord (Borno Community Edition)
<p align="center">
<img width="128" height="128" alt="Kiebord" src="https://github.com/user-attachments/assets/346d3099-7f42-462a-931c-959a216ebccc" />
</p>


<table>
  <tr>
<td><img width="512" alt="borno-phonetic" src="https://github.com/user-attachments/assets/35c84108-40bb-4f04-99e7-04c9f9d58019" /></td>
<td><img width="512" alt="national" src="https://github.com/user-attachments/assets/ce2f1910-4eb1-4d67-8f2b-b00b0153ae72" /></td>
  </tr>
</table>

[![CI Build](https://github.com/JayedAhsan/Kiebord/actions/workflows/publish.yml/badge.svg?branch=main)](https://github.com/JayedAhsan/Kiebord/actions/workflows/publish.yml)
[![Latest Release](https://img.shields.io/github/v/release/JayedAhsan/Kiebord?include_prereleases&label=Latest%20Release)](https://github.com/JayedAhsan/Kiebord/releases)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2024%2B)-blue)](https://developer.android.com)
[![Rust](https://img.shields.io/badge/Engine-Rust%20NDK-orange)](https://www.rust-lang.org)
[![Kotlin](https://img.shields.io/badge/UI-Jetpack%20Compose-purple)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-GPL%203.0-green.svg)](https://www.gnu.org/licenses/gpl-3.0)

A free, open-source input method editor (IME) for Android built with Jetpack Compose and a native Rust engine.

---

## Overview

Kiebord is the result of over 6 years of development on Borno Keyboard, heavily relying on its foundational transliteration architecture while introducing improved gesture typing, spatial touch modeling, and optimized runtime performance.

The codebase represents an extensive modernization from legacy Java and C++ into modern Kotlin, Jetpack Compose, and a pure Rust core (`borno-engine`), with every migration step manually audited, refined, and verified.

---

## Features

Kiebord comes with the essential features you need for everyday typing, without bloat or complicated configuration menus. Additional capabilities will be implemented gradually.

### Supported Layouts

| Language | Layouts |
|---|---|
| Bangla | Borno Phonetic, Avro Phonetic, Borno Easy, National (BSTI), Probhat, Khipro, Unijoy |
| English | QWERTY, Borno Math |
| Arabic | Arabic (with dedicated Harakat / diacritics row) |
| Turkish *(Extension)* | Türkçe (Q), Türkçe (Compact), Türkçe (F) |

*Additional languages, layouts, and dictionaries can be installed as `.bornolang` packs via **Settings > Language Extensions** (available through the in-app catalog or sideloaded from device storage). Custom layouts can also be added by placing JSON layout files in `assets/layouts/`.*

### Input & Editing
* **Gesture Typing**: Swipe-to-type support across supported layouts.
* **Next-Word Predictions**: On-device candidate completions and next-word suggestions.
* **Spacebar & Backspace Gestures**: Slide across the spacebar to navigate the cursor; slide left from the backspace key to quickly erase words.
* **Clipboard Manager**: History of copied text with pin/unpin support.
* **Text Editing Panel**: Dedicated directional arrows, text selection handles, copy, cut, paste, undo, and redo.
* **Emoji Picker**: Built-in emoji panel with search (WIP) and category tabs.
* **Voice Typing**: Direct speech-to-text input via the system voice recognizer.
* **One-Handed Mode & Resizing**: Toggleable left/right one-handed modes and adjustable keyboard height.
* **Haptic Feedback**: Keypress vibration support.
* **Themes**: Themes curated from Borno Keyboard.
* **Straightforward Settings**: Clean, uncluttered preference screens without confusing toggles.

---

## Project Structure

```
kiebord/
├── app/                  # Application entry point, onboarding, and settings UI
├── core/
│   ├── common/           # Shared models (KeyDef, KeyType, etc.)
│   ├── data/             # Preferences datastore and layout repositories
│   └── theme/            # Theme models and theme parser
├── feature/
│   └── ime/              # InputMethodService implementation, Compose canvas, and JNI bindings
├── native/
│   └── borno-engine/     # Rust native library (spatial model, trie dictionaries, BornoTSF)
└── docs/                 # Layout and theme development specifications
```

---

## Building from Source

### Prerequisites
* Android Studio Ladybug / Meerkat or Android SDK command-line tools
* JDK 17 or 21
* Android SDK Platform 35
* Android NDK `26.2.11394342`
* Rust 1.75+ with `cargo-ndk` (only required if modifying native Rust sources)

### Build Commands

Clone the repository:
```bash
git clone https://github.com/JayedAhsan/Kiebord.git
cd Kiebord
```

Build the debug APK:
```bash
./gradlew assembleDebug
```

Build the release APK:
```bash
./gradlew assembleRelease
```

Build the release Android App Bundle (for Google Play):
```bash
./gradlew bundleRelease
```

Binary dictionary assets (`.dict`) are tracked in the repository. To compile the native Rust engine (`borno-engine`) into `libborno_engine.so`:

```bash
cd native/borno-engine
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../../feature/ime/src/main/jniLibs build --release
```

---

## Support & Sponsor

If you find Kiebord helpful, you can support its ongoing maintenance and development:
* [Support Kiebord](https://jayed.me/support-kiebord)

---

## Organization & Maintainer

* **Organization**: [Codepotro](https://codepotro.com)
* **Lead Maintainer**: [Jayed Ahsan Saad](https://jayed.me) ([@JayedAhsan](https://github.com/JayedAhsan))

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the `LICENSE` file for the full license text.

All contributions are subject to the [Contributor License Agreement (CLA)](CLA.md), granting **Codepotro** dual-licensing rights for the Borno keyboard ecosystem.
