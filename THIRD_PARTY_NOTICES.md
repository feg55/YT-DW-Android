# Third-Party Notices

This file is part of the distributed application. Do not remove it from forks or binary releases.

## youtubedl-android 0.18.1

- Components: `library` and `ffmpeg`.
- License declared by the Maven artifact publisher: GNU GPL v3.0.
- Source code and build instructions: https://github.com/yausername/youtubedl-android/tree/0.18.1
- Source archive: https://github.com/yausername/youtubedl-android/archive/refs/tags/0.18.1.tar.gz

The `ffmpeg` module contains native FFmpeg binaries and related libraries. FFmpeg itself is distributed under LGPL-2.1-or-later, or GPL-2.0-or-later when GPL components are enabled during the build: https://ffmpeg.org/legal.html. The publisher declares the complete Maven module as GPL-3.0.

The published AAR does not include separate LICENSE/NOTICE files, and its build instructions use Termux packages without pinning a Termux revision. A distributor of a binary release must therefore preserve access to the corresponding source code and build information. Before publishing an APK, attach the source archive for tag `0.18.1` to the GitHub Release and keep the source link available for as long as the APK remains available.

## yt-dlp 2026.07.04

- Included file: Unix zipimport executable `yt-dlp`.
- Main source code license: The Unlicense.
- The zipimport build also includes code under the ISC and MIT licenses.
- Release source: https://github.com/yt-dlp/yt-dlp/tree/2026.07.04
- Source archive: https://github.com/yt-dlp/yt-dlp/archive/refs/tags/2026.07.04.tar.gz
- License: https://github.com/yt-dlp/yt-dlp/blob/2026.07.04/LICENSE

The downloaded file version and SHA-256 checksum are pinned in `app/build.gradle.kts`.

## AndroidX, Kotlin, and other dependencies

AndroidX, Jetpack Compose, Room, WorkManager, Kotlin, and kotlinx.coroutines are primarily distributed under the Apache License 2.0. Test dependencies are not included in the release APK. Exact artifact coordinates and versions are listed in `app/build.gradle.kts`; the licenses published by the copyright holders of each artifact apply.

## Corresponding source code

For every published APK, the project's corresponding source code is the Git tag from which it was built, together with the Gradle Wrapper, build scripts, and pinned dependency versions. Source code for included GPL/LGPL binaries must remain available next to the APK or through clearly identified stable links, as required by the applicable licenses.
