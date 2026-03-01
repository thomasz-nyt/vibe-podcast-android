# CLAUDE.md — Vibe Podcast Android

This file provides guidance for AI coding assistants working on this Android podcast player app.

## Project Overview

A Kotlin/Jetpack Compose podcast player for Android that supports search, RSS feed parsing, audio playback, offline downloads, playback queues, sleep timer, and background playback via a foreground service.

- **App ID:** `com.podcastplayer.app`
- **Min SDK:** 26 (Android 8.0) | **Target/Compile SDK:** 34
- **Language:** Kotlin | **JVM Target:** 17
- **UI:** Jetpack Compose + Material3

---

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.podcastplayer.app.presentation.viewmodel.PodcastViewModelTest"

# Run instrumented (connected) tests
./gradlew connectedAndroidTest

# Lint checks
./gradlew lint
./gradlew :app:lint

# Clean build outputs
./gradlew clean

# Full build (including checks)
./gradlew build
```

> **JDK requirement:** JDK 17 is required. The `.tool-versions` file configures this for asdf/mise. If Gradle fails, ensure `JAVA_HOME` points to a JDK 17 installation.

---

## Repository Structure

```
vibe-podcast-android/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/podcastplayer/app/
│           ├── MainActivity.kt              # Single-Activity entry point
│           ├── data/
│           │   ├── local/                   # Room DB + SharedPreferences storage
│           │   ├── remote/                  # iTunes API + RSS parser
│           │   └── repository/             # PodcastRepository, DownloadManager
│           ├── domain/
│           │   └── model/                  # Domain data classes + enums
│           ├── presentation/
│           │   ├── viewmodel/              # MVVM ViewModels
│           │   └── ui/                     # Compose screens + navigation
│           ├── service/                    # Media3 playback service
│           └── ui/theme/                  # Material3 theme
├── docs/
│   ├── spec.md                            # Session notes & design decisions
│   └── specs/                             # Feature specifications
├── .github/workflows/                     # CI/CD (GitHub Actions)
├── AGENTS.md                              # Coding conventions (shared with CLAUDE.md)
└── README.md
```

---

## Architecture

The app uses **MVVM + Repository pattern** with no dependency injection framework (manual factory construction).

### Layer Responsibilities

| Layer | Responsibility |
|---|---|
| **UI** (`presentation/ui`) | Stateless Compose screens; emits events, observes StateFlow |
| **ViewModel** (`presentation/viewmodel`) | Owns UI state as `StateFlow`; orchestrates repositories |
| **Repository** (`data/repository`) | Abstracts data sources; returns `Result<T>` |
| **Data sources** (`data/local`, `data/remote`) | Room, SharedPreferences, Retrofit, RSS parser |
| **Domain** (`domain/model`) | Plain data classes/enums; no Android dependencies |
| **Service** (`service`) | Background Media3 playback; progress persistence |

### ViewModel Construction

There is **no Hilt/Dagger**. ViewModels are created via manual factories:

- `PodcastViewModelFactory` — injects `PodcastRepository`, `DownloadManager`, `SavedPodcastsStorage`, `QueueStorage`, `PlaybackProgressDao`
- `PlayerViewModelFactory` — injects `PlayerController` singleton

Both ViewModels are scoped at the `PodcastNavHost` top level so state survives navigation transitions.

---

## Key Source Files

### Navigation
**`presentation/ui/PodcastNavHost.kt`** — Navigation Compose host; defines all routes and wires ViewModels to screens.

Routes:
```
"search"                 → PodcastListScreen
"episodes/{podcastId}"   → EpisodeListScreen
"queue"                  → QueueScreen
"downloads"              → DownloadsScreen
"player"                 → PlayerScreen
```

All "back" presses pop to `"search"` (inclusive = false) rather than following the system back stack.

### ViewModels
**`presentation/viewmodel/PodcastViewModel.kt`** — Manages podcast search, episode list, saved podcasts, queue management, download state, and playback progress.

Exposed `StateFlow` values:
- `uiState: PodcastUiState` — search results state (Initial/Loading/Success/Error)
- `episodesUiState: EpisodesUiState` — episode list for selected podcast
- `selectedPodcast`, `selectedEpisode` — current selections
- `downloadedEpisodes`, `downloadProgress` — download state per episode
- `savedPodcasts` — subscribed podcasts
- `queues`, `selectedQueueId`, `selectedQueuePodcasts` — queue management
- `downloadedEpisodesAll`, `downloadedEpisodesUi` — downloads screen data
- `playbackProgress` — map of episodeId → PlaybackProgressEntity

**`presentation/viewmodel/PlayerViewModel.kt`** — Controls playback state, sleep timer, and queue navigation.

Exposed `StateFlow` values:
- `playerState: PlayerState` — PlaybackState enum + position/duration/speed
- `currentEpisode: Episode?` — currently playing
- `currentArtworkUrl: String?`
- `sleepTimerRemaining: Long?`
- `hasPrevious`, `hasNext: Boolean`

### Data Layer
**`data/remote/iTunesApi.kt`** — Retrofit interface; `GET https://itunes.apple.com/search?term=…&media=podcast&limit=25`

