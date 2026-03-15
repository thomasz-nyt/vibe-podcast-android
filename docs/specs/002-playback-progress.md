# Spec: Playback Position + Listening History

Project: `vibe-podcast-android`

Owner: TBD

Last updated: 2026-01-31

## 1) Summary
Add persistent episode playback progress (resume position) and lightweight listening history (played/partially played) backed by Room. The feature enables:
- Resuming an episode from where the user left off across app restarts.
- Showing listening progress indicators in the episode list.
- Marking episodes as played when nearly completed.

This spec intentionally keeps the data model small and local-only (no cloud sync).

---

## 2) Goals
1. **Persist playback position per episode** (ms) and update it during playback.
2. **Resume playback** when the user replays an episode with saved progress.
3. **Listening status** derived from progress:
   - Not started
   - In progress
   - Played (completed)
4. **Episode list UI indicators**:
   - Progress bar / percent and/or time remaining.
   - Played checkmark and/or dimmed title.
5. **Robustness**:
   - Handles missing duration, streaming vs downloaded audio, and app/service lifecycle changes.
6. **Backward compatible**:
   - Adds Room tables without breaking existing `downloaded_episodes` table.

---

## 3) Non-goals
- Cloud sync across devices.
- Cross-app integration (e.g., Android Auto, Google Podcasts import).
- Full analytics of listening behavior (per-second timeline, skip tracking, etc.).
- Multi-device shared queue/history.
- Server-side storage.

---

## 4) User Stories
1. As a listener, when I reopen an episode I partially listened to, I can **resume** from my last position.
2. As a listener, I can glance at a podcast’s episode list and see **which episodes I already played**.
3. As a listener, I can see **how far I am** through an episode (progress indicator).
4. As a listener, when I finish an episode (or reach near the end), it is automatically **marked played**.

---

## 5) Existing Architecture Touchpoints (current repo)
- Room DB: `PodcastDatabase` (v1) currently has `DownloadedEpisodeEntity` only.
- Playback: `PlayerService` (Media3 `ExoPlayer`) + `PlayerController` and `PlayerViewModel`.
- Episode list: `EpisodeListScreen` renders `EpisodeItem` cards.

---

## 6) Data Model (Room)
### 6.1 New Entity: EpisodePlaybackEntity
Store current playback state per episode (one row per episode id).

```kotlin
@Entity(tableName = "episode_playback")
data class EpisodePlaybackEntity(
    @PrimaryKey val episodeId: String,

    // denormalized for efficient queries from episode list
    val podcastId: String,

    // last known position and duration at time of update
    val positionMs: Long,
    val durationMs: Long?,

    // derived status
    val isCompleted: Boolean,

    // auditing / ordering in UI
    val lastPlayedAtMs: Long, // System.currentTimeMillis()

    // optional counters; keep minimal
    val playCount: Int = 0
)
```

**Notes**
- `durationMs` can be null because RSS duration may be missing and Media3 duration can be `C.TIME_UNSET` early.
- `isCompleted` is a stored flag to avoid recomputing when duration is unknown later.
- `playCount` increments when playback starts for an episode (optional but useful for future sorting).

### 6.2 DAO
```kotlin
@Dao
interface EpisodePlaybackDao {
    @Query("SELECT * FROM episode_playback WHERE episodeId = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): EpisodePlaybackEntity?

    @Query("SELECT * FROM episode_playback WHERE podcastId = :podcastId")
    fun observeByPodcastId(podcastId: String): Flow<List<EpisodePlaybackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EpisodePlaybackEntity)

    @Query("DELETE FROM episode_playback WHERE episodeId = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)

    @Query("UPDATE episode_playback SET positionMs = 0, isCompleted = 1, lastPlayedAtMs = :nowMs WHERE episodeId = :episodeId")
    suspend fun markCompleted(episodeId: String, nowMs: Long)

    @Query("UPDATE episode_playback SET positionMs = 0, isCompleted = 0 WHERE episodeId = :episodeId")
    suspend fun resetProgress(episodeId: String)
}
```

### 6.3 Database changes
Update `PodcastDatabase`:
- Add entity `EpisodePlaybackEntity`.
- Bump version `1 -> 2`.

```kotlin
@Database(
  entities = [DownloadedEpisodeEntity::class, EpisodePlaybackEntity::class],
  version = 2
)
abstract class PodcastDatabase : RoomDatabase() {
  abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
  abstract fun episodePlaybackDao(): EpisodePlaybackDao
}
```

