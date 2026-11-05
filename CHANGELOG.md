# Changelog

All notable changes to mpvGo are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Issue numbers refer to the upstream
[mpvEx issue tracker](https://github.com/marlboro-advance/mpvEx/issues) unless noted.

This file is updated with every batch of changes and before every release push.

## [Unreleased]

### Added
- Browser/Player: browser web videos open in a small Android Picture-in-Picture floating window over the browser by default, with an option to choose "Small window" or "Full screen" in Settings › Browser › Videos (#11).
- Browser: compact tab count switch and live device RAM indicator (used / total and free GB) in the tabs drawer header (#18).
- Browser: brief tab position flash (e.g. "3/5") on the top bar tabs button when switching tabs (#4).
- Browser: address bar tab switching now supports fling gestures, provides light haptic feedback, and bounces when reaching the first or last tab (#2).
- Browser: tapping the Private box in the tabs drawer when there are no private tabs directly creates a new private tab and focuses the address bar with the keyboard (#5).
- Browser: layered video detection with capture-phase element listeners, DOM src observation, fetch/XHR content sniffing for HLS/DASH playlists, and detection log (#12).
- Browser: explanation cards for hidden-playlist streams and DRM/MSE blob streams in the found videos sheet (#12).
- Browser: option to stop site autoplay videos until tapped (Settings › Browser › Videos) (#10).

### Changed
- Browser: opening the tabs panel by swiping from the left edge is now off by default to avoid accidental triggers with system back navigation and web sliders. It can still be turned on in Settings › Browser › Gestures.

### Fixed
- Player: fixed PiP window disappearing when leaving the app by setting autoRemoveFromRecents to false (#681).
- Player: fixed video continuing to play after closing PiP with the close button (#762).
- Player: added PiP capability and permission checks before entering PiP mode to avoid UI hangs on unsupported devices (#736).
- Browser: autoplaying and background page videos no longer force-open mpvGo or show takeover dialogs; only user-initiated playback triggers takeover (#10).
- Browser: muted, looping, and preview videos under 200px without controls are ignored for player takeover (#10).
- Browser: swiping the address bar to switch tabs now tracks the current tab dynamically and switches reliably on every swipe.
- Browser: tapping outside the open tabs drawer or swiping left to close it now always closes the drawer even when the swipe-to-open gesture is turned off.
- Browser: pressing Back on a tab opened from a link or pop-up now closes the tab and returns to the opener tab before exiting the browser.
- Browser: opening the tabs drawer always opens on the section matching the current tab (Normal or Private), fixing state sticking to the previously selected section.


## [1.1.0] - 2026-10-28

This release adds the Go browser: a built-in web browser with ad blocking, video detection
that plays web videos in mpvGo, and a download manager.

### Added
- Go Browser entry points: a ringed globe in the top bar of every main tab and a globe button
  at the bottom left of Home and Downloads.
- Downloads tab (replaces the Network tab), with a badge for active downloads.
- Swipe left or right to switch between the main tabs (Home, Recents, Playlists, Downloads).
  Swiping is off while selecting, searching or with the Play menu open.
- Downloader: multi-connection HTTP downloads and HLS downloads (AES-128 decrypted, remuxed to
  MP4 with a .ts fallback). Downloads run in a background service, resume after the app is
  closed and retry failed connections up to 10 times. Finished videos appear in the library
  right away.
- Downloads list with filters, search, a selection mode, pause/resume, details and a menu for
  finished videos (play, open with, share, show in folder, copy link, download again, delete).
- New download sheet (the "+" button, and a Download button in Open Link) with smart naming,
  folder, connection count and Wi-Fi only options.
- Play while downloading: the video streams from the partial file while it downloads.
- Download notifications for progress and finished videos, with Play and Share actions.
- Download settings (folder, limits, connections, Wi-Fi only, battery optimization prompt).
- Ad blocking engine for the Go browser (Brave adblock-rust). Filter lists (EasyList,
  EasyPrivacy, uBlock filters, cookie notices) are downloaded when the browser first opens and
  updated weekly; nothing loads until you open the browser. Adds about 1 MB to the APK.
- Go browser core: tabs and private tabs (separate cookies and storage, no history), ad and
  tracker blocking with element hiding, and video detection. A "videos found" chip lists the
  videos on the page; when a page starts a video, mpvGo asks whether to play it in mpvGo or the
  web player (with "Remember for this site"). Videos open in mpvGo with the page's cookies,
  referrer and user agent, so protected streams keep working.
- Go browser interface: a start page with a search box (Google, DuckDuckGo, Brave, Bing,
  Startpage or a custom engine) and editable speed dials; a top bar with the tab count, address
  box (swipe it to switch tabs), Bookmarks and Downloads buttons and a menu; a tabs drawer with
  normal and private tabs; find in page; desktop site per tab; pull to refresh; a per-site
  settings sheet (ad block, pop-ups, what happens when you tap a video); and a floating media
  button with the number of videos found.
- Go browser bookmarks and history: bookmark a page from the menu star (with folders and
  "Also add to start page"); a Bookmarks and History page with search, folders, move, multi-select,
  share, and import and export of browser bookmark files (HTML); history grouped by day, with
  played videos tagged; clear browsing data by time range (history, cookies, cache, site settings).
  Open tabs are remembered across restarts (private tabs never are).
- Go browser settings (Settings › Browser, or Browser settings in the browser menu), with a search
  box and pages for appearance (site tiles, text size, dark websites, desktop by default), top bar
  and gestures (pick up to two top bar buttons, such as Home or Ad block), search (engine,
  suggestions from your history and bookmarks, a copied link), tabs (remember tabs, open links
  from other apps), video playback, downloads, ad block (filter lists, your own filters, allowed
  sites), privacy (block third-party cookies, always use HTTPS, Safe Browsing, Do Not Track, site
  permissions, clear data when you exit, how long to keep history) and advanced (user agent,
  JavaScript, reset). Suggestions are looked up on the device; nothing you type is sent until you
  search. Browser settings also appear in the main settings search.
- adblock-rust (MPL-2.0) is listed under Open source licences.
- Downloads from the browser: when a page has a video, the Ask popup offers Download as well as
  Play. The found-videos sheet shows a quality choice for streams, marks videos you have already
  downloaded, names files after the page title and has "Download all". Long-press a link to open
  it in a new tab, copy it, play it or download it. Other file links (for example a .zip) open
  the New download sheet with the site's file name. If a file is already downloaded you can play
  it or download it again. Protected (DRM) videos, YouTube and in-page (blob) videos show why they
  can't be downloaded. A failed download whose link has expired offers "Refresh link": it opens
  the page again and picks up the new link when you play the video. The video button shows a
  progress ring and the menu shows how many downloads are running. Downloads from a private tab
  don't appear in Recents.

### Changed
- Network streaming moved out of the bottom bar. Open it from the Play button menu (new
  "Network" item) or from the new "Browser, Downloads & Network" section at the top of Settings.
- The subtitle gestures switch moved from Settings › Gestures to Settings › Subtitles
  (new "Gestures" section at the end). The saved setting is kept.

### Fixed
- Browser: the list of videos found on a page now clears when you type a new address.
- Subtitle gestures: a short drag on the subtitle while the controls were hidden also counted
  as a tap and brought up the controls, which lifted the subtitle away from the finger. A touch
  claimed by a subtitle gesture is no longer a tap; a plain tap still toggles the controls.
- The system "Open with" chooser listed mpvGo twice ("mpvGo" and "Media Info"). Media Info no
  longer registers for opening files or links; it is still available in the app and from Share.
- Network streams opened with HTTP headers: the headers are now applied before the stream opens
  instead of after, every header in the "headers" extra is used (the first pair was dropped unless
  it was the User-Agent), and opening a new stream no longer reuses the previous stream's
  User-Agent, cookies or other headers.

## [1.0.0] - 2026-10-25

First public mpvGo release. mpvGo is now independent of mpvEx: package `app.arju.mpvgo`,
own database, backups and notification channel. Data from 0.1.0 test installs is not carried
over.

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
- GitHub releases are signed with the mpvGo release key.

### Changed
- Battery: the playback position is polled only while the controls or seekbar are visible.
- Battery/startup: the full storage media scan on every app launch was removed; new files are
  still picked up by the system media store and folder refreshes.
- mpvGo is now independent: the package is `app.arju.mpvgo` and the internal names (database
  `mpvgo.db`, settings backup root `mpvGoSettings`, notification channel, mpv user-data keys,
  User-Agent `mpvGo/1.0`) were renamed. **Existing test installs lose their library history and
  settings once** (no public release yet, so no migration). README keeps the mpvEx credit.
- Performance/battery audit:
  - Library refresh skips the forced full storage rescan when nothing changed; pull-to-refresh
    still finds new files.
  - Playlist, folder and video lists use stable keys and map lookups instead of repeated
    list scans.
  - Wavy seekbar only animates while playing and visible.
  - Player controls no longer recompose on every position update.
  - Network streaming: shared daemon thread pool, larger buffers, thread-safe caches, and
    stopping a stream no longer blocks the main thread.
  - Playback notification updates only when position drifts or speed/duration changes,
    instead of every second.
  - Library lists stop collecting database updates while the screen is in the background.
  - Empty and permission-denied screens pulse 3 times then stop animating.
  - Cache maintenance runs at most once a day, 15 s after launch.
  - Baseline Profile + ProfileInstaller for faster startup and scrolling (GitHub/standard build).
  - Smaller APK: only English resources are kept; Compose source information removed from builds.
- A stream or file that failed to open (or was still opening) is no longer saved with the previous
  file's position; resume saves now wait until the file has actually loaded.
- Empty playlists show the "0 Videos" chip.

### Not done (by decision)
- Database destructive-migration fallback and new indices: left as-is (database upgrades are rare).
- Remaining audit items skipped by the user, plus the exit-while-loading cleanup (tied to #540).

### Investigating
- Two media sessions (notifications/controls) can appear for one playback.
- Crash on mpv property calls after the player is destroyed
  ([#792](https://github.com/marlboro-advance/mpvEx/issues/792)); the known trigger was removed
  upstream, still being watched.
- Hang when exiting while a file is loading
  ([#540](https://github.com/marlboro-advance/mpvEx/issues/540)).

## Planned / requested

- Built-in web browser (home-screen tile, video detection, ad blocking).
- Downloads (HTTP/HLS) with a Downloads page.
- Donate links.
- Rebranded documentation.

## [0.1.0]

### Added
- Initial mpvGo fork of mpvEx: new app name, icons and application ID
  (`io.github.arju22.mpvgo`), so it installs alongside mpvEx.
- Android 14+ (minSdk 34), arm64-v8a only builds.
- Single-source versioning in `version.properties`; release tags `vX.Y.Z` are checked by CI.