**`data/remote/RssParser.kt`** — `XmlPullParser` to extract episodes from RSS feeds. Episode IDs prefer RSS `guid`, fall back to enclosure URL.

**`data/repository/PodcastRepository.kt`** — Combines iTunes search + RSS parsing; both methods return `Result<T>`.

**`data/repository/DownloadManager.kt`** — Downloads audio files to `getExternalFilesDir(DIRECTORY_PODCASTS)/episodes/`. Uses MD5 of episode ID/URL for filenames. Returns `Result<Unit>`.

**`data/local/SavedPodcastsStorage.kt`** — SharedPreferences `saved_podcasts` pref, JSON-serialized via Gson. Exposes `StateFlow<List<Podcast>>` with `Mutex` for safe concurrent writes.

**`data/local/QueueStorage.kt`** — SharedPreferences `podcast_queues` pref. Manages named queues of podcast IDs. Creates a default `"Morning"` queue on first run.

**`data/local/PodcastDatabase.kt`** — Room database (v2). Tables:
- `downloaded_episodes` — downloaded episode metadata
- `playback_progress` — per-episode listen position and completion status

### Service Layer
**`service/PlayerService.kt`** — `MediaSessionService` wrapping ExoPlayer. Handles:
- Foreground playback notification (channel: `podcast_player_channel`)
- Auto-resume from saved position on media item transition
- Progress persistence every 5 seconds + on seek/pause/stop
- Session persistence for app restart restore

**`service/PlayerController.kt`** — Singleton `MediaController` wrapper. All methods are `suspend` (awaits the controller future). Used exclusively from `PlayerViewModel`.

**`service/PlaybackSessionStorage.kt`** — SharedPreferences-backed JSON storage for the last playback session (queue, index, position, speed, wasPlaying).

---

## Domain Models

```kotlin
// domain/model/Podcast.kt
data class Podcast(val id: String, val title: String, val artist: String,
                   val artworkUrl: String?, val feedUrl: String?)

// domain/model/Episode.kt
data class Episode(val id: String, val podcastId: String, val title: String,
                   val description: String?, val pubDate: Date?,
                   val audioUrl: String, val duration: Long?,
                   val imageUrl: String? = null,
                   val isDownloaded: Boolean = false,
                   val localPath: String? = null)

// domain/model/PlayerState.kt
enum class PlaybackState { IDLE, LOADING, PLAYING, PAUSED, ERROR }
data class PlayerState(val state: PlaybackState, val currentEpisode: Episode?,
                       val currentPosition: Long, val duration: Long, val playbackSpeed: Float)

// domain/model/PodcastQueue.kt
data class PodcastQueue(val id: String, val name: String, val createdAt: Long)
```

---

## UI State Sealed Classes

Defined at the bottom of `PodcastViewModel.kt`:

```kotlin
sealed class PodcastUiState {
    data object Initial : PodcastUiState()
    data object Loading : PodcastUiState()
    data class Success(val podcasts: List<Podcast>) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}

sealed class EpisodesUiState {
    data object Initial : EpisodesUiState()
    data object Loading : EpisodesUiState()
    data class Success(val episodes: List<Episode>) : EpisodesUiState()
    data class Error(val message: String) : EpisodesUiState()
}
```

