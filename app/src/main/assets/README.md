# Assets

This directory must contain the MediaPipe pose landmarker model file:

```
pose_landmarker_full.task
```

`MediaPipePoseEstimator` loads this file by name via
`BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task")`, and MediaPipe
resolves that path relative to the app's Android assets. **The app will not run —
`PoseLandmarker.createFromOptions` will fail — until this file is present here.**

## Where to get it

Download from Google's MediaPipe model zoo (search "MediaPipe Pose Landmarker
models"). There are three size/accuracy variants:

- `pose_landmarker_lite.task`
- `pose_landmarker_full.task` — matches the filename already referenced in code
- `pose_landmarker_heavy.task`

Download `pose_landmarker_full.task` and place it in this directory
(`app/src/main/assets/pose_landmarker_full.task`) before building/running the app.

This binary model file is intentionally not checked into version control here since
it could not be fetched in this environment (no internet/binary-asset access); it
must be added by whoever next builds and runs the app.
