# AGENTS.md

This file contains guidelines for agentic coding agents working on this podcast player Android app.

## Build Commands

```bash
# Build the app
./gradlew assembleDebug

# Build release variant
./gradlew assembleRelease

# Run tests
./gradlew test

# Run unit tests for a specific class
./gradlew test --tests "com.podcastplayer.app.presentation.viewmodel.PodcastViewModelTest"

# Run connected tests on device/emulator
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint

# Run lint for a specific module
./gradlew :app:lint

# Clean build
./gradlew clean

# Build with dependencies check
./gradlew build
```

## Code Style Guidelines

### Kotlin Style

Follow Kotlin's official coding conventions and Android best practices.

#### Naming Conventions

- **Classes**: PascalCase (e.g., `PodcastViewModel`, `DownloadManager`)
- **Functions/Properties**: camelCase (e.g., `searchPodcasts`, `currentEpisode`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `CHANNEL_ID`, `NOTIFICATION_ID`)
- **Private properties**: camelCase with optional underscore prefix for backing fields
- **Interfaces**: Follow same naming as classes, avoid "I" prefix
- **Sealed classes**: PascalCase (e.g., `PodcastUiState`)

```kotlin
class PodcastViewModel {
    private val _uiState = MutableStateFlow(...)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    companion object {
        private const val CHANNEL_ID = "podcast_player_channel"
    }
}
```

#### Imports

- Use explicit imports, avoid wildcards (`import com.example.*`)
- Group imports in order: stdlib, third-party, project
- Android Studio's built-in formatter handles this automatically

```kotlin
import android.app.Notification
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import com.podcastplayer.app.domain.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
```

#### Formatting

- 4-space indentation (no tabs)
- Maximum line length: 120 characters
- Use trailing commas in multi-line parameters/lists
- Put opening brace on same line for functions/classes

```kotlin
fun searchPodcasts(
    query: String,
    limit: Int = 25,
): Result<List<Podcast>> {
    return try {
        // implementation
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

#### Types

- Prefer immutable types (`val` over `var`)
- Use `Result<T>` for functions that can fail
- Use `StateFlow` for reactive state in ViewModels
- Use `suspend` for coroutines

```kotlin
suspend fun searchPodcasts(query: String): Result<List<Podcast>>

private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Initial)
val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()
```

#### Error Handling

- Use `Result<T>` for operations that can fail
- Avoid silent failures - log errors appropriately
- Use sealed classes for UI states

```kotlin
sealed class PodcastUiState {
    data object Initial : PodcastUiState()
    data object Loading : PodcastUiState()
    data class Success(val podcasts: List<Podcast>) : PodcastUiState()
    data class Error(val message: String) : PodcastUiState()
}
```

### Compose Guidelines

- Use `@Composable` functions for reusable UI components
- Follow Material3 design principles
- Use `remember` for state that doesn't need to be shared
- Use `mutableStateOf` for local component state
- Use `ViewModel` for state that needs to persist across recompositions

```kotlin
@Composable
fun PodcastItem(
    podcast: Podcast,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // content
    }
}
```

### Repository Pattern

All data access should go through repositories:
- `PodcastRepository`: Handles API calls and RSS parsing
- `DownloadManager`: Handles offline episode storage
- Use `Result<T>` for repository functions

### Service Guidelines

- Services must be declared in `AndroidManifest.xml`
- Foreground services require notification
- Use Media3's `MediaSessionService` for media playback

## Architecture

Follow MVVM with Repository pattern:
- **ViewModel**: Manages UI state and business logic
- **Repository**: Abstracts data sources
- **Domain models**: Plain data classes
- **UI**: Stateless Composable functions

## Testing

Write unit tests for:
- ViewModels (use `MainDispatcherRule` for coroutines)
- Repository methods
- Utility functions

## Dependencies

Add new dependencies to `app/build.gradle.kts`. Prefer stable versions.
