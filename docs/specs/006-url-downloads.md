# Spec: Add from URL — YouTube/X offline downloads

Project: `vibe-podcast-android`

Issue: [#33](https://github.com/thomasz-nyt/vibe-podcast-android/issues/33)

Owner: TBD

Last updated: 2026-05-02

## 1) Summary

Let the user paste, share, or type a YouTube or X (Twitter) URL into the app and
save the audio (MP3) or video (MP4) for fully offline playback alongside
podcast episodes. The new media is surfaced in a "Saved from URL" section on
the home screen and plays through the existing Media3 pipeline.

This spec keeps things on-device — no server component — and minimizes blast
radius on the existing podcast flow by isolating URL-downloaded items in a
separate Room table and a synthetic `podcastId`.

> ⚠️ **Personal/internal use**. YouTube and X Terms of Service generally
> prohibit unauthorized downloading. Distribution through the Play Store is
> **not** recommended for builds with this feature enabled.

---

## 2) Goals

1. **Three converging entry points**, all leading to the same flow:
   - Android Share intent (`ACTION_SEND` text/plain) from YouTube / X / any app
     that emits a URL.
   - Pasting a URL into the existing search field → inline "Save offline" CTA.
   - Dedicated "Add from URL" chip on the home screen.
2. **Two output formats**, chosen by the user before download starts:
   - Audio: extracted via ffmpeg → MP3.
   - Video: muxed via ffmpeg → MP4 (h264 + aac when available, falling back to
     yt-dlp's `best` selection).
3. **Resilient downloads**:
   - Run in a foreground service so they survive app backgrounding.
   - Show progress in the notification shade and on the home screen.
   - Cancelable mid-flight; failed items can be retried by re-enqueuing.
4. **Familiar playback**:
   - Audio items reuse the existing `PlayerScreen` (artwork + transport).
   - Video items render a Media3 `PlayerView` in the artwork slot of the same
     `PlayerScreen`; transport bar still drives playback.
5. **Backward compatible**:
   - Existing podcast download flow (`downloaded_episodes` table) is untouched.
   - `Episode` data class extended with a default-valued `mediaType` field.

---

## 3) Non-goals

- Cloud sync of saved URL items.
- Captions / subtitles extraction.
- Playlist / channel batch downloads (single video only; `--no-playlist`).
- Authenticated downloads (e.g. age-gated YouTube content, X content behind a
  login). Cookie support could be added later but is out of scope here.
- Live-stream capture.
- Picture-in-picture or background-audio-from-video hybrid modes.
- Storage management UI beyond per-item delete (no global "X MB used" screen
  in this iteration).

---

## 4) User Stories

1. As a user, I tap **Share** on a YouTube video and pick this app, see a
   preview of the title and thumbnail, choose audio or video, and tap save.
2. As a user, I paste a YouTube URL into the search box and notice the
   "Save offline" affordance — tapping it brings me into the same flow.
3. As a user, I open the home screen and tap **"Add from URL"**, paste a URL,
   and follow the same flow.
4. As a user, I see in-flight downloads on the home screen with progress and
   can cancel them.
5. As a user, I tap a saved item and play it — audio plays in the existing
   player; video items render frames inside the same player surface.
6. As a user, I delete a saved item from the home screen and the underlying
   file is reclaimed from disk.

---

## 5) Existing Architecture Touchpoints

| Layer | File | Touchpoint |
| --- | --- | --- |
| App | `MainActivity.kt` | Add `ACTION_SEND` intent filter; route the URL into nav. |
| App | `PodcastApplication.kt` (new) | Initialize `YoutubeDL` + `FFmpeg` on launch. |
| Manifest | `AndroidManifest.xml` | New permissions, share filter, `UrlDownloadService` declaration, Application class. |
| DB | `PodcastDatabase.kt` | Bump v2 → v3, add `url_downloads` entity. |
| Domain | `Episode.kt`, `MediaType.kt` (new) | Optional `mediaType` field; new enum. |
| Service | `UrlDownloadService.kt` (new) | Foreground (`dataSync`) service drains the queue. |
| Service | `PlayerController.kt` | Expose `awaitController()` so video surface can bind. |
| ViewModel | `UrlDownloadViewModel.kt` (new) | Owns preview state + completed/in-flight flows. |
| UI | `AddFromUrlScreen.kt` (new) | Single converged entry point. |
| UI | `VideoSurface.kt` (new) | Compose wrapper around Media3 `PlayerView`. |
| UI | `HomeScreen.kt` | "Add from URL" chip; "Saving from URL" + "Saved from URL" sections. |
| UI | `PodcastListScreen.kt` | Pasted-URL detection card. |
| UI | `PodcastNavHost.kt` | New `add-url?url={url}` route; share-intent handoff. |

---

## 6) Data Model (Room)

### 6.1 New entity: `UrlDownloadEntity`

Stores one row per `(URL, mediaType)` pair. Distinct from `downloaded_episodes`
so URL items can be queried, deleted, and surfaced separately on the home
screen without polluting RSS-podcast flows.

```kotlin
@Entity(tableName = "url_downloads")
data class UrlDownloadEntity(
    @PrimaryKey
    val id: String,                 // SHA-1 of canonical(URL) + "|" + mediaType
    val sourceUrl: String,
    val source: String,             // "youtube" | "x" | "other"
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val mediaType: String,          // "audio" | "video"
    val localPath: String?,         // set once status == COMPLETED
    val durationMs: Long?,
    val fileSize: Long?,
    val status: String,             // UrlDownloadStatus.name
    val progressPercent: Float,     // 0..100
    val errorMessage: String?,
    val createdAtMs: Long,
    val completedAtMs: Long?,
)
```

**Identity**: `id = sha1(canonicalize(sourceUrl) + "|" + mediaType)`.
- Same URL + same format ⇒ same id ⇒ duplicate downloads collapse.
- Same URL but audio vs. video ⇒ two distinct rows can coexist.

`canonicalize` strips `www.`, lowercases the host, and drops tracking params
(`utm_*`, `fbclid`, etc.) while keeping meaningful ones (`v`, `list`, `t`,
`start`).

### 6.2 DAO

```kotlin
@Dao
interface UrlDownloadDao {
    @Query("SELECT * FROM url_downloads ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<UrlDownloadEntity>>

    @Query("SELECT * FROM url_downloads WHERE status = :status ORDER BY createdAtMs DESC")
    fun observeByStatus(status: String): Flow<List<UrlDownloadEntity>>

    @Query("SELECT * FROM url_downloads WHERE id = :id")
    suspend fun getById(id: String): UrlDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UrlDownloadEntity)

    @Query("UPDATE url_downloads SET status = :status, progressPercent = :progress, errorMessage = :error WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float, error: String?)

    @Query(
        """
        UPDATE url_downloads
        SET status = :status, localPath = :localPath, fileSize = :fileSize,
            progressPercent = 100, completedAtMs = :completedAtMs, errorMessage = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(id: String, status: String, localPath: String, fileSize: Long, completedAtMs: Long)

    @Query("UPDATE url_downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: String, status: String, error: String?)

    @Query("DELETE FROM url_downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

### 6.3 Database changes

```kotlin
@Database(
    entities = [
        DownloadedEpisodeEntity::class,
        PlaybackProgressEntity::class,
        UrlDownloadEntity::class,
    ],
    version = 3,
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun urlDownloadDao(): UrlDownloadDao
    // ...existing DAOs
}
```

### 6.4 Migration v2 → v3

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("""
      CREATE TABLE IF NOT EXISTS url_downloads (
        id TEXT NOT NULL,
        sourceUrl TEXT NOT NULL,
        source TEXT NOT NULL,
        title TEXT NOT NULL,
        uploader TEXT,
        thumbnailUrl TEXT,
        mediaType TEXT NOT NULL,
        localPath TEXT,
        durationMs INTEGER,
        fileSize INTEGER,
        status TEXT NOT NULL,
        progressPercent REAL NOT NULL,
        errorMessage TEXT,
        createdAtMs INTEGER NOT NULL,
        completedAtMs INTEGER,
        PRIMARY KEY(id)
      )
    """.trimIndent())
  }
}
```

Registered in `DatabaseProvider.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

---

## 7) Domain Model / Mapping

### 7.1 New: `MediaType` enum

```kotlin
enum class MediaType {
    AUDIO,
    VIDEO;
    val tag: String get() = name.lowercase()
    companion object {
        fun fromTag(tag: String): MediaType =
            entries.firstOrNull { it.tag == tag.lowercase() } ?: AUDIO
    }
}
```

### 7.2 `Episode` extension

```kotlin
data class Episode(
    // existing fields...
    val mediaType: MediaType = MediaType.AUDIO,  // <-- new, default for back-compat
)
```

The default keeps every existing call site source-compatible. URL-downloaded
items set this to `MediaType.VIDEO` when the user picked the video format.

### 7.3 Synthetic podcastId

URL-downloaded items map to `Episode` with:

- `id = "url:${entity.id}"` — distinct namespace from RSS episode ids.
- `podcastId = "vibe-url-downloads"` — synthetic, never matches a real iTunes
  podcast id, keeping queue and saved-podcast logic from accidentally picking
  these up.
- `localPath` set to the on-disk file.
- `mediaType` derived from `entity.mediaType`.

### 7.4 URL classification

`UrlSource.classify(rawUrl)` returns `YOUTUBE | X | OTHER` based on host
matching. Hosts include `youtube.com`, `m.youtube.com`, `music.youtube.com`,
`youtu.be`, `x.com`, `twitter.com`, `mobile.twitter.com`.

Unsupported URLs render an error in the AddFromUrl screen rather than
attempting a download (yt-dlp can in principle handle far more sites, but we
want a confident UX surface for v1).

---

## 8) Download Pipeline

### 8.1 Native init (PodcastApplication)

`PodcastApplication.onCreate` runs (off the main thread):

1. `YoutubeDL.getInstance().init(this)` — unpacks the bundled Python runtime
   and yt-dlp script on first launch (idempotent on subsequent launches).
2. `FFmpeg.getInstance().init(this)` — unpacks the bundled ffmpeg.
3. Best-effort `YoutubeDL.updateYoutubeDL(this, UpdateChannel.STABLE)` — pulls
   the latest yt-dlp from upstream so platform changes don't break extraction.

A volatile flag `PodcastApplication.youtubeDlReady` flips to `true` once init
succeeds. The repository checks this gate before invoking yt-dlp and falls
back to a user-visible error if false (typical only on first launch with no
network).

### 8.2 Repository (UrlDownloadRepository)

Public surface:

```kotlin
class UrlDownloadRepository(context: Context) {
    val downloadDir: File   // filesDir/url_downloads/

    fun observeAll(): Flow<List<UrlDownloadEntity>>
    fun observeCompleted(): Flow<List<UrlDownloadEntity>>
    fun observeInFlight(): Flow<List<UrlDownloadEntity>>
    fun observe(id: String): Flow<UrlDownloadEntity?>

    suspend fun fetchMetadata(rawUrl: String): UrlMetadata?
    suspend fun enqueue(rawUrl: String, mediaType: MediaType, prefetched: UrlMetadata? = null): String
    suspend fun delete(id: String): Result<Unit>
    fun toEpisode(entity: UrlDownloadEntity): Episode?

    // mutators called by the service
    suspend fun markExtracting(id: String)
    suspend fun markDownloading(id: String, progress: Float)
    suspend fun markFailed(id: String, message: String?)
    suspend fun markCanceled(id: String)
    suspend fun markCompleted(id: String, file: File)

    fun buildDownloadRequest(entity: UrlDownloadEntity, outputTemplate: File): YoutubeDLRequest
}
```

### 8.3 Format selection

Encoded into the `YoutubeDLRequest` returned by `buildDownloadRequest`:

| Media type | yt-dlp options |
| --- | --- |
| Audio | `-x`, `--audio-format mp3`, `--audio-quality 0`, `-f "bestaudio/best"` |
| Video | `-f "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"`, `--merge-output-format mp4` |

Common: `--no-playlist`, `--no-mtime`, `--socket-timeout 30`,
`-o "{outdir}/%(id)s.%(ext)s"`.

### 8.4 Storage location

App-private under `Context.filesDir/url_downloads/`. Each in-flight item
downloads into a per-id subdirectory; on success the produced file is moved to
`url_downloads/{id}.{ext}` and the workdir is deleted.

App-private (rather than `Environment.DIRECTORY_DOWNLOADS`) keeps ToS exposure
minimal — files aren't visible to other apps.

### 8.5 Foreground service (UrlDownloadService)

Single-instance foreground service with `foregroundServiceType="dataSync"` and
the `FOREGROUND_SERVICE_DATA_SYNC` permission. Lifecycle:

1. `enqueue` inserts a `QUEUED` row.
2. `UrlDownloadService.startPump(context)` is called; the service promotes
   itself to foreground with a generic "preparing…" notification.
3. The pump (a single-flight coroutine) drains the queue serially:
   - Find the oldest `QUEUED` row.
   - Mark `EXTRACTING_METADATA`, then `DOWNLOADING`.
   - Run `YoutubeDL.execute(request)` with a progress callback that updates
     the DB row + the notification.
   - On completion: locate the produced file (prefer mp4/mp3/m4a/webm,
     fallback to largest), move it into `url_downloads/{id}.{ext}`, mark
     `COMPLETED`.
4. When no more `QUEUED` rows remain, the service stops itself.

Concurrency is intentionally **1**: yt-dlp + ffmpeg are CPU-heavy and
oversubscribing on lower-end devices is bad. (Future: configurable.)

Cancel: `UrlDownloadService.cancel(context, id)` invokes
`YoutubeDL.destroyProcessById("url-dl-$id")`, which kills the underlying
process; the service catches `CanceledException` and marks the row `CANCELED`.

### 8.6 Status state machine

```
QUEUED → EXTRACTING_METADATA → DOWNLOADING → COMPLETED
                              ↘ FAILED
                              ↘ CANCELED
```

Statuses are persisted as the enum's `name` (`String`) on `status` to keep the
schema migration-friendly.

---

## 9) UI / UX Changes

### 9.1 Routes

`PodcastNavHost` adds a new route:

```
"add-url?url={url}"  → AddFromUrlScreen
```

`url` is optional. When entered via Share or paste, it's URL-encoded into the
route arg; when entered via the dedicated chip, the screen opens with an
empty field.

### 9.2 AddFromUrlScreen

Vertical stack:

1. `VibeTopBar` with eyebrow "OFFLINE · YOUTUBE / X" + back button.
2. URL input card (BasicTextField with monospace font).
3. Metadata preview area, transitioning through:
   - **Idle** — illustration + tagline.
   - **Loading** — skeleton card with spinner.
   - **Loaded** — thumbnail (16:9), source chip, duration chip, title (≤3
     lines), uploader.
   - **Error** — `LinkOff` glyph + message ("Couldn't read that URL. It may be
     private, age-gated, or temporarily unavailable.").
4. Format picker (when `Loaded`): two square tiles (`Audio` / `Video`) with a
   selected accent. Audio is the default.
5. Primary CTA pill: **"Save audio offline" / "Save video offline"** depending
   on selection.

### 9.3 Home screen sections

Order (top to bottom):

- Greeting header.
- Quick-action row (currently just "Add from URL" chip).
- "Saving from URL" — vertical column of in-flight items with progress bars
  and cancel button. Hidden when empty.
- "Continue listening" — existing.
- "Saved from URL" — horizontally-scrolling row of completed URL downloads.
  Each card has thumbnail, source badge, format badge (AUDIO/VIDEO), title,
  uploader, and a tiny delete affordance. Hidden when empty.
- "Your subscriptions" — existing (unless empty AND no URL items, in which
  case the existing empty-state copy is broadened: "Search for podcasts, or
  paste a YouTube / X link to save offline.").

### 9.4 Search-screen URL detection

When the user types/pastes text into the search field that matches a YouTube
or X URL (`UrlValidator.extractFirstUrl` + `UrlSource.classify`), an inline
`UrlDownloadShortcutCard` appears between the input and the results:

```
┌────────────────────────────────────────────┐
│ [↓] Save this YouTube link offline         │
│      https://youtu.be/dQw4w9WgXcQ          │
└────────────────────────────────────────────┘
```

Tapping it clears focus and navigates to `add-url?url={url}`.

### 9.5 Share intent

`MainActivity` is `singleTask` and registers:

```xml
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="text/plain" />
</intent-filter>
```

`MainActivity.extractSharedUrl()` pulls the first http(s) URL out of
`Intent.EXTRA_TEXT`. The activity exposes a `pendingShareUrl` Compose state;
`PodcastNavHost`'s `LaunchedEffect(sharedUrl)` navigates to
`add-url?url={url}` and clears the slot via `onSharedUrlConsumed`.

A commented-out `ACTION_VIEW` filter exists in the manifest for direct
URL-launch handling — left disabled by default to avoid fighting the official
YouTube / X apps.

### 9.6 Player screen — video support

`PlayerScreen` checks `episode.mediaType`:

- `AUDIO` (default) — renders the existing `AsyncImage` artwork box.
- `VIDEO` — renders a new `VideoSurface()` composable in the same slot, both
  for the portrait and landscape layouts.

`VideoSurface` is an `AndroidView` wrapper around Media3's `PlayerView`. It
awaits `PlayerController.awaitController()` (a new method exposing the
underlying `MediaController`) and binds it as the view's player. The Media3
`PlayerView`'s built-in controls are disabled (`useController = false`) so
the existing custom transport bar continues to drive playback.

`PlayerController.getCurrentEpisode()` infers `MediaType` from the file
extension (`mp4`, `webm`, `mkv`, `mov`, `avi`, `m4v` → VIDEO; else AUDIO) so
restored sessions render correctly even though `MediaItem` metadata doesn't
carry a media-type marker.

---

## 10) Repository / ViewModel Integration

### 10.1 ViewModel: `UrlDownloadViewModel : AndroidViewModel`

```kotlin
class UrlDownloadViewModel(application: Application) : AndroidViewModel(application) {
    val previewState: StateFlow<UrlPreviewState>          // Idle | Loading | Loaded | Error
    val selectedMediaType: StateFlow<MediaType>           // user toggle
    val completedDownloads: StateFlow<List<UrlDownloadEntity>>
    val inFlightDownloads: StateFlow<List<UrlDownloadEntity>>

    fun loadPreview(rawUrl: String)
    fun setMediaType(mediaType: MediaType)
    fun confirmCurrentDownload()                          // enqueue + startPump
    fun deleteDownload(id: String)
    fun cancelDownload(id: String)
    fun resetPreview()
}
```

Constructed via a manual `UrlDownloadViewModelFactory(application)` mirroring
the codebase convention (no DI).

### 10.2 Manual factories

`PodcastNavHost` builds three top-level ViewModels:

| ViewModel | Factory | New? |
| --- | --- | --- |
| PodcastViewModel | PodcastViewModelFactory | existing |
| PlayerViewModel | PlayerViewModelFactory | existing |
| UrlDownloadViewModel | UrlDownloadViewModelFactory | new |

All three remain scoped at the `PodcastNavHost` so state survives navigation.

### 10.3 Wiring summary

```
[Share / Paste / Chip]
        │
        ▼
PodcastNavHost ── nav("add-url?url=…") ──▶ AddFromUrlScreen
        │                                          │
        │                            confirmCurrentDownload()
        │                                          │
        ▼                                          ▼
UrlDownloadViewModel ──── enqueue ──▶ UrlDownloadRepository ──▶ Room
                                              │
                                  startPump(context)
                                              │
                                              ▼
                                   UrlDownloadService (FG)
                                              │
                            yt-dlp execute + progress callback
                                              │
                                  markDownloading / markCompleted
                                              │
                                              ▼
                                          Room
                                              │
                  observeCompleted / observeInFlight (StateFlow)
                                              │
                                              ▼
                                       HomeScreen renders
```

---

## 11) Edge Cases / Rules

- **Duplicate dedupe**: enqueue checks for an existing row in
  `IN_FLIGHT_STATUSES + COMPLETED`; if found, returns the existing id with no
  side effects. Repeat enqueues of failed/canceled items reset the row and
  re-queue.
- **Live streams / unknown duration**: `VideoInfo.duration` is `0` on absent;
  treated as null in our model. The download itself still succeeds when
  yt-dlp can resolve a finite stream; live streams are not supported.
- **Auth-walled X content**: `fetchMetadata` returns null; the AddFromUrl
  screen shows the generic error. Cookie-based auth is out of scope.
- **Very large videos**: no hard size cap. Storage management UI is
  intentionally minimal in v1 (per-item delete only).
- **Concurrent shares**: `MainActivity` is `singleTask`. A second share while
  the AddFromUrl screen is already open re-enters via `onNewIntent`, updates
  `pendingShareUrl`, and the nav effect navigates to a fresh route arg.
- **Process death during download**: the foreground service keeps the process
  alive; if the OS still reaps it (unlikely for `dataSync`), the row is left
  in `DOWNLOADING` state. The next time the service starts, it picks up only
  `QUEUED` rows — orphan in-flight rows show up to the user as stuck. (Future:
  reconcile non-`QUEUED` rows on service start.)
- **yt-dlp binary outdated**: the `youtubeDlReady` gate ensures we don't
  attempt extraction before init. The on-launch `updateYoutubeDL` call is
  best-effort; a stale binary may cause 4xx-style errors that surface as
  `FAILED`.

---

## 12) Performance & Storage

- **APK size**: `youtubedl-android` 0.18.1 + ffmpeg adds ~50MB to the APK from
  the bundled Python runtime + binaries. ABI splits could chop this in 4 — out
  of scope for v1; flag for release builds.
- **First-launch unpacking**: `YoutubeDL.init` writes ~20MB of unpacked
  binaries to `noBackupFilesDir`. One-time cost per install.
- **Concurrent downloads**: capped at 1 to avoid CPU/RAM oversubscription.
  Sequential queue drains predictably even on low-end devices.
- **Progress writes**: yt-dlp emits a progress callback at variable rates
  (sub-second). We serialize each tick into a `UPDATE` query — minor write
  amplification but bounded by the underlying media duration. Not enough to
  warrant debouncing for v1.
- **Notification updates**: `setOnlyAlertOnce(true)` and `setSilent(true)`
  prevent buzz / sound spam.

---

## 13) Testing Checklist

### 13.1 Unit tests (JVM)

Located in `app/src/test/java/com/podcastplayer/app/`:

- `UrlSourceTest`:
  - YouTube hosts (incl. `m.`, `music.`, `youtu.be`) classify YOUTUBE.
  - X / Twitter hosts classify X.
  - Unrelated hosts classify OTHER.
  - Malformed input falls back to OTHER.
  - `tag` → `fromTag` round-trips.
- `UrlValidatorTest`:
  - `extractFirstUrl` returns null for blank input; pulls a URL out of
    share-style text; handles `youtu.be` short links and X URLs.
  - `isSupportedUrl` recognizes YouTube/X, rejects unrelated.
  - `stableId` is deterministic and differentiates `audio` vs `video`.
  - `canonicalize` strips `utm_*` but keeps `v` and `t`.
- `MediaTypeTest`:
  - `tag` is the lower-case enum name.
  - `fromTag` is round-trip safe and falls back to `AUDIO` for unknown.

### 13.2 Migration tests (instrumented)

- Create v2 DB with `downloaded_episodes` + `playback_progress` populated.
- Run migration 2→3.
- Verify `url_downloads` table exists and existing tables are intact.

### 13.3 Integration tests (instrumented; future)

- Mock `YoutubeDL` and verify `UrlDownloadService` calls
  `markDownloading`/`markCompleted` in the correct sequence.
- Verify `enqueue` is idempotent for duplicate URL+mediaType pairs.
- Verify `delete` removes both the row and the file.

### 13.4 UI tests (Compose; future)

- `AddFromUrlScreen`:
  - Loaded state renders thumbnail, title, audio/video selector, CTA.
  - Tapping CTA invokes `onConfirm`.
- `HomeScreen` renders the URL-downloads section when items exist; empty when
  none; in-flight column visible when downloads in progress.

### 13.5 Manual QA checklist

- Paste `https://www.youtube.com/watch?v=dQw4w9WgXcQ` into search → "Save
  offline" affordance appears.
- Tap it → metadata preview loads (title, thumbnail, duration).
- Pick "Audio" → tap save → notification appears with progress.
- In-flight row appears in home-screen "Saving from URL".
- On completion, row moves to "Saved from URL"; tap to play in audio player.
- Repeat with "Video" → `PlayerScreen` renders frames in the artwork slot.
- Share a YouTube URL from the YouTube app → routes into AddFromUrl screen.
- Share an X URL → metadata extraction works.
- Cancel an in-flight download from the home-screen card → row disappears.
- Delete a completed item → file is removed from `filesDir/url_downloads/`.
- Restart app while video is playing → frames continue to render after
  session restore.

---

## 14) Rollout Plan

1. Land manifest + dependencies + Application class (Phase 1).
2. Land `MediaType`, `Episode.mediaType`, `UrlDownloadEntity`, DAO, migration
   v2→v3 (Phase 2).
3. Land `UrlDownloadRepository`, `UrlSource`, `UrlValidator` (Phase 3).
4. Land `UrlDownloadService` (Phase 4).
5. Land share intent on `MainActivity` (Phase 5).
6. Land search-screen URL detection (Phase 6).
7. Land `AddFromUrlScreen` + ViewModel + factory (Phase 7).
8. Land home-screen sections + nav route (Phase 8 + 9).
9. Land `VideoSurface` + PlayerScreen integration (Phase 10).
10. Unit tests (Phase 13).
11. Update docs (this spec, README, CLAUDE.md) (Phase 14).

All phases land in a single PR for #33 since the feature is meaningful only
end-to-end.

---

## 15) Open Questions

- Should the search-screen "Save offline" affordance also detect non-YouTube/X
  URLs that yt-dlp supports (e.g. Vimeo, SoundCloud)? Currently we hide it for
  `OTHER` to keep the UX confident.
- Should `Downloads` screen also list URL items, or remain RSS-podcast only?
  Currently the Home screen is the only surface; revisit if users get lost.
- Should we expose a "Wi-Fi only" download preference? Out of scope for v1
  but a likely follow-up.
- Should we support "Download as both audio and video"? Today the user picks
  one; the dedupe key allows both rows to coexist if they enqueue twice.
- Should we add cookie support for X auth-walled content? Powerful but raises
  the security/UX bar significantly.
- Should we add picture-in-picture for video items? Native Android API exists
  but requires Activity work.