### 6.4 Migration
Add Room migration `MIGRATION_1_2` in `DatabaseProvider`.

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS episode_playback (
        episodeId TEXT NOT NULL,
        podcastId TEXT NOT NULL,
        positionMs INTEGER NOT NULL,
        durationMs INTEGER,
        isCompleted INTEGER NOT NULL,
        lastPlayedAtMs INTEGER NOT NULL,
        playCount INTEGER NOT NULL,
        PRIMARY KEY(episodeId)
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_episode_playback_podcastId ON episode_playback(podcastId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_episode_playback_lastPlayedAtMs ON episode_playback(lastPlayedAtMs)")
  }
}
```

Then:
```kotlin
Room.databaseBuilder(...)
  .addMigrations(MIGRATION_1_2)
  .build()
```

---

## 7) Domain Model / Mapping
### 7.1 UI-facing model
Instead of polluting `Episode` directly, add a small UI wrapper/state (preferred):

```kotlin
data class EpisodePlaybackState(
  val positionMs: Long = 0,
  val durationMs: Long? = null,
  val isCompleted: Boolean = false,
  val lastPlayedAtMs: Long? = null
)
```

Then Episode list screen can map `Episode + EpisodePlaybackState`.

Alternate (acceptable for this app’s size): extend `Episode` with optional `playbackPositionMs`, `isPlayed`, etc. This is simpler but mixes network/domain with local state.

### 7.2 Completed threshold
Use a consistent threshold so completion is stable:
- `COMPLETED_WHEN_REMAINING_MS <= 30_000` (30 seconds remaining)
- and `durationMs != null && durationMs > 0`

Additionally, treat completion if `positionMs / durationMs >= 0.95`.

### 7.3 Resume threshold
Avoid resuming from the first few seconds:
- Resume only if `positionMs >= 10_000` (>= 10s)
- If completed, start from 0 (unless user explicitly chooses “Replay from start”).

---

## 8) Playback Integration
### 8.1 Ensure episode id is available to the player
Currently `PlayerController.playEpisode()` sets metadata but not `mediaId`.

Update to set `MediaItem.mediaId = episode.id` and include extras if needed:
- `podcastId`
- `title`

This makes it possible for the service/controller layer to know which episode is playing.

```kotlin
val mediaItem = MediaItem.Builder()
  .setMediaId(episode.id)
  .setUri(mediaUri)
  .setMediaMetadata(metadata)
  .build()
