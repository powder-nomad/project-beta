# Project Beta — Phase 2: Report UI & Climb History Design

**Date:** 2026-07-04
**Status:** Approved for planning

## Background

Phase 1 (complete, merged to `master`) built the core analysis engine: capture (CameraX), calibration, MediaPipe pose estimation, trajectory building, and the speed/stability/crux metrics engine. `AnalysisPipeline.run()` produces an `AnalysisReport` but `CaptureActivity` currently just logs it — there is no UI to view results, and nothing is saved between app runs.

Phase 2 adds the results UI: a report screen (charts + skeleton-overlay video replay) and a persistent history of past climbs, per the original "charts + skeleton-overlay video" ask.

## Scope (Phase 2)

In scope:
- A **Report screen** shown after each recording: summary stats, speed/stability line charts with the crux segment highlighted, and a skeleton-overlay video replay.
- **Local persistence** of every completed analysis (Room database + saved video files), so climbs survive app restarts.
- A **History screen**: a list of past climbs as cards (looped crux-segment video, peak speed, crux difficulty score, sparkline of the speed curve), reachable from the capture screen. Tapping a card reopens its Report screen.

Out of scope (deferred):
- Live/real-time skeleton overlay during recording (this is post-hoc replay only, matching Phase 1's post-climb batch design).
- Report sharing/export (e.g. exporting a shareable .gif or video).
- Any cloud sync, backend, or multi-device history.
- Automatic storage cleanup / retention limits for saved videos.
- Editing or deleting individual history entries (may follow naturally from the data model later, but isn't required now).

## Screens & Navigation

- `CaptureActivity` remains the launcher screen (unchanged entry point), with a new "History" button.
- After a recording finishes and `AnalysisPipeline` runs, the result is persisted, then the app auto-navigates to `ReportActivity` for that climb.
- `HistoryActivity`: a scrollable `RecyclerView` of past-climb cards, opened from `CaptureActivity`'s History button.
- `ReportActivity`: shows one climb's full report (summary stats, charts, skeleton-overlay player). Reachable either right after recording or by tapping a card in `HistoryActivity`.

```
CaptureActivity --(record + analyze)--> ReportActivity
CaptureActivity --(History button)--> HistoryActivity --(tap card)--> ReportActivity
```

## Data & Persistence

- New Room database, package `com.projectbeta.data`, with one `ClimbRecord` entity per completed analysis:
  - Queryable columns: `id`, `recordedAt` (epoch ms), `videoFilePath`, `avgSpeed`, `peakSpeed`, `cruxStartMs`, `cruxEndMs`, `cruxDifficultyScore` — enough for the history list to sort/render cards without deserializing the heavy payload.
  - One JSON blob column, `reportJson`, via `kotlinx.serialization` (JetBrains' official Kotlin serialization library, Apache-2.0), holding the full `AnalysisReport` (trajectory, speed curve, stability curve) **plus** the raw `List<PoseFrame>` needed for the skeleton overlay. `Point3D`, `Joint`, `JointObservation`, `PoseFrame`, and the engine's curve/report data classes gain `@Serializable` annotations.
- Recorded videos are saved to the app's private external files directory (`getExternalFilesDir`), one file per climb, named by climb id.
- `com.projectbeta.engine` stays pure Kotlin with zero Android dependencies, as established in Phase 1. The new `com.projectbeta.data` package depends on the engine's data classes but not vice versa — same dependency direction the pose module already follows.
- `AnalysisPipeline.run()`'s behavior doesn't change. `CaptureActivity` adds one step after it: save the result via a `ClimbRepository` (wraps the Room DAO), then navigate to `ReportActivity(climbId)`.

## Report Screen

- **Skeleton overlay video player**: ExoPlayer playing the saved video, with a transparent custom overlay `View` drawn on top. On each frame tick, the overlay looks up the closest-timestamp `PoseFrame` from the persisted `List<PoseFrame>` and draws the 8 tracked joints as dots + connecting bones (shoulders↔hips, hips↔ankles, shoulders↔wrists), mapped from pose-landmark coordinate space into the view's pixel space. This coordinate-mapping function is pure Kotlin (no Android View dependency) so it can be unit tested directly.
- **Charts**: two MPAndroidChart `LineChart`s (speed curve, stability curve) stacked vertically, Apache-2.0 licensed, x-axis = time. The crux segment's time range is highlighted as a shaded band on both charts. Tapping the highlighted band seeks the video player to the start of that segment.
- **Summary stats**: average speed, peak speed, crux difficulty score, and crux time range shown as text above the charts.

## History Screen

- `RecyclerView` list of `ClimbRecord` cards, newest first. Each card shows:
  - A looped video clip of just the crux segment (`[cruxStartMs, cruxEndMs]` range of the saved video), auto-playing, muted, via ExoPlayer with `REPEAT_MODE_ONE` — not a real `.gif` file, since a repeat-mode video clip gets the same looping effect without needing frame extraction or a GIF encoder.
  - Featured KPIs: peak speed and crux difficulty score.
  - A small sparkline of the speed curve, drawn with a lightweight custom `Canvas` view (not MPAndroidChart — a RecyclerView list of many full chart views is unnecessary overhead for a preview-sized sparkline).
  - Recorded date/time.
- Because ExoPlayer instances are relatively expensive, the adapter keeps a small bounded pool and pauses/releases players for cards scrolled off-screen rather than one live player per row.
- Empty state (no climbs yet) shown when the list is empty.

## Error Handling & Edge Cases

- **Analysis pipeline failure** (e.g. missing pose model, per Phase 1's `IllegalStateException`): caught in `CaptureActivity`, shown as an error dialog, no `ClimbRecord` is created.
- **Missing video file** when opening a history entry (e.g. manually cleared app storage): `ReportActivity`/the history card show a "video unavailable" state instead of crashing; report stats/charts still render since they're stored independently in `reportJson`.
- **Empty history**: `HistoryActivity` shows an empty-state message instead of a blank list.
- **Storage growth**: out of scope for Phase 2 (no auto-cleanup) — flagged as an open question below, not a v1 requirement.
- **Room schema changes**: this is the first schema version; future migrations aren't needed yet, but the `ClimbRepository` boundary means schema evolution stays isolated to `com.projectbeta.data`.

## Testing Strategy

- **Persistence** (`com.projectbeta.data`): Room DAO tests against an in-memory database (`Room.inMemoryDatabaseBuilder`) — insert/query/sort `ClimbRecord`s, verify `reportJson` round-trips through `kotlinx.serialization` correctly (including the pose-frame list).
- **Skeleton overlay coordinate mapping**: pure-Kotlin unit tests for the pose-space → view-space mapping function, independent of any Android `View` — mirrors how the engine module stays unit-testable without Android.
- **Nearest-timestamp pose-frame lookup**: pure-Kotlin unit tests (given a playback position, returns the correct `PoseFrame`).
- **UI-level verification** (chart rendering, ExoPlayer playback, card recycling): no device/emulator exists in this sandbox, same limitation noted in Phase 1 — this will need manual on-device verification once implemented, explicitly called out rather than skipped silently.

## Open Questions for Later Iteration

- Storage retention: videos accumulate with no cleanup in Phase 2; a future phase may need a storage cap or manual delete-from-history action.
- Whether the crux-clip looping video should support export/sharing (would reopen the earlier .gif-vs-video tradeoff for a shareable format).
- Migrating history storage to a queryable multi-field search/filter UI if the list grows large (Room is already used, so this is additive).
