# Project Beta — Bouldering Video Analysis: v1 (Path A) Design

**Date:** 2026-07-03
**Status:** Approved for planning

## Background

Two business paths were identified for a bouldering movement-analysis product:

- **Path A** — consumer app, limited to hardware everyday users already have (phone camera only, no LiDAR requirement).
- **Path B** — sold as a solution to gyms/coaches, with advanced hardware (LiDAR, possibly multi-camera), server-side course management, and richer features (e.g. hold-level detection via SAM-based segmentation, color/tape-based start detection, "holds the climber is touching" tracking).

This spec covers **Path A only**. The core analysis engine is designed to be hardware-agnostic so that Path B can extend it later (add a depth channel, add hold-awareness) rather than requiring a rewrite.

**Project codename:** Project Beta (internal only — the branded product name will be chosen after market research; "beta" is climbing slang for route/movement information, which is a fitting match for an automated movement-analysis tool).

## Scope (v1 / Path A)

- **Native Android app (Kotlin + CameraX)**. Android-first because that's the hardware the initial builder/user actually has, and it fits Path A's "everyday hardware" framing well — no depth sensor is assumed or required. Native (not React Native/Flutter) because a native iOS app with ARKit LiDAR is already planned for later (see below), and cross-platform buys little when both platforms will eventually need platform-specific native work anyway.
- iOS is a planned future port (native Swift), at which point it also picks up ARKit LiDAR support for the depth-fusion path described below. Order is: Android RGB-only now, iOS (RGB + LiDAR) later.
- Fixed camera capture (tripod or propped phone at the base of the boulder problem) — camera does not move during the climb. This removes the need for camera-motion compensation.
- On-device processing throughout (no backend/server needed for v1).
- Pose estimation via MediaPipe (real-time on-device, ~30+ FPS on mid-range phones, validated in published studies against 3D motion capture for movement tracking; same model/SDK used on both Android and the future iOS build, so this layer doesn't change between platforms).
- Engine accepts an optional depth channel but must gracefully degrade to RGB-only pose estimation when depth is unavailable. On Android v1 this path is effectively always inactive (consumer Android devices don't have a meaningful equivalent to ARKit LiDAR), so v1 is RGB-only in practice — but the pipeline is still built with this seam so the future iOS+LiDAR build is additive, not a rewrite.
- Pure movement analysis — no hold or route detection in v1. Metrics are derived entirely from the climber's body movement over time.
- Post-climb batch report (not real-time overlay). The climber records a full attempt, then receives a report afterward. This gives headroom for model quality and iteration without fighting a real-time frame budget; real-time is a possible v2 feature once metrics are validated.

Out of scope for v1 (explicitly deferred to Path B / later iOS port):
- Hold/route detection (SAM-based segmentation, color/tape start detection, "holds touched" tracking).
- Multi-camera setups.
- Server-side course/session management.
- Real-time on-screen feedback during recording.
- iOS app + LiDAR depth fusion (planned next, after Android v1 is validated).

## Architecture & Data Flow

1. **Capture** — Android CameraX records RGB video. One take per climb attempt, fixed camera position. (Future iOS build: AVFoundation for RGB, plus ARKit's scene depth API for a parallel depth stream on LiDAR-capable devices.)
2. **Calibration** (once per session) — before climbing, the climber taps a known reference (e.g. their own height, or a fixed hold spacing) on screen. Establishes pixel→real-world scale for that camera position. If skipped, the engine falls back to relative units (see Error Handling).
3. **Pose estimation** — MediaPipe runs per-frame on the RGB video, producing joint landmarks (wrists, ankles, hips, shoulders, etc.) with confidence scores. Identical whether or not depth is present.
4. **Depth fusion (optional)** — if a depth stream exists, 2D joint landmarks are projected into 3D using the depth map at each joint's pixel location. If absent, joints remain calibrated 2D pixel coordinates. This is the only stage that branches on hardware; everything downstream is hardware-unaware.
5. **Trajectory builder** — aggregates per-frame joints into a continuous time series: center-of-mass (COM) position over time, per-limb position over time. Applies smoothing (e.g. Savitzky-Golay filter) to reduce per-frame pose jitter.
6. **Metrics engine** (hardware-agnostic, pure time-series math) — consumes the trajectory, outputs the speed curve, stability curve, and crux point(s). See Metrics below.
7. **Report generator** — renders speed/stability graphs plus a skeleton-overlay replay video with the crux segment highlighted.

Steps 6-7 never touch a camera API or know about LiDAR — they consume a trajectory only. This is what makes the Path B upgrade (multi-camera, hold-awareness, depth) additive rather than a rewrite.

## Metrics

### Speed
- Instantaneous speed = `|d(COM)/dt|` from the smoothed trajectory, in real-world units/sec if calibrated, else "body-heights/sec" as a fallback unit.
- Reported as a speed-over-time curve, plus summary stats (average pace, peak speed).

### Stability
An instability score over time, combining:
- **Sway** — variance of COM position on the axis perpendicular to the climb's overall direction (side-to-side wobble while otherwise moving up).
- **Jerk** — rate of change of acceleration (3rd derivative of position); a standard biomechanics smoothness measure. Higher jerk = less controlled movement.
- (Possible v1.1 addition: limb tremor — micro-shake in a limb that should be static while gripping a hold. Not required for v1.)

### Crux point
- A per-moment "difficulty score" = weighted combination of (low speed / long dwell time) + (high instability) + (pause duration before the next move).
- The crux is the local maximum of that score, reported as a **time range/segment**, not a single frame — a crux is a short sequence of moves, not an instant.
- v1 reports the single highest-scoring segment by default.

**Caveat (explicitly flagged and accepted):** the weights combining these signals into a "difficulty score" are a hypothesis, not a validated formula. They require tuning against real climbs — ideally ones where a human coach already agrees on where the crux was — before the output can be trusted. Metric tuning is treated as its own iteration loop after the pipeline works end-to-end, not something to get exactly right on the first pass.

## Error Handling & Edge Cases

- **Occlusion** (limb briefly out of frame or behind the body): interpolate short gaps (a few frames). Longer gaps are flagged as "tracking lost" for that segment rather than fabricating a position.
- **Multiple people in frame** (spotter, adjacent climbers): v1 assumes the largest/most-central detected person is the climber. If two similarly-sized people persist in frame, the ambiguity is flagged rather than silently picking one.
- **No calibration performed**: falls back to relative units (body-heights/sec) — still useful for comparing attempts across sessions, just not in absolute units.
- **Low pose confidence** (motion blur, poor lighting): low-confidence keypoints are dropped from that frame rather than smoothed over. If a large fraction of the video has poor confidence, the report surfaces a warning instead of a confidently-wrong result.

## Testing Strategy

- **Metrics engine**: unit tests against synthetic trajectories (e.g. a hand-crafted "COM slows and wobbles here" time series, asserting the crux detector finds that segment). Fully testable without video/CV involved — this is the natural isolation boundary in the system.
- **Pose/capture integration**: a small set of real reference climbing videos, sanity-checked by eye (does the skeleton track correctly, does the speed curve look plausible) rather than exact-match assertions, since there is no ground truth available without LiDAR/mocap.
- **On-device performance**: processing time and memory per minute of video, since this runs entirely on-device.

## Open Questions for Later Iteration

- Exact weighting formula for the crux "difficulty score" — needs validation against real, coach-labeled climbs.
- Whether limb tremor should be added to the stability score in v1.1.
- iOS port timeline (adding ARKit LiDAR capture + depth fusion) and MediaPipe parity testing between Android and iOS.
