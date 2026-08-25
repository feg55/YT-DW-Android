# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows Semantic Versioning.

## [Unreleased]

### Changed

- Simplified release APK names to `v8-lite`, `v7-legacy`, `x86-emulator`, and `universal`.

## [0.1.3] — 2026-08-24

### Added

- Parallel analysis of links and playlists.
- Up to three parallel downloads and a configurable number of concurrent fragments.
- Destination folder selection through Android MediaStore.
- Download queue state persistence and restoration after restart.
- Database migration and instrumentation tests.

### Changed

- Accelerated playlist analysis through flat metadata extraction.
- Reduced YouTube connection time with a fast client that skips optional HLS/DASH manifests and falls back to full extraction when needed.
- Removed preliminary preparation of temporary media URLs that caused repeated connections and HTTP 403 errors.
- Throttled progress updates to avoid overloading Room and the user interface.
- Moved yt-dlp and FFmpeg initialization out of the download hot path.

### Fixed

- Application termination after analysis or operation cancellation.
- Download queue stalls after canceling a yt-dlp process.
- Display of the actual source connection and downloader preparation stages.
- Unavailable cover art no longer stops the main download.

[Unreleased]: https://github.com/feg55/YT-DW-Android/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/feg55/YT-DW-Android/releases/tag/v0.1.3
