# Vibe Podcast — App Analysis & Roadmap (July 2026)

A full-codebase audit across four areas: UI/UX, performance, feature completeness, and AI/learning
integration opportunities. Findings carry `file:line` references against the state of the repo at the
time of the audit (post-#51). Items marked **[FIXED]** were addressed in the commits accompanying this
document; everything else is roadmap.

---

## 1. UI / UX

### Navigation

| Finding | Evidence | Impact |
|---|---|---|
| Back always resets to Home instead of unwinding the stack. Search → podcast → Episodes → back lands on **Home**, not Search. Same for Queue, Downloads, Add-URL, Add-Feed. | `PodcastNavHost.kt:411-413, 467-469, 556, 314-317, 339-342` | Disorienting; violates Android back expectations |
| Player back handler *navigates* to `episodes/{id}` rather than popping — can grow/rewrite the back stack. | `PodcastNavHost.kt:593-607` | Back-stack bloat |
| Morning-queue auto-play waits on a hardcoded `delay(2000)` to "let session restore finish". | `PodcastNavHost.kt:149-177` | Race-prone; either signal readiness from `PlayerController` or observe restore state |
| No deep links; the `ACTION_VIEW` intent filter for YouTube/X hosts is commented out. | `AndroidManifest.xml:59-71` | Only Share-to-app works; tapping a link can't open the app |

### Now-playing access

- **No mini-player on the Downloads screen** (or Settings/Add screens). From the Downloads tab there is
  no way back to the player without switching tabs first. `DownloadsScreen` never receives
  `currentEpisode` (`PodcastNavHost.kt:527-557`).
- Mini-player progress line is decorative — `onSeek` is unused (`MiniPlayerBar.kt:46`), so it can't scrub.
- Mini-player dismiss button is 28dp; Home's delete-URL-download icon is **14dp** (`HomeScreen.kt:475`);
  `VibeCircleIconButton` defaults to 40dp. Material minimum touch target is 48dp.
- No "up next" queue view inside the player; skip next/prev is blind.

### States & feedback

- **No empty state for zero search results** — `Success` with an empty list renders a blank area
  (`PodcastListScreen.kt:341-365`).
- **No empty state for a feed with zero episodes** — header says "Episodes / 0" over a blank list
  (`EpisodeListScreen.kt:218-277`).
- Search error state has no Retry affordance (`PodcastListScreen.kt:368-386`).
- Confirm-on-AddFromUrl navigates Home with no snackbar/toast that the download started
  (`PodcastNavHost.kt:310-313`).
- Destructive-action confirmation is inconsistent: dialogs exist for queue-delete and delete-all-downloads,
  but unsubscribe, remove-podcast-from-queue, per-item download delete, and delete-URL-download are all
  single-tap with no confirm and no undo (`QueueScreen.kt:337-343`, `DownloadsScreen.kt:374-380`,
  `HomeScreen.kt:471-478`, `PodcastListScreen.kt:281`). Recommendation: standardize on snackbar-with-undo
  for single items, dialog for bulk.
- No pull-to-refresh anywhere; feed refresh is a top-bar icon on Episodes only.

### Visual / structural

- **Every user-facing string is hardcoded** — no `strings.xml` usage in Compose at all. Blocks
  localization and makes copy edits scattershot.
- Home subscriptions grid is **not lazy** (`chunked(3)` of Rows inside a `verticalScroll` Column,
  `HomeScreen.kt:693-721`); every tile + artwork composes eagerly. Continue-listening and URL-download
  rows are also non-lazy `horizontalScroll` + `forEach`.
- All state collection uses `collectAsState()` instead of `collectAsStateWithLifecycle()` — flows keep
  collecting while backgrounded, which matters for a background-playback app.
- `QueueRow`'s `AsyncImage` has no placeholder/error/fallback (`QueueScreen.kt:311-317`) — the only list
  in the app without one.
- No `animateItemPlacement` on any list, including the drag-reorderable queue.
- No edge-to-edge (`enableEdgeToEdge()` absent) yet some composables manually pad by
  `WindowInsets.statusBars` → double-padding risk, and target SDK 35 will force edge-to-edge.
  No splash screen (`androidx.core:core-splashscreen` absent).
- Landscape bugs: `EpisodeListLandscape` seeds subscription state once into a local `remember` and flips
  it locally — drifts from the real saved state (`EpisodeListScreen.kt:339, 427-430`); landscape player
  shows the raw `podcastId` (a GUID/URL) as the subtitle (`PlayerScreen.kt:680-683`);
  `border(width = 0.dp)` no-op (`EpisodeListScreen.kt:546`).
- Dead code: `MiniPlayerBarLandscape` and `LandscapeBrowseScaffold` (`LandscapeLayouts.kt:132, 270`)
  are never composed; `QueuedEpisode` domain model has zero usages; `onBack`/`onOpenDownloads` params
  suppressed as unused in three screens.
- Player speed control is cycle-only (0.75→2.0); no direct picker, no per-podcast speed.
- Typography: downloadable Google Fonts with no bundled fallback (first-frame flash, Play-Services
  dependency); 8.5–9.5sp metadata text on Home/Player hurts legibility.

---

## 2. Performance

Ranked by user-visible impact.

1. **[FIXED] Main-thread binder IPC every second during playback.** `PlayerController.inferMediaType`
   called `ContentResolver.getType()` — a synchronous Binder IPC — inside `snapshotOf`, which runs on
   every `Player.Listener.onEvents` batch *and* the 1 Hz position ticker. For `content://` media (all
   URL downloads) that is per-second main-thread IPC. (`PlayerController.kt:132-191`) → now memoized
   per URI.
2. **[FIXED] Download progress recomposition storm.** `DownloadManager.copyWithProgress` invoked
   `onProgress` on every 8 KB chunk (`DownloadManager.kt:135-141`); `PodcastViewModel` rebuilt the whole
   `Map<String, Float>` StateFlow per callback (`PodcastViewModel.kt:268-270`) — ~6,400 emissions for a
   50 MB episode, each recomposing every observer. Progress was also never reported when the server
   omitted `Content-Length`. → now emits on whole-percent change + guaranteed completion.
3. **[FIXED] URL-download progress: per-tick Room write + notification + unordered coroutines.**
   yt-dlp's callback fired several times/sec; each tick spawned a fresh coroutine doing a DB write
   (re-emitting every observer Flow) and a `notify()`; out-of-order completion made progress jump
   backwards (`UrlDownloadService.kt:129-141`). → throttled to whole percent on a serialized executor.
4. **[FIXED] No feed caching, no timeouts.** `PodcastRepository` fetched feeds with raw
   `URL.openStream()` — infinite default timeouts (a hung feed host blocks forever) and zero caching, so
   *every* episode-list open re-downloaded and re-parsed the entire feed (`PodcastRepository.kt:36, 51`;
   trigger path `PodcastNavHost.kt:262/354/402` → `selectPodcast` → `loadEpisodes`). → 15 s/30 s
   timeouts + 15-minute in-memory TTL cache, Refresh icon bypasses.
5. **[FIXED] Full-queue JSON serialization on the main thread every 5 s.**
   `PlayerService.persistPlaybackSession` built the session JSON on `Dispatchers.Main` on every persist
   tick, transition, pause, and seek (`PlayerService.kt:135-166`, `PlaybackSessionStorage.kt:34-54`).
   → player state snapshotted on main, serialization + write moved off-main, writes serialized.
6. **Startup: synchronous prefs + Gson on the main thread during composition.**
   `SavedPodcastsStorage`, `QueueStorage`, `QueuePodcastsStorage`, `PlaybackSessionStorage`, `AppSettings`
   all do a blocking prefs read + Gson parse in their constructors/init, and they're constructed during
   Compose composition (`PodcastNavHost.kt:91-112`, `SavedPodcastsStorage.kt:21`, `QueueStorage.kt:30-43`).
   The factory args in `PodcastNavHost.kt:99-107` also aren't `remember`ed. Roadmap: move construction to
   `PodcastApplication` (background prewarm) and `remember` the graph in the NavHost.
7. **Build/deps debt:** Compose BOM `2023.10.01` (misses 2+ years of Lazy-list/strong-skipping perf work),
   Media3 `1.2.1`, Kotlin `1.9.20`/compiler `1.5.4`, `enableJetifier=true` (slows every build, likely
   unneeded), legacy `androidx.media:media` alongside Media3, **no baseline profile** (meaningful
   cold-start win for a Compose+Media3 app). CI never runs the release/R8 build, so the hand-written
   keep rules for Gson-reflected models are untested.
8. Smaller items: `SimpleDateFormat` allocated per episode per format attempt in the RSS parse loop
   (`RssParser.kt:186-189`); unbounded parallel manual downloads (one coroutine per tap, no semaphore);
   `AutoDownloadWorker.enqueuePeriodic` runs a prefs read + WorkManager enqueue on the main thread in
   `Application.onCreate` (`PodcastApplication.kt:46`); yt-dlp self-update hits the network on every cold
   start (`PodcastApplication.kt:62-72`) — should be throttled to ~daily; no `@Index` on
   `downloaded_episodes.podcastId` / `playback_progress.podcastId`; `exportSchema = false` (no migration
   drift guard).

---

## 3. Features: gaps & corrections

### Corrections (bugs in shipped features)

- **[FIXED] Play Queue contradicted its own spec.** `docs/specs/004-podcast-queue-play.md` specifies
  playing **all** unplayed episodes per podcast, oldest→newest; the implementation played only the single
  newest unplayed episode per podcast (`PodcastViewModel.kt:470-499`, `maxByOrNull { pubDate }`). Also
  fetched each feed sequentially. → spec behavior implemented, fetches parallelized (bounded).
- **[FIXED] URL-download partial files leaked.** Workdir cleanup ran only on the success path
  (`UrlDownloadService.kt:169-171`); any failure/cancel left `url_downloads/<id>/` with `.part` files
  forever, and a leftover partial could even be picked up as the "produced file" on retry
  (`UrlDownloadRepository.pickProducedFile:240-249`). → cleanup on all paths + orphan sweep at pump start.
- **Manual RSS downloads die with the process.** `PodcastViewModel.startDownload` runs the download in a
  raw `viewModelScope` coroutine (`PodcastViewModel.kt:260-280`) — no foreground service, no WorkManager,
  no resume; kill the app mid-download and it silently vanishes. (Auto-downloads already use WorkManager;
  manual ones should enqueue through the same worker or a dedicated one.) **P1.**
- No HTTP `Range` resume anywhere — a download that dies at 99% restarts from zero
  (`DownloadManager.kt:96-98`).
- OPML export silently drops podcasts without a `feedUrl` (`OpmlManager.kt:23`).
- Restored-session episodes lose `duration`/`pubDate` (`PlayerController.kt:171-173`); `podcastId` is
  smuggled through `mediaMetadata.artist` — a load-bearing convention worth making explicit.
- RSS episode-ID fallback uses the item *index* when guid and enclosure are both blank
  (`RssParser.kt:133`) — unstable across fetches; playback-progress rows can orphan.

### Missing features (prioritized)

**P1 — high value, moderate effort**
- **Episode-level queue / Play-next / Up-Next.** Queues are podcast-level only; "add to queue" adds a
  whole podcast. The `QueuedEpisode` model was scaffolded and never wired (zero usages). Play-next and
  add-episode-to-queue from the episode list, an up-next sheet in the player, and append/reorder of the
  live Media3 queue are the single biggest product gap.
- **New-episode awareness.** The 24 h `AutoDownloadWorker` already fetches feeds but only to download —
  no "N new episodes" notification, no unread badges, no display-facing feed refresh. Cache the worker's
  parse results and surface them.
- **Manual downloads via WorkManager** (see correction above) + download queue with a concurrency cap,
  pause/resume via `Range` requests, and auto-delete-played / keep-latest-N storage policies.
- **URL downloads:** auto-retry with backoff for transient failures; `--continue` for resume; playlist
  support; quality selection; per-item progress on the AddFromUrl screen; snackbar confirmation on enqueue.
- **Playback:** skip-silence (`setSkipSilenceEnabled` — one-liner with ExoPlayer), volume boost
  (`LoudnessEnhancer`), configurable skip intervals, end-of-episode sleep-timer mode (timer should also
  live in the service, not the ViewModel, to survive process death), chapter support
  (`podcast:chapters` / MP3 chapter frames).
- **CI:** run `./gradlew test` and `lint` on PRs; add an `assembleRelease` job so R8 keep rules for
  Gson-reflected models stop being a release-only landmine.

**P2 — polish / platform**
- Android Auto (`MediaLibraryService` + content tree + manifest declaration), Chromecast (`media3-cast`),
  Wear.
- `strings.xml` migration; edge-to-edge + splash; dependency refresh (Compose BOM, Media3 1.4+,
  Kotlin 2.x + strong skipping, drop Jetifier); baseline profile; predictive back.
- Cookie/login support for age-gated YouTube/X downloads; ABI splits or app bundle to cut the ~50 MB+
  yt-dlp/ffmpeg APK weight; `FileProvider` for sharing downloads out.
- Room: indices on `podcastId` columns, `exportSchema = true` + migration tests.
- Statistics (listening time), per-podcast settings (speed, auto-download count cap), full settings
  backup/export.

---

## 4. AI integration: "Episode Intelligence"

**Goal:** for any episode (RSS, downloaded, or YouTube/X URL download): get a transcript, generate an AI
summary and structured study notes (slides later), and export Markdown to a personal Obsidian vault —
turning the player into a learning tool.

**Decisions taken** (with the app owner): no speech-to-text in the MVP — transcripts come from publisher
`<podcast:transcript>` RSS tags (tier 1) and yt-dlp YouTube captions (tier 2); STT is a later pluggable
tier 3. Obsidian export writes directly into the on-device vault folder chosen once via SAF.

### Architecture (MVP)

- **Transcript tiers**
  1. Parse `<podcast:transcript url type>` in `RssParser` (namespace processing is off, so the literal
     prefixed tag matches like the existing `itunes:image`). Preference: `text/vtt` > SRT > JSON > HTML;
     MVP normalizes VTT + SRT to plain text (strip cues/timestamps, merge rolling-caption duplicates).
  2. For URL downloads: `yt-dlp --skip-download --write-subs --write-auto-subs --sub-format vtt`
     via the existing `youtubedl-android` integration (`UrlDownloadRepository.buildSubtitleRequest`).
  3. (Later) `SpeechToTextProvider` interface + OpenAI-compatible Whisper endpoint; on-device whisper.cpp
     rejected — the APK already carries ~50 MB of Python/ffmpeg and models would add 30–75 MB more.
- **Generation:** Claude API via the official `com.anthropic:anthropic-java` SDK (OkHttp transport —
  already on the classpath; `java.time` is native at minSdk 26 so no desugaring needed). Default model
  `claude-opus-4-8`, user-overridable in Settings (`claude-sonnet-5` is the cost-efficient alternative).
  Always streaming; `max_tokens` 16 000; no `temperature`/`thinking` params (removed on current models).
  One call per artifact with a **shared, byte-identical system prompt + transcript block carrying
  `cache_control`** — generating the summary writes the prompt cache, so notes/slides within the 5-min
  TTL pay ~10% input cost. A 2 h transcript ≈ 40 K tokens: ≈ $0.25 for the first artifact, cents for the
  rest. R8 risk: Jackson needs keep rules; all Anthropic types confined to one provider class so a raw
  SSE fallback over OkHttp is a one-file swap if minified builds misbehave.
- **Persistence:** new `episode_ai` Room table (v3→v4 migration): transcript status/source/path
  (transcript text lives as a file under `filesDir/episode_ai/`, artifacts as columns), summary/notes/
  slides Markdown + timestamps, model used, last error. Survives process death; reopening the screen is
  instant.
- **API key:** `EncryptedSharedPreferences` (new `SecureSettings`, AES256-GCM Keystore master key),
  excluded from auto-backup rules, never echoed in UI. Vault URI/subfolder/model live in plain
  `AppSettings`.
- **Obsidian export:** `ACTION_OPEN_DOCUMENT_TREE` picker (SAF precedent already exists for OPML),
  persisted URI permission, `DocumentFile` write of `yyyy-MM-dd - <title>.md` into a configurable
  subfolder (default `Podcasts/`) with YAML frontmatter (`title`, `podcast`, `date`, `source`, `tags`,
  `model`). Handles revoked permission with a "re-pick vault" error.
- **UI:** new `episode-ai/{episodeId}` route + `EpisodeAiScreen` (transcript card with source chip,
  artifact cards with streamed text, export row), entries from the Player and the episode list; new
  `EpisodeAiViewModel` + manual factory, matching the app's no-DI pattern. Repository is a process-level
  singleton owning its own scope so generation survives navigation.

### Build order

1. **Phase 0:** deps (`anthropic-java`, `security-crypto`, `documentfile`), R8 keep rules, `SecureSettings`,
   Settings UI (key, model, vault picker).
2. **Phase 1:** `podcast:transcript` parsing, transcript normalizer, `episode_ai` table + MIGRATION_3_4,
   `EpisodeAiRepository.ensureTranscript` (tiers 1–2).
3. **Phase 2:** Claude streaming generation (summary + notes), Obsidian exporter, screen + nav + entry
   points. **← MVP complete**
4. **Phase 3:** STT tier, slides artifact, Markdown renderer, JSON/HTML transcript formats,
   `obsidian://` deep link, cost display from `usage`.

Verification gates: unit tests for transcript parsing/normalization/migration and the cache-prefix
invariant (three prompts share byte-identical prefix); end-to-end on a Podcasting-2.0 feed and a YouTube
download; a release-build (R8) smoke test of the full flow.

---

## 5. Roadmap summary

| Priority | Items |
|---|---|
| **P0 — this branch** | Feed timeouts + TTL cache · download-progress throttling (RSS + URL) · main-thread IPC fix in playback snapshots · session persistence off main thread · Play-Queue spec-004 correction + parallel fetch · partial URL-download cleanup + orphan sweep · this document + CLAUDE.md corrections |
| **P1 — next sessions** | Episode Intelligence MVP (AI) · episode-level queue / play-next · manual downloads via WorkManager (+resume, storage policies) · new-episode notifications/badges · URL-download retry/resume/quality · skip-silence + volume boost + service-side sleep timer · empty states + confirm/undo consistency + mini-player on Downloads + scrubbable mini-player · back-navigation fix · CI: tests + lint + release build |
| **P2 — later** | strings.xml/i18n · edge-to-edge + splash · dependency refresh + baseline profile · Android Auto/Cast · chapters · OPML completeness · Room indices/schema export · APK slimming (ABI splits) · statistics & per-podcast settings |