---

## Persistence Summary

| Storage | Key / File | Contents |
|---|---|---|
| Room DB v2 | `podcast_database` | `downloaded_episodes`, `playback_progress` |
| SharedPreferences | `saved_podcasts` → `podcasts` | JSON list of saved `Podcast` objects |
| SharedPreferences | `podcast_queues` → `queues` | JSON list of `QueuePayload` objects |
| SharedPreferences | `player_session` | JSON of last playback session |

---

## Code Style

### Kotlin Conventions
- **Classes/Sealed classes:** PascalCase (`PodcastViewModel`, `EpisodesUiState`)
- **Functions/Properties:** camelCase (`searchPodcasts`, `currentEpisode`)
- **Constants:** UPPER_SNAKE_CASE (`CHANNEL_ID`, `DATABASE_NAME`)
- **Private backing StateFlow fields:** `_fieldName` (MutableStateFlow), exposed as `fieldName` (StateFlow via `.asStateFlow()`)
- **No "I" prefix on interfaces**
- Prefer `val` over `var`
- 4-space indentation, 120-character line limit
- Trailing commas in multi-line parameters/argument lists
- Explicit imports — no wildcards

### Error Handling
- Use `Result<T>` for repository/data-source operations that can fail
- Use `.fold(onSuccess = …, onFailure = …)` at the call site
- Sealed classes for ViewModel UI states

### Coroutines
- `viewModelScope` for ViewModel-scoped coroutines
- `Dispatchers.IO` for network and disk I/O
- `suspend` for functions that need to await async work
- `StateFlow` for observable state; `Flow` for streams

### Compose
- Screens are stateless Composables; they receive data and lambda callbacks
- Use `ViewModel` for state that must survive recompositions
- Use `remember`/`mutableStateOf` for transient local UI state
- Follow Material3 design principles

---

## Adding Dependencies

Add to `app/build.gradle.kts` under the `dependencies {}` block. Prefer stable/GA versions. Room requires `kapt` for the compiler annotation processor:

```kotlin
implementation("androidx.room:room-runtime:X.Y.Z")
implementation("androidx.room:room-ktx:X.Y.Z")
kapt("androidx.room:room-compiler:X.Y.Z")
```

---

## CI/CD

**GitHub Actions** (`.github/workflows/android-debug-apk.yml`):
- Triggered on `pull_request` to `main` or manually via `workflow_dispatch`
- Builds debug APK with `./gradlew --no-daemon assembleDebug`
- Uploads `app-debug.apk` as a build artifact

---

## Testing

No tests currently exist. When writing tests:

- Unit tests for ViewModels use `MainDispatcherRule` for coroutines
- Use `kotlinx-coroutines-test` for `TestScope`/`runTest`
- Mock repositories and storage with fakes or Mockito
- Test `Result.success`/`Result.failure` branches in repository methods

---

## AndroidManifest Permissions & Services

**Permissions required:**
- `INTERNET` — Network access for search + RSS feeds
- `FOREGROUND_SERVICE` — Background playback
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Media-specific foreground service type
- `POST_NOTIFICATIONS` — Media playback notification

**`PlayerService`** must remain `exported="true"` with the `androidx.media3.session.MediaSessionService` intent filter so Media3 can resolve the `SessionToken` from other processes.

---

## Known Issues / Technical Debt

- No Hilt/Dagger — ViewModels use manual factories; consider Hilt for new features.
- Room schema export disabled (`exportSchema` not configured, triggers kapt warning). Either add `room.schemaLocation` to kapt options or set `exportSchema = false` in `@Database`.
- `usesCleartextTraffic="true"` in the manifest allows HTTP RSS feeds; note the security trade-off.
- No unit test coverage currently.

---

## Feature Specifications

Detailed design specs live in `docs/specs/`:
- `001-build-jdk17.md` — JDK 17 toolchain requirement
- `002-playback-progress.md` — Playback position persistence design
- `003-navigation-compose.md` — Navigation Compose migration
- `004-podcast-queue-play.md` — Podcast queue playback feature

Session notes and architecture decisions are in `docs/spec.md`.
