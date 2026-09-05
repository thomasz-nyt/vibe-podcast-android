# 007 — First Reliability Milestone

**Status:** Approved for implementation  
**Decision date:** 2026-09-04  
**Baseline:** `d7e745913e95782467bb1f1beee3fcbf417a3841`  
**Scope:** navigation reachability, media safety, transfer visibility, and offline queue behavior

## 1. Goal

Make daily listening simpler and safer without a visual rebrand or framework rewrite. The milestone
keeps the app's current product shape, moves common actions beside the content they affect, and
makes offline/delete behavior trustworthy before more playback features are added.

## 2. Product contract

### 2.1 Navigation

Keep four top-level destinations:

1. Home
2. Search
3. Queue
4. Downloads

Settings is not a fifth tab. A consistently placed global Settings gear is available from every
root screen and show detail. Settings returns to the route that opened it.

One application shell owns root navigation, insets, bottom content reservation, and the mini-player.
The mini-player is available during ordinary browsing, including Downloads and Settings, and is
hidden only by the full player. Back returns to the actual caller; it must not infer a destination
from the last browsed podcast.

### 2.2 Queue semantics

A named Queue remains an ordered list of shows. Play Queue selects exactly one newest non-completed
episode per show, in saved show order. A partially played newest episode remains eligible. Undated
episodes rank below dated episodes, with feed order retained when all candidates are undated.

Do not silently redefine Queue as an episode-level Up next list. The active Media3 playlist may be
described as Up next where it is exposed.

### 2.3 Deleting current or upcoming media

Deleting media used by current or upcoming playback requires a decision before the file is removed:

- Current item: **Stop playback and delete** or **Keep file**.
- Upcoming item: **Remove from Up next and delete** or **Keep file**.
- **Keep file** cancels deletion and leaves both the library entry and payload intact.

After a stop/remove decision, physical deletion and any required Android system consent complete
before the database row is forgotten. Denied consent or provider failure keeps the row and file and
reports the outcome. Bulk removal and automatic retention use the same verified result contract.

### 2.4 Offline Queue

A successful feed refresh persists a timestamped snapshot of playable episode metadata. When live
freshness cannot be established, Play Queue uses that snapshot to select the newest eligible episode
under the same rules as online playback.

For every selected episode, playback then resolves that exact episode to a readable local payload:

- If the selected cached-newest episode is available locally, play it.
- If it is not available locally, skip that show and explain why.
- Do not silently substitute an older downloaded episode.
- If no snapshot exists, report that show as unavailable offline.

The UI displays snapshot age/last-updated information so cached selection is not presented as live
feed freshness.

## 3. Milestone sequence

1. Verification contract and CI gates.
2. Canonical media references and honest payload availability.
3. Transactional, playback-aware deletion.
4. Request-bound Add RSS / Save from URL workflows.
5. Shared four-tab shell, global Settings, and origin-preserving Back.
6. One persistent mini-player, including Downloads.
7. Queue-local show management and stable-identity reorder.
8. Portrait/landscape show-detail parity with contextual actions.
9. Unified Waiting / In progress / Needs attention / Downloaded activity.
10. Persistent feed snapshots and offline Queue playback.

This sequence is the repository-level dependency contract. Each PR remains independently reviewable
and updates the relevant specifications with its implemented behavior.

## 4. Verification contract

Every pull request runs independent CI jobs for:

- JVM unit tests (`testDebugUnitTest`)
- Android lint (`lintDebug`)
- debug and release assembly (`assembleDebug assembleRelease`)
- API 34 connected instrumentation tests (`connectedDebugAndroidTest`)

Reports are uploaded after success or failure unless the run is cancelled. Build artifacts are uploaded
only after successful assembly. Storage and migration PRs must document the applicable manual API
28/29/34 checks prompted by the pull-request template; CI green does not replace those checks.

## 5. Milestone acceptance

- Settings is one tap from each root destination and show detail.
- Downloads can pause or reopen current playback in one tap.
- Search query/list state and player origin survive navigation round trips.
- Add-flow confirmation always matches the visible input and latest request.
- Queue shows can be added in Queue; reorder is stable with unavailable IDs.
- Portrait and landscape show detail expose the same state and actions.
- Download activity shows queued, running, failed, and completed RSS/URL work with truthful
  Cancel/Retry actions.
- File deletion never forgets a row before deletion or consent succeeds.
- Restore and cleanup compare canonical media identity, not URI spelling.
- Cold offline Queue uses timestamped cached-newest selections and valid local payloads without
  changing show order or substituting older downloads.

## 6. Standing constraints

- Retain min SDK 26 and target/compile SDK 34 during this milestone.
- Preserve durable manual RSS WorkManager jobs, URL audio/video downloads, conservative restore
  matching, and AUTO-vs-manual retention behavior.
- Personal/internal distribution remains the working model. Play Store policy and APK-size reduction
  are not milestone gates.
- Morning auto-start remains unchanged unless a race fix requires touching it; newer explicit user
  intent must win.

## 7. Non-goals

This milestone does not add cloud sync, accounts, AI summaries, Android Auto browsing, Cast, PiP,
episode-level queue editing, HTTP Range resume, a global storage quota, or a broad
dependency/toolchain upgrade. Full episode-identity and OPML remapping migrations remain separate
work.
