# Spec 005: Landscape (Horizontal) Layout

Last updated: 2026-05-02

## 1) Problem

The app was portrait-only. Rotating to landscape produced a stretched, unusable UI:
- BottomNav wasted vertical space
- PlayerScreen's single-column layout left the artwork tiny and controls cramped
- EpisodeListScreen had no use for the extra horizontal real-estate

## 2) Design Goals

- Replace BottomNav with a side NavRail in landscape
- Show a compact MiniPlayerBar at the bottom of the content area (not full-screen)
- Two-column PlayerScreen: large artwork left, controls right
- Two-column EpisodeListScreen: podcast meta left, episode list right
- Zero code duplication — single NavHost handles both orientations

## 3) Orientation Detection

`LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE` at the `PodcastNavHost` top level. The `isLandscape` boolean is passed as a parameter to screens that have different layouts.

## 4) New Components (`LandscapeLayouts.kt`)

### 4.1 `VibeNavRail`
- 88dp wide column, `fillMaxHeight`, `background(colors.background)`, 1dp `outlineVariant` right border
- "V" brand mark: 36dp box, `RoundedCornerShape(10.dp)`, primary background
- Same `VibeTab` entries as BottomNav; active tab gets `primaryContainer` pill background on the icon

### 4.2 `MiniPlayerBarLandscape`
- 56dp height pill at bottom of content area (`RoundedCornerShape(14.dp)`, 1dp outline border)
- Contents: 44dp artwork → title+time column (weight=1) → Back15 → Play/Pause (42dp accent circle) → Fwd30
- 2dp accent progress line at the very bottom (thin `Box` overlay)

### 4.3 `LandscapeBrowseScaffold` (unused — superceded by inline `Row` in NavHost)
- Kept for reference; wraps NavRail + content column + MiniPlayer overlay

## 5) Scaffold Structure (`PodcastNavHost.kt`)

Single `Scaffold` handles both orientations — no duplicate `NavHost`:

```
Scaffold(
    bottomBar = {
        if (!isLandscape && currentRoute in bottomNavRoutes)
            VibeBottomNav(...)
    }
) { innerPadding ->
    Row(Modifier.fillMaxSize().padding(innerPadding)) {
        if (isLandscape && currentRoute in bottomNavRoutes)
            VibeNavRail(...)

        NavHost(modifier = Modifier.weight(1f)) {
            // all routes — no duplication
        }
    }
}
```

MiniPlayer in landscape is shown inside the player-screen routes via the `PlayerViewModel` state; browse routes continue to use the existing portrait `MiniPlayerBar` at the bottom of each screen's own `Box`.

## 6) PlayerScreen Landscape (`PlayerScreen.kt`)

`isLandscape: Boolean = false` parameter triggers early-return to `PlayerLandscape`:

```
Row(padding start=24, top=32, end=110, bottom=14) {
    Column(width=300dp) {
        CloseButton (36dp, rounded 10dp)
        Artwork (240dp, rounded 18dp, outlined)
    }
    Column(weight=1f) {
        Title (22sp bold, maxLines=2) + podcast name
        Slider scrubber + time labels
        Transport row: Prev(38) + Rewind(44) + Play(60dp accent) + Fwd(44) + Next(38)
        Spacer(weight=1f)
        Speed chip + Sleep chip (VibePill)
    }
}
```

## 7) EpisodeListScreen Landscape (`EpisodeListScreen.kt`)

`isLandscape: Boolean = false` parameter triggers early-return to `EpisodeListLandscape`:

```
Row {
    Column(width=280dp, verticalScroll) {   // left panel
        Back button
        Artwork (180dp)
        Category / Title / Host
        Subscribe toggle
    }
    LazyColumn(weight=1f) {                 // right panel
        "Episodes (N)" header
        EpisodeLandscapeRow items
    }
}
```

`EpisodeLandscapeRow`: 36dp outlined circle play button + title/meta/progress + download icon; no episode artwork (saves horizontal space).

## 8) Files Changed

| File | Change |
|---|---|
| `presentation/ui/LandscapeLayouts.kt` | **New** — `VibeNavRail`, `MiniPlayerBarLandscape`, `LandscapeBrowseScaffold` |
| `presentation/ui/PodcastNavHost.kt` | Orientation detection + single Scaffold+Row pattern |
| `presentation/ui/PlayerScreen.kt` | `isLandscape` param + `PlayerLandscape` composable |
| `presentation/ui/EpisodeListScreen.kt` | `isLandscape` param + `EpisodeListLandscape` + `EpisodeLandscapeRow` composables |
