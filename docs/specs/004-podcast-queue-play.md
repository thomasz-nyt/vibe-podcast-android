# Spec 004: Podcast Queue Playback (Play Subscriptions)

Last updated: 2026-01-31

## 1) Problem
- Users want a **quick Play button on Home** to start playing their queue.
- Today, the app’s “Queue” is a **podcast list** (Saved podcasts), not an episode up-next list.
- Today, “Play Queue” does **not** start playback; it just navigates to the Episodes screen for the first podcast.

## 2) Decisions (based on user feedback)
1. **Saved == Subscribed** (for now)
   - We will treat the existing Saved podcasts list as the user’s subscriptions/library.
2. **Queue remains podcast-level** (Option A)
   - Queue is a manually ordered list of subscribed podcasts.
3. **Play Queue behavior**
   - “Play Queue” should play **all unplayed episodes** for each podcast **in the queue order**.
   - Within a podcast, episodes are played in a consistent order (default: **oldest → newest** among unplayed episodes).

## 3) UX changes
### 3.1 Home (Search screen)
- Add a **Play Queue** (play icon) action in the top app bar.
- Enabled when the user has at least 1 saved/subscribed podcast.

### 3.2 Queue screen
- Keep the existing manual reorder/remove UX.
- Update **Play Queue** to start playback rather than just navigating.

## 4) Playback implementation
- Build a flattened episode list:
  1) Iterate podcasts in queue order
  2) Fetch RSS episodes per podcast
  3) Filter out completed episodes using `PlaybackProgressDao` (completed=true)
  4) Append remaining episodes to a single list
- Start playback using a Media3 playlist:
  - `MediaController.setMediaItems(mediaItems)`
  - `prepare()` then `play()`

## 5) Data / filtering rules
- “Unplayed” = no progress row, or `completed=false`.
- Exclude episodes marked completed.
- Partially played episodes are included.

## 6) Non-goals (for this spec)
- Episode-level Up Next queue UI.
- Advanced smart-queue mixing across subscriptions.
- Background refresh / WorkManager.

## 7) Acceptance criteria
- Home shows a Play Queue button when subscriptions exist.
- Queue screen Play Queue starts audio playback.
- Playback continues across multiple episodes (playlist) until exhausted.
- Completed episodes are skipped.