```

### 8.2 Where to persist progress
Preferred: persist from the **service** so it works while app UI is backgrounded.

Implementation approach:
1. Create `PlaybackProgressUpdater` (or repository) that receives `(episodeId, podcastId, positionMs, durationMs, isCompleted)`.
2. In `PlayerService.initializePlayer()`, add a `Player.Listener`.
3. Update DB:
   - on `onIsPlayingChanged(true)` start periodic updates.
   - on `onPlaybackStateChanged(STATE_ENDED)` mark completed.
   - on `onIsPlayingChanged(false)` persist a final snapshot.

Suggested periodic update interval:
- Every 5 seconds while playing.
- Also persist on seek events (`onPositionDiscontinuity`) to capture manual scrubs.

If service-level update is too invasive, fallback is to persist from `PlayerViewModel.startPositionUpdates()` (currently runs every 1 second), but this will stop when UI/viewmodel is gone.

### 8.3 Resume on play
When user taps an episode in `EpisodeListScreen`:
1. Look up playback entity by `episodeId`.
2. If `isCompleted == true`: default to start from 0.
3. Else if `positionMs >= RESUME_THRESHOLD_MS`: seek to `positionMs` before play.

API change proposal:
- Add optional parameter to `PlayerController.playEpisode(episode, artworkUrl, startPositionMs: Long?)`.
- Implementation:
  - `controller.setMediaItem(mediaItem)`
  - `controller.prepare()`
  - if start position != null: `controller.seekTo(startPositionMs)`
  - `controller.play()`

### 8.4 Handling unknown duration
If duration is unknown:
- Still store position.
- Don’t mark completed automatically on percent.
- Mark completed only on `STATE_ENDED`.

---

## 9) UI/UX Changes
### 9.1 Episode list indicators
In `EpisodeItem` (EpisodeListScreen):
- Add an “In progress” progress bar under metadata if position > 0 and not completed.
- Add a “Played” indicator if completed.

Recommended UI behavior:
- **Played**: show a small check icon and optionally reduce title alpha.
- **In progress**: show linear progress with `position/duration` when duration available; else show “Listening” chip.
- **Resume label**: show text “Resume at 12:34” if position >= threshold.

Data needs:
- A map `episodeId -> EpisodePlaybackState` exposed by a new ViewModel flow.

### 9.2 Resume prompt (optional)
If desired, show a simple confirm dialog when tapping an in-progress episode:
- Title: “Resume?”
- Buttons: “Resume” (default), “Start over”

This is optional; spec supports auto-resume without prompt.

### 9.3 Player screen
- Add “Mark as played” and “Mark as unplayed” actions (overflow menu or button).
  - Mark as played sets completed and position 0.
  - Mark as unplayed resets.

These are helpful for manual corrections.

### 9.4 MiniPlayerBar
Optionally display progress bar in mini player (already has seek UI). Not required for first iteration.

---

## 10) Repository / ViewModel Integration
### 10.1 Repository
Create `EpisodePlaybackRepository` wrapping DAO.

Responsibilities:
- observe playback state for a podcast id.
- get playback for an episode id.
- update progress snapshots.
- mark completed/reset.

### 10.2 PodcastViewModel integration
When `selectPodcast(podcast)` is called, also start observing playback states:
- `episodePlaybackRepository.observeByPodcastId(podcast.id)`
- build a map and expose to UI.

Then in `EpisodeListScreen`, for each `episode` fetch `playbackMap[episode.id]` and render indicators.

### 10.3 PlayerViewModel integration
Add methods:
- `suspend fun resolveResumePosition(episodeId): Long?`
- `fun markPlayed(episodeId)` / `fun markUnplayed(episodeId)`

When `playEpisode()` is invoked:
- Query DB for resume position.
- Call controller with `startPositionMs`.

If service-level persistence is implemented, `PlayerViewModel` only reads state to resume and to reflect UI.

---

## 11) Edge Cases / Rules
- If user seeks near the end (within completion threshold) and then pauses, mark completed.
- If episode is completed and user hits play again, start from 0 unless they explicitly resume (not applicable since completed).
- If audio URL changes (feed update) but episode id stable, progress remains.
- If episode id changes (rare; feed instability), progress cannot be matched—acceptable.

---

## 12) Performance & Storage
- One row per episode listened to; small (< 1 KB each).
- Periodic updates every 5 seconds prevents excessive writes.
- Use `REPLACE` upserts.
- Consider debouncing writes if position changes less than 1 second (optional).

---

## 13) Testing Checklist
### 13.1 Unit tests (JVM)
- `EpisodePlaybackDao`:
  - upsert + get returns same values.
  - observeByPodcastId emits updates.
  - markCompleted/resetProgress updates fields.

### 13.2 Migration tests
- Create v1 DB with `downloaded_episodes` populated.
- Run migration 1→2.
- Verify:
  - `downloaded_episodes` still readable.
  - `episode_playback` table exists.
  - indices exist.

### 13.3 Integration tests
- Playback persistence:
  - Start playing episode, advance position, verify DB updated.
  - Pause/stop, verify final snapshot saved.
  - End playback, verify `isCompleted = true`.

### 13.4 UI tests (Compose)
- Episode list shows:
  - Played indicator for completed.
  - Progress bar for in progress.
  - No indicator when not started.
- Tapping in-progress episode resumes (verify seek invoked; can be faked via repository mock).

### 13.5 Manual QA checklist
- Start playback, background app, wait, foreground: position should reflect.
- Kill app process, relaunch: resume works.
- Streamed episode and downloaded episode both persist progress.
- Unknown duration episodes do not break UI.

---

## 14) Rollout Plan
1. Land DB migration + entity/dao + repository.
2. Land persistence in service (or viewmodel fallback).
3. Land resume behavior.
4. Land episode list indicators.
5. Add manual mark played/unplayed.

---

## 15) Open Questions
- Should the app auto-resume without prompting or show a “Resume/Start over” dialog?
- How should “played” be represented visually (check icon, dim, label)?
- Should we keep a separate history list screen (recently played)? (Out of scope for now.)
