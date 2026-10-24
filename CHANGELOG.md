# Changelog

All notable changes to mpvGo are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Issue numbers refer to the upstream
[mpvEx issue tracker](https://github.com/marlboro-advance/mpvEx/issues) unless noted.

This file is updated with every batch of changes and before every release push.

## [Unreleased]

### Fixed
- Natural sort no longer crashes or misorders names with very long numbers or leading zeros
  ([#861](https://github.com/marlboro-advance/mpvEx/issues/861),
  [#613](https://github.com/marlboro-advance/mpvEx/issues/613),
  [#620](https://github.com/marlboro-advance/mpvEx/issues/620),
  [#721](https://github.com/marlboro-advance/mpvEx/issues/721)).
- Crash when parsing malformed track or chapter lists from mpv
  ([#755](https://github.com/marlboro-advance/mpvEx/issues/755)).
- Resume position is saved reliably when leaving the player, with a checkpoint every 15 s
  while playing ([#859](https://github.com/marlboro-advance/mpvEx/issues/859),
  [#852](https://github.com/marlboro-advance/mpvEx/issues/852),
  [#695](https://github.com/marlboro-advance/mpvEx/issues/695),
  [#683](https://github.com/marlboro-advance/mpvEx/issues/683)).
- Resume restores the exact saved position instead of snapping back to the previous keyframe
  (often the start of the file).
- A finished (or nearly finished) video starts from the beginning instead of reopening in the
  last seconds and closing ([#659](https://github.com/marlboro-advance/mpvEx/issues/659)).
  Rule: saved position within the last 5 s or at 95% or more of the duration.
- Opening a new file while the player is running (including from picture-in-picture) saves the
  previous file's position under its own entry.
- Taps near the screen edges are no longer ignored (48 dp dead zone removed for taps).
- Taps during heavy playback are more reliable: event timestamps and the system touch slop are
  used, and a quick tap in another zone is no longer dropped.

### Added
- Center tap area: with "Single tap for center" on, the play/pause area is an adjustable
  ellipse, edited on a landscape preview page (Settings › Gestures › Center tap area).
- "Bottom strip always shows controls" option (on by default): a tap on the bottom quarter of
  the screen always shows the controls.
- Audio track names show the language name, codec and channels, e.g. "Hindi · AAC 5.1";
  "und" (undetermined) languages are hidden and track titles are kept.
- MX-style subtitle gestures (Settings › Gestures › Subtitles, on by default): a gesture that
  starts on the subtitle acts on it. Swipe left/right for the next/previous line, drag
  up/down to move it, pinch to resize. Position and size are saved. Gestures elsewhere are
  unchanged.
- While the player controls are visible, subtitles move above them so they don't overlap,
  and return to their saved position when the controls hide.
- JVM unit tests (JUnit 4, kotlinx-coroutines-test) for sorting, track parsing, resume,
  tap zones, audio labels and subtitle gestures.

### Changed
- Battery: the playback position is polled only while the controls or seekbar are visible.
- Battery/startup: the full storage media scan on every app launch was removed; new files are
  still picked up by the system media store and folder refreshes.

### Investigating
- Crash on mpv property calls after the player is destroyed
  ([#792](https://github.com/marlboro-advance/mpvEx/issues/792)); the known trigger was removed
  upstream, still being watched.
- Hang when exiting while a file is loading
  ([#540](https://github.com/marlboro-advance/mpvEx/issues/540)).

## Planned / requested

- Wavy seekbar redraw cost (battery).
- APK size quick wins (unused resources, locale filters).
- Built-in web browser (home-screen tile, video detection, ad blocking).
- Downloads (HTTP/HLS) with a Downloads page.
- Donate links.
- Release signing for GitHub releases.
- Rebranded documentation.

## [0.1.0]

### Added
- Initial mpvGo fork of mpvEx: new app name, icons and application ID
  (`io.github.arju22.mpvgo`), so it installs alongside mpvEx.
- Android 14+ (minSdk 34), arm64-v8a only builds.
- Single-source versioning in `version.properties`; release tags `vX.Y.Z` are checked by CI.
