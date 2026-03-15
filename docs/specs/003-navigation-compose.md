# Spec 003: Replace manual routing with Navigation Compose

## Context
`PodcastNavHost` currently uses a `var currentScreen by remember { mutableStateOf("search") }` and a `when (currentScreen)` to switch between:
- `search` (PodcastListScreen)
- `episodes` (EpisodeListScreen)
- `queue` (QueueScreen)
- `player` (PlayerScreen)

Back behavior is implemented via a custom `BackHandler`.

This works, but it blocks common navigation capabilities:
- real back stack (state restoration, deep links)
- typed/validated route arguments
- easier feature expansion (settings, downloads, etc.)

## Goals
- Replace manual routing with **Navigation Compose** (`NavHost` + `NavController`).
- Preserve existing UX/back behavior.
- Keep changes incremental and low-risk.

## Non-goals
- Large-scale architecture rewrite (DI/Hilt) as part of this change.
- Adding deep links immediately (we’ll leave the graph ready for it).

## Proposed routes
Use a small sealed routes object + helpers:

```kotlin
object Routes {
  const val SEARCH = "search"
  const val QUEUE = "queue"
  const val PLAYER = "player"

  const val EPISODES = "episodes/{podcastId}"
  const val ARG_PODCAST_ID = "podcastId"

  fun episodes(podcastId: String) = "episodes/${Uri.encode(podcastId)}"
}
```

### Notes on IDs
`podcastId` is currently derived from iTunes results and used throughout the app. If it can contain characters that break path segments, we must `Uri.encode()` when navigating and `Uri.decode()` when reading args.

## Navigation graph
```kotlin
NavHost(navController, startDestination = Routes.SEARCH) {
  composable(Routes.SEARCH) { /* PodcastListScreen */ }

  composable(
    route = Routes.EPISODES,
    arguments = listOf(navArgument(Routes.ARG_PODCAST_ID) { type = NavType.StringType })
  ) { entry ->
    val podcastId = Uri.decode(entry.arguments?.getString(Routes.ARG_PODCAST_ID)!!)
    /* EpisodeListScreen */
  }

  composable(Routes.QUEUE) { /* QueueScreen */ }
  composable(Routes.PLAYER) { /* PlayerScreen */ }
}
```

## Back behavior mapping
Current behavior:
- From `player` → back goes to `episodes`
- From `episodes` → back goes to `search`
- From `queue` → back goes to `search`

With Navigation Compose:
- `navController.popBackStack()` naturally returns to the previous destination.
- Ensure navigation actions build the same stack:
  - Search → Episodes: `navigate(Routes.episodes(podcastId))`
  - Episodes → Player: `navigate(Routes.PLAYER)`
  - Search → Queue: `navigate(Routes.QUEUE)`

No special-case `BackHandler` should be needed after migration.

## ViewModel scoping
We currently create both ViewModels inside `PodcastNavHost` via factories:
- `PodcastViewModel` (repo, download manager, saved storage)
- `PlayerViewModel` (PlayerController)

To avoid behavior changes, keep the same scope:
- instantiate ViewModels in the top-level `PodcastNavHost` (outside individual composables)
- pass them into each destination

This keeps playback/search state stable across navigation.

## Incremental migration plan
1. Introduce `NavController` + `NavHost` and create 1-to-1 composables for each existing screen.
2. Remove `currentScreen` string state.
3. Remove the custom `BackHandler`.
4. Add minimal tests (optional): route building/encoding.

## Acceptance criteria
- App builds and runs.
- Navigation flows match current behavior:
  - Search → Episodes → Player → back → Episodes → back → Search
  - Search → Queue → back → Search
- ViewModels remain stable (no re-creation when navigating).

## Testing checklist
- [ ] Manual: navigate between all screens and confirm back stack
- [ ] Manual: rotate device / process death (adb) and confirm navigation restores reasonably
- [ ] Ensure podcastId encoding doesn’t break episode navigation
