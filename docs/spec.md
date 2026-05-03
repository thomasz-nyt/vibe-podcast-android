# Podcast Player – Session Notes (2026-01-11)

## Recent Fixes
- Playback now prefers downloaded `localPath` for offline episodes.
- PlayerScreen receives podcast artwork and displays it instead of audio URL.
- Manifest: `PlayerService` exported with MediaSessionService intent filter for Media3 SessionToken resolution.
- Compose BOM aligned (2023.10.01) to match compiler; crash resolved.
- Gradle wrapper + AndroidX/Jetifier + SDK path added; debug build verified via `./gradlew assembleDebug` (JDK 17).
- Media notification now uses Media3 PlayerNotificationManager with metadata from episodes.
- Mini player artwork sticks to the active episode.
- Episode IDs now use RSS `guid`/`enclosure` URL to keep list and playback aligned.
- Download flow exposes progress and disables the button while downloading.
- Downloaded playback uses file URIs and preserves original stream URLs.

## Known Warnings/Follow-ups
- Room: schema export path not set (kapt warning); choose `exportSchema=false` or set `room.schemaLocation`.
- AppOps attributionTag warnings from platform; currently harmless.
- MediaPlayerWrapper metadata timeouts observed; benign while session metadata syncs.

## Next-Step Ideas (proposed)
- Proper navigation graph (Navigation Compose) instead of manual string routing.
- Media notification with controls and metadata (artwork/title) using Media3 PlayerNotificationManager. **(In progress/partially added)**
- Download UX: progress, storage usage, retry/cancel, cleanup.
- Playback resilience: handle network errors, show player errors, and auto-reconnect.
- Testing: unit tests for ViewModels (search, downloads), RSS parsing, repository flows.
- Analytics/observability: basic logging for search failures and playback errors.

## Decisions (2026-01-11)
- Priority: media player experience/resilience first.
- Manifest: PlayerService exported + MediaSessionService intent filter to satisfy Media3 SessionToken.
- Compose BOM aligned to 2023.10.01 with compiler 1.5.4 to avoid KeyframesSpec crash.
- Saved podcasts use lightweight local storage (SharedPreferences/JSON) for now.
- Mini player hides only on full player; show artwork if available.
- Hide Saved section when search is focused; show results directly below input.
- Episode list enhancements: parse episode images, strip HTML in descriptions, expand/collapse at 4 lines.
- Sleep timer uses single value default 15 min with +/- 5 min controls; cancel aligns right when active.
- Saved list always reappears when search loses focus.
- Sleep timer stays above playback controls.
- Mini player uses a darker background with larger controls for easy tap.

## Open Questions
- Need background resume/auto-reconnect when app is killed?
- Should we target dark theme/design polish now or later?

## New Feature Plan (2026-01-11)
- **Mini player bar:** Persistent bottom bar on non-player screens showing current episode (title, podcast art), play/pause, and tap-to-open full player.
- **Saved/subscribed podcasts:** Add "+"/Subscribe action on search results; maintain a saved list shown on home. Allow removal. Persist locally (Room or simple prefs) and reconcile with downloads.
- **Lock screen richness:** Ensure MediaSession metadata includes title, artist/podcast name, and artwork; keep notification foreground so lock screen shows media controls/art.
- **Home refinements:** hide Saved section on search focus; saved empty-state card with CTA.
- **Episode detail improvements:** show episode image when available; strip HTML descriptions; show duration + date metadata.
- **Sleep timer UX:** adjustable +/- controls, default 15 min; inline cancel when active.

---

# Podcast Player – Session Notes (2026-05-02)

## Add from URL — YouTube / X offline downloads (issue #33)

Shipped per [docs/specs/006-url-downloads.md](specs/006-url-downloads.md). Lets
the user paste, share, or type a YouTube or X (Twitter) URL and save the audio
(MP3) or video (MP4) for fully offline playback alongside podcast episodes.

### Headlines

- **Three converging entry points** — Share intent, search-paste shortcut card,
  and a home-screen "Add from URL" chip — all open the same `AddFromUrlScreen`.
- **On-device extraction** via [`youtubedl-android` 0.18.1](https://github.com/yausername/youtubedl-android)
  (yt-dlp + ffmpeg). Initialized once in a new `PodcastApplication` class; the
  yt-dlp Python script self-updates from upstream on launch.
- **Foreground service (`dataSync`)** drains a serial queue with progress
  notifications. Concurrency capped at 1.
- **DB v2 → v3** — new `url_downloads` Room table, separate from
  `downloaded_episodes`. Migration registered.
- **Player video support** — `Episode` gains an optional `mediaType` field
  (default `AUDIO`); `PlayerScreen` renders a Media3 `PlayerView`
  (`VideoSurface`) in the artwork slot when the current episode is video.

### Decisions (2026-05-02)

- Personal/internal use only — Play Store ToS risk acknowledged and accepted.
- Single converged screen (`AddFromUrlScreen`) for all three entry flows;
  avoids a bottom-sheet / full-screen split.
- Audio-default in the format picker (smaller files, podcast-like UX).
- App-private storage at `filesDir/url_downloads/` (not external Downloads).
- Stable IDs hash the canonicalized URL + media type so the same URL can exist
  as audio AND video without colliding.
- Synthetic `podcastId = "vibe-url-downloads"` keeps URL items out of
  saved-podcast / queue logic.
- Scope-trim: no Downloads-screen surfacing of URL items, no Wi-Fi-only
  setting, no PiP, no cookie-auth — all flagged as v2 candidates in §15 of
  the spec.

### Open follow-ups

- ABI splits for the youtubedl-android native libs to chop ~40MB off the APK.
- Android 15 16KB-page-size verification of the bundled native libs.
- Reconcile logic for orphaned `DOWNLOADING` rows on service start (process
  death edge case).
- `Downloads` screen surfacing of URL items (currently only Home).
