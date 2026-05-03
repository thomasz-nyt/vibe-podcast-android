# Podcast Player Android App

A simple podcast player app built with Kotlin and Jetpack Compose.

## Features

- **Podcast Discovery**: Search and browse podcasts using the iTunes Search API
- **RSS Feed Parsing**: Parse podcast RSS feeds to get episode listings
- **Media Playback**: Full-featured audio player using ExoPlayer (Media3)
- **Offline Playback**: Download episodes for offline listening
- **Background Play**: Foreground service with media session for background playback
- **Player Controls**: Play/pause, seek, playback speed control
- **Add from URL**: Paste / share / type a YouTube or X (Twitter) URL and save the
  audio (MP3) or video (MP4) for fully offline playback. Powered by yt-dlp via
  [`youtubedl-android`](https://github.com/yausername/youtubedl-android).

## Architecture

The app follows MVVM architecture with a repository pattern:

```
app/src/main/java/com/podcastplayer/app/
├── data/
│   ├── local/           # Room database for downloaded episodes
│   ├── remote/          # iTunes API and RSS parser
│   └── repository/      # Data repositories
├── domain/
│   └── model/           # Domain models (Podcast, Episode, PlayerState)
├── presentation/
│   ├── viewmodel/       # ViewModels for UI state
│   └── ui/              # Compose UI screens
└── service/             # Media player service
```

## Technologies

- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM with Repository Pattern
- **Async**: Kotlin Coroutines + Flow
- **Networking**: Retrofit + iTunes Search API
- **Media**: ExoPlayer (Media3)
- **Database**: Room for offline episodes
- **Image Loading**: Coil

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Kotlin 1.9.20
- Android SDK 34
- Min SDK 26

### Build

```bash
./gradlew assembleDebug
```

### JDK

This project expects **JDK 17**. If your system `java` is newer and Gradle fails early, set `JAVA_HOME` to a JDK 17 install (or use a version manager like mise/asdf via `.tool-versions`).


### Install

```bash
./gradlew installDebug
```

## Usage

1. **Search**: Enter a podcast name or topic in the search bar
2. **Select**: Tap on a podcast to view its episodes
3. **Play**: Tap on an episode to start playback
4. **Controls**: Use the player controls to pause, seek, and adjust playback speed

## Project Structure

### Data Layer

- `iTunesApi.kt`: Retrofit interface for iTunes Search API
- `RssParser.kt`: XML parser for podcast RSS feeds
- `PodcastRepository.kt`: Repository for podcast and episode data
- `DownloadManager.kt`: Manages episode downloads and offline storage
- `PodcastDatabase.kt`: Room database for downloaded episodes

### Domain Layer

- `Podcast.kt`: Podcast model
- `Episode.kt`: Episode model
- `PlayerState.kt`: Player state and playback state

### Presentation Layer

- `PodcastViewModel.kt`: Manages podcast search and episode list state
- `PodcastListScreen.kt`: Search and browse podcasts
- `EpisodeListScreen.kt`: View and select episodes
- `PlayerScreen.kt`: Full-screen player with controls

### Service Layer

- `PlayerService.kt`: Media session service for background playback
- `PlayerController.kt`: Interface to control the player service
- `UrlDownloadService.kt`: Foreground service that drives yt-dlp downloads for
  the "Add from URL" feature, surfacing progress in the notification shade

### Add-from-URL feature (issue #33)

The "Add from URL" flow lets the user feed an external video/audio link from
YouTube or X into the same offline-first playback pipeline as podcast episodes.

Three converged entry points all open the same screen:

1. **Share intent** — share-to-app from YouTube / X (or any app that emits a
   text/plain URL).
2. **Paste in search** — pasting a recognized link into the Search screen
   surfaces a "Save offline" affordance above the results.
3. **Dedicated button** — "Add from URL" chip on the Home screen.

Pipeline:

- `UrlDownloadRepository` extracts metadata via `yt-dlp --dump-json`.
- The user picks audio (MP3, extracted via ffmpeg) or video (MP4, h264+aac merged).
- A row is inserted into Room (`url_downloads` table) and `UrlDownloadService`
  drains the queue serially, posting progress updates back through the repo.
- Completed items appear in a "Saved from URL" section on the home screen and
  play through the existing Media3 player. Video items render via a `PlayerView`
  surface in the player screen; audio items reuse the artwork view.

> ⚠️ **Personal/internal use.** YouTube and X Terms of Service generally prohibit
> unauthorized downloading. Distribution through the Play Store is **not**
> recommended for builds with this feature enabled.

## API Endpoints

### iTunes Search API
```
GET https://itunes.apple.com/search?term={query}&media=podcast&limit=25
```

## Dependencies

See `app/build.gradle.kts` for the full list of dependencies.

## License

MIT License
