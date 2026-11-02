// SPDX-License-Identifier: AGPL-3.0-or-later

// Versions are pinned on purpose: reproducible builds (F-Droid) require
// the exact same toolchain on every machine. Never use dynamic versions.
plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
