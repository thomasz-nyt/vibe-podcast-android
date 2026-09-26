# Morning playback and automatic queue downloads

Implementation scope: regular RSS feeds, offline queues, controller/session recovery, and durable automatic downloads.
The YouTube download path keeps its existing transfer implementation.

## Behavior

- All three Play Queue entry points use the same structured queue result. Selection remains the newest unfinished
  episode per show, in queue order. A readable download for that exact identity takes priority over its RSS enclosure.
- A successful feed parse atomically replaces a Room snapshot identified by show and feed URL. A failed refresh keeps
  the previous snapshot. Offline/cached playback requires the selected episode's readable file; unavailable shows and
  saved-feed timestamps are reported in the queue result dialog. An older download is never substituted.
- Controller connections can be rebuilt after disconnection, and video surfaces observe the current controller.
  A request has a 10-second connection timeout, a 30-second preparation/buffering timeout, and at most one automatic
  recovery attempt. User selection, pause, and navigation invalidate previous work. Retry keeps the playlist,
  position, speed, and original RSS URLs. Startup waits for initialization instead of sleeping for two seconds.
- Before 08:30, the Morning queue starts only if initialization leaves playback inactive and no newer user action
  has occurred. Natural queue advancement completes the outgoing episode; manual skipping does not.
- Enabling downloads or adding a show to an enabled queue requests an immediate durable check. Foreground entry
  checks the six-hour freshness threshold. Periodic work is updated to six hours. Both scans and automatic transfers
  honor the saved cellular preference. WorkManager still controls exact background execution time.
- RSS automatic downloads use the existing Room request table and WorkManager worker, with bounded concurrency,
  exponential backoff for transient failures, and retained terminal failures. Manual ownership takes priority.
  Queue UI reports checking, waiting, downloading, completed, failed, and the last successful check, with Retry/Dismiss.
- Migration 5 → 6 adds feed snapshots and an origin column defaulting to MANUAL. Existing request IDs, unique worker
  names, download metadata, and progress are preserved. Retention protects manual and current/upcoming playback items.

## Verification

Local verification uses JVM tests, Android lint, and connected-test compilation. APK builds and API 34 connected
execution run in the existing GitHub Actions workflow, per the user's instruction to build APKs on GitHub.

Regression coverage includes delayed initialization and queue construction, superseded/null preparation, buffering
exhaustion and Retry, navigation errors, pause cancellation, disconnected connections, connection timeouts, exact
local queue selection, cold offline snapshots, missing files, mixed queues, retry policy, and unchanged ordering.
Connected tests cover schema 5 → 6 data preservation, snapshot persistence after reopening the database, failed-refresh
retention, WorkManager constraints and cadence, ownership/deduplication, interrupted enqueue reconciliation, retained
failures, retention protection, real Media3 service recreation with a generated local WAV, and natural versus manual
queue transitions.

The original morning spinner has not been reproduced on the user's device. CI lifecycle coverage and its actual
outcome must be reported separately from that original symptom. No local APK build is required for this handoff.

## References

- [Media3 controller connection lifecycle](https://developer.android.com/media/media3/session/connect-to-media-app)
- [WorkManager requests, constraints, periodic work, and backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Testing worker implementations](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl)
