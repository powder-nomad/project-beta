# Project Beta — Phase 1: Core Analysis Pipeline (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working, on-device Android pipeline that takes a fixed-camera recorded bouldering climb video plus a one-time calibration tap, and produces an `AnalysisReport` containing the speed curve, stability curve, and crux segment — proving out the full engine end-to-end before investing in report UI (charts, skeleton-overlay video export), which is a separate follow-up plan.

**Architecture:** A pure-Kotlin, hardware-agnostic core (`Point3D`/`PoseFrame`/`Trajectory` data model → `TrajectoryBuilder` → `MetricsEngine`) that never touches Android/CameraX/MediaPipe APIs directly, wired together by an `AnalysisPipeline` orchestrator that depends only on a `PoseEstimator` interface. This mirrors the spec's layering: the analysis logic (steps 5-6 of the pipeline) must be unaware of where pose data came from, so it can be fully unit-tested on the JVM without a device or camera.

**Tech Stack:** Kotlin, Android CameraX (capture), MediaPipe Tasks Vision `PoseLandmarker` (pose estimation), JUnit 5 (unit tests), Gradle (Kotlin DSL).

## Global Constraints

- Native Android app (Kotlin), not cross-platform — per spec decision, iOS+LiDAR is a planned native port later, not this phase.
- Fixed camera only — no camera-motion compensation logic anywhere in this pipeline.
- On-device processing only — no network calls, no backend.
- Depth channel is optional and unused in this phase (no consumer Android device has usable depth hardware) — `PoseFrame` and `TrajectoryBuilder` must accept a nullable depth value per joint so the seam exists, even though Phase 1 always passes `null`.
- Pure movement analysis only — no hold/route detection anywhere in this phase.
- Batch processing only — no real-time overlay requirement in this phase.
- Low-confidence pose keypoints must be dropped, not smoothed over (per spec Error Handling).
- Occlusion gaps: interpolate short gaps (≤5 frames); longer gaps must be flagged, not fabricated.
- No calibration performed → fall back to relative units (body-heights/sec), never silently treat uncalibrated pixel distance as real-world units.

---

## File Structure

```
app/
  build.gradle.kts                                          # deps: CameraX, MediaPipe Tasks Vision, JUnit5, coroutines
  src/main/java/com/projectbeta/engine/
    Point3D.kt                                               # Task 2
    PoseFrame.kt                                              # Task 2
    Trajectory.kt                                             # Task 2
    TrajectoryBuilder.kt                                       # Task 3
    MetricsEngine.kt                                          # Tasks 4, 5, 6
    AnalysisReport.kt                                          # Task 6
    CalibrationCalculator.kt                                   # Task 9
  src/main/java/com/projectbeta/pose/
    PoseEstimator.kt                                          # Task 7 (interface + joint mapping)
    MediaPipePoseEstimator.kt                                  # Task 7 (real implementation)
  src/main/java/com/projectbeta/pipeline/
    AnalysisPipeline.kt                                       # Task 8
  src/main/java/com/projectbeta/capture/
    CaptureActivity.kt                                        # Task 10
    CalibrationOverlayView.kt                                  # Task 10
  src/test/java/com/projectbeta/engine/
    Point3DTest.kt                                            # Task 2
    TrajectoryBuilderTest.kt                                   # Task 3
    MetricsEngineSpeedTest.kt                                  # Task 4
    MetricsEngineStabilityTest.kt                               # Task 5
    MetricsEngineCruxTest.kt                                    # Task 6
    CalibrationCalculatorTest.kt                                # Task 9
  src/test/java/com/projectbeta/pose/
    PoseEstimatorMappingTest.kt                                 # Task 7
  src/test/java/com/projectbeta/pipeline/
    AnalysisPipelineTest.kt                                     # Task 8
```

Rationale for the split: `engine/` is pure Kotlin (JVM-testable, no Android dependency) — this is the core IP and the part the spec explicitly calls out as needing validation. `pose/` isolates the one Android/MediaPipe-dependent boundary behind an interface so `engine/` and `pipeline/` never import Android or MediaPipe types. `capture/` is UI/CameraX glue with no meaningful unit-test surface, verified manually per the spec's testing strategy.

---

## Task 1: Project Scaffold

**Files:**
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: a Gradle Android app module named `app`, package `com.projectbeta`, min SDK 26 (CameraX requirement), with dependencies available for all later tasks.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "project-beta"
include(":app")
```

- [ ] **Step 2: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.projectbeta"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.projectbeta"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:label="Project Beta"
        android:allowBackup="false">
        <activity
            android:name=".capture.CaptureActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (no source files yet besides the manifest, so this just validates Gradle/plugin wiring).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: scaffold Android project with CameraX and MediaPipe dependencies"
```

---

## Task 2: Core Data Model (`Point3D`, `PoseFrame`, `Trajectory`)

**Files:**
- Create: `app/src/main/java/com/projectbeta/engine/Point3D.kt`
- Create: `app/src/main/java/com/projectbeta/engine/PoseFrame.kt`
- Create: `app/src/main/java/com/projectbeta/engine/Trajectory.kt`
- Test: `app/src/test/java/com/projectbeta/engine/Point3DTest.kt`

**Interfaces:**
- Produces:
  - `data class Point3D(val x: Double, val y: Double, val z: Double = 0.0)` with `operator fun minus`, `operator fun plus`, `fun distanceTo(other: Point3D): Double`.
  - `enum class Joint { LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, LEFT_WRIST, RIGHT_WRIST, LEFT_ANKLE, RIGHT_ANKLE }`
  - `data class JointObservation(val joint: Joint, val position: Point3D, val confidence: Double, val hasDepth: Boolean)`
  - `data class PoseFrame(val timestampMs: Long, val joints: List<JointObservation>)`
  - `data class TrajectoryPoint(val timestampMs: Long, val centerOfMass: Point3D)`
  - `data class Trajectory(val points: List<TrajectoryPoint>, val units: DistanceUnit)`
  - `enum class DistanceUnit { METERS, BODY_HEIGHTS }`

- [ ] **Step 1: Write the failing test for `Point3D`**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class Point3DTest {
    @Test
    fun `subtracting two points gives the vector between them`() {
        val a = Point3D(3.0, 4.0, 0.0)
        val b = Point3D(0.0, 0.0, 0.0)
        val result = a - b
        assertEquals(3.0, result.x, 1e-9)
        assertEquals(4.0, result.y, 1e-9)
    }

    @Test
    fun `distanceTo computes euclidean distance`() {
        val a = Point3D(3.0, 4.0, 0.0)
        val b = Point3D(0.0, 0.0, 0.0)
        assertEquals(5.0, a.distanceTo(b), 1e-9)
    }

    @Test
    fun `adding two points sums components`() {
        val a = Point3D(1.0, 2.0, 3.0)
        val b = Point3D(4.0, 5.0, 6.0)
        val result = a + b
        assertEquals(Point3D(5.0, 7.0, 9.0), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.Point3DTest"`
Expected: FAIL — compilation error, `Point3D` is unresolved.

- [ ] **Step 3: Implement `Point3D.kt`**

```kotlin
package com.projectbeta.engine

import kotlin.math.sqrt

data class Point3D(val x: Double, val y: Double, val z: Double = 0.0) {
    operator fun minus(other: Point3D): Point3D =
        Point3D(x - other.x, y - other.y, z - other.z)

    operator fun plus(other: Point3D): Point3D =
        Point3D(x + other.x, y + other.y, z + other.z)

    fun distanceTo(other: Point3D): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
```

- [ ] **Step 4: Implement `PoseFrame.kt`**

```kotlin
package com.projectbeta.engine

enum class Joint {
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_HIP, RIGHT_HIP,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_ANKLE, RIGHT_ANKLE
}

data class JointObservation(
    val joint: Joint,
    val position: Point3D,
    val confidence: Double,
    val hasDepth: Boolean
)

data class PoseFrame(
    val timestampMs: Long,
    val joints: List<JointObservation>
)
```

- [ ] **Step 5: Implement `Trajectory.kt`**

```kotlin
package com.projectbeta.engine

enum class DistanceUnit { METERS, BODY_HEIGHTS }

data class TrajectoryPoint(
    val timestampMs: Long,
    val centerOfMass: Point3D
)

data class Trajectory(
    val points: List<TrajectoryPoint>,
    val units: DistanceUnit
)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.Point3DTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/Point3D.kt \
        app/src/main/java/com/projectbeta/engine/PoseFrame.kt \
        app/src/main/java/com/projectbeta/engine/Trajectory.kt \
        app/src/test/java/com/projectbeta/engine/Point3DTest.kt
git commit -m "feat: add core geometry and pose data model"
```

---

## Task 3: `TrajectoryBuilder` (pose frames → smoothed COM trajectory)

**Files:**
- Create: `app/src/main/java/com/projectbeta/engine/TrajectoryBuilder.kt`
- Test: `app/src/test/java/com/projectbeta/engine/TrajectoryBuilderTest.kt`

**Interfaces:**
- Consumes: `PoseFrame`, `JointObservation`, `Point3D`, `Trajectory`, `TrajectoryPoint`, `DistanceUnit` (Task 2).
- Produces:
  - `object TrajectoryBuilder { fun build(frames: List<PoseFrame>, scaleMetersPerUnit: Double?, minConfidence: Double = 0.5, smoothingWindow: Int = 5): Trajectory }`
  - Center of mass per frame = average position of all joints in that frame with `confidence >= minConfidence`; frames with zero qualifying joints are skipped (this realizes the spec's "drop low-confidence keypoints" / "flag tracking lost" behavior — a skipped frame is the flag).
  - Smoothing = simple centered moving average over `smoothingWindow` frames (chosen over Savitzky-Golay for Phase 1 implementation simplicity; same "reduce pose jitter" intent from the spec).
  - If `scaleMetersPerUnit` is non-null, COM coordinates are multiplied by it and `units = DistanceUnit.METERS`; if null, coordinates stay in raw pixel-normalized units and `units = DistanceUnit.BODY_HEIGHTS` (the fallback-to-relative-units behavior from the spec's Error Handling section).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrajectoryBuilderTest {
    private fun frame(timestampMs: Long, y: Double, confidence: Double = 0.9) = PoseFrame(
        timestampMs = timestampMs,
        joints = listOf(
            JointObservation(Joint.LEFT_HIP, Point3D(0.0, y, 0.0), confidence, hasDepth = false),
            JointObservation(Joint.RIGHT_HIP, Point3D(0.0, y, 0.0), confidence, hasDepth = false)
        )
    )

    @Test
    fun `builds one trajectory point per frame using average joint position`() {
        val frames = listOf(frame(0, 1.0), frame(33, 2.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, smoothingWindow = 1)
        assertEquals(2, trajectory.points.size)
        assertEquals(1.0, trajectory.points[0].centerOfMass.y, 1e-9)
        assertEquals(2.0, trajectory.points[1].centerOfMass.y, 1e-9)
    }

    @Test
    fun `frames with only low-confidence joints are dropped, not fabricated`() {
        val frames = listOf(
            frame(0, 1.0, confidence = 0.9),
            frame(33, 5.0, confidence = 0.1),
            frame(66, 3.0, confidence = 0.9)
        )
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, minConfidence = 0.5, smoothingWindow = 1)
        assertEquals(2, trajectory.points.size)
        assertEquals(0L, trajectory.points[0].timestampMs)
        assertEquals(66L, trajectory.points[1].timestampMs)
    }

    @Test
    fun `applies real-world scale and reports meters when calibrated`() {
        val frames = listOf(frame(0, 1.0), frame(33, 1.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = 2.0, smoothingWindow = 1)
        assertEquals(DistanceUnit.METERS, trajectory.units)
        assertEquals(2.0, trajectory.points[0].centerOfMass.y, 1e-9)
    }

    @Test
    fun `falls back to body-heights unit when no calibration provided`() {
        val frames = listOf(frame(0, 1.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, smoothingWindow = 1)
        assertEquals(DistanceUnit.BODY_HEIGHTS, trajectory.units)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.TrajectoryBuilderTest"`
Expected: FAIL — `TrajectoryBuilder` unresolved.

- [ ] **Step 3: Implement `TrajectoryBuilder.kt`**

```kotlin
package com.projectbeta.engine

object TrajectoryBuilder {
    fun build(
        frames: List<PoseFrame>,
        scaleMetersPerUnit: Double?,
        minConfidence: Double = 0.5,
        smoothingWindow: Int = 5
    ): Trajectory {
        val rawPoints = frames.mapNotNull { frame ->
            val qualifying = frame.joints.filter { it.confidence >= minConfidence }
            if (qualifying.isEmpty()) return@mapNotNull null
            val avg = Point3D(
                x = qualifying.sumOf { it.position.x } / qualifying.size,
                y = qualifying.sumOf { it.position.y } / qualifying.size,
                z = qualifying.sumOf { it.position.z } / qualifying.size
            )
            TrajectoryPoint(frame.timestampMs, avg)
        }

        val smoothed = smooth(rawPoints, smoothingWindow)

        val scaled = if (scaleMetersPerUnit != null) {
            smoothed.map {
                it.copy(
                    centerOfMass = Point3D(
                        it.centerOfMass.x * scaleMetersPerUnit,
                        it.centerOfMass.y * scaleMetersPerUnit,
                        it.centerOfMass.z * scaleMetersPerUnit
                    )
                )
            }
        } else {
            smoothed
        }

        val units = if (scaleMetersPerUnit != null) DistanceUnit.METERS else DistanceUnit.BODY_HEIGHTS
        return Trajectory(scaled, units)
    }

    private fun smooth(points: List<TrajectoryPoint>, window: Int): List<TrajectoryPoint> {
        if (window <= 1 || points.size < 2) return points
        val half = window / 2
        return points.indices.map { i ->
            val start = (i - half).coerceAtLeast(0)
            val end = (i + half).coerceAtMost(points.size - 1)
            val slice = points.subList(start, end + 1)
            val avg = Point3D(
                x = slice.sumOf { it.centerOfMass.x } / slice.size,
                y = slice.sumOf { it.centerOfMass.y } / slice.size,
                z = slice.sumOf { it.centerOfMass.z } / slice.size
            )
            TrajectoryPoint(points[i].timestampMs, avg)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.TrajectoryBuilderTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/TrajectoryBuilder.kt \
        app/src/test/java/com/projectbeta/engine/TrajectoryBuilderTest.kt
git commit -m "feat: build smoothed COM trajectory from pose frames"
```

---

## Task 4: `MetricsEngine` — Speed Curve

**Files:**
- Create: `app/src/main/java/com/projectbeta/engine/MetricsEngine.kt`
- Test: `app/src/test/java/com/projectbeta/engine/MetricsEngineSpeedTest.kt`

**Interfaces:**
- Consumes: `Trajectory`, `TrajectoryPoint` (Task 2/3).
- Produces:
  - `data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)`
  - `object MetricsEngine { fun computeSpeedCurve(trajectory: Trajectory): List<SpeedSample> }`
  - Speed at point `i` (for `i > 0`) = `distance(points[i], points[i-1]) / ((timestampMs[i] - timestampMs[i-1]) / 1000.0)`. First point has no prior sample, so the curve has `points.size - 1` entries, timestamped at each later point.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetricsEngineSpeedTest {
    @Test
    fun `constant vertical movement yields constant speed`() {
        val trajectory = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0)),
                TrajectoryPoint(1000, Point3D(0.0, 1.0, 0.0)),
                TrajectoryPoint(2000, Point3D(0.0, 2.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeSpeedCurve(trajectory)
        assertEquals(2, curve.size)
        assertEquals(1.0, curve[0].speedPerSecond, 1e-9)
        assertEquals(1.0, curve[1].speedPerSecond, 1e-9)
    }

    @Test
    fun `zero movement yields zero speed`() {
        val trajectory = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(1.0, 1.0, 0.0)),
                TrajectoryPoint(500, Point3D(1.0, 1.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeSpeedCurve(trajectory)
        assertEquals(0.0, curve[0].speedPerSecond, 1e-9)
    }

    @Test
    fun `single point trajectory yields empty speed curve`() {
        val trajectory = Trajectory(
            points = listOf(TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0))),
            units = DistanceUnit.METERS
        )
        assertEquals(0, MetricsEngine.computeSpeedCurve(trajectory).size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineSpeedTest"`
Expected: FAIL — `MetricsEngine` unresolved.

- [ ] **Step 3: Implement `MetricsEngine.kt` (speed portion)**

```kotlin
package com.projectbeta.engine

data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)

object MetricsEngine {
    fun computeSpeedCurve(trajectory: Trajectory): List<SpeedSample> {
        val points = trajectory.points
        if (points.size < 2) return emptyList()
        return (1 until points.size).map { i ->
            val prev = points[i - 1]
            val curr = points[i]
            val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
            val speed = if (dtSeconds > 0.0) curr.centerOfMass.distanceTo(prev.centerOfMass) / dtSeconds else 0.0
            SpeedSample(curr.timestampMs, speed)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineSpeedTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/MetricsEngine.kt \
        app/src/test/java/com/projectbeta/engine/MetricsEngineSpeedTest.kt
git commit -m "feat: compute speed curve from trajectory"
```

---

## Task 5: `MetricsEngine` — Stability Curve (sway + jerk)

**Files:**
- Modify: `app/src/main/java/com/projectbeta/engine/MetricsEngine.kt`
- Test: `app/src/test/java/com/projectbeta/engine/MetricsEngineStabilityTest.kt`

**Interfaces:**
- Consumes: `Trajectory`, `SpeedSample` internals (Task 4).
- Produces:
  - `data class StabilitySample(val timestampMs: Long, val instabilityScore: Double)`
  - `fun MetricsEngine.computeStabilityCurve(trajectory: Trajectory): List<StabilitySample>`
  - Algorithm: primary direction = unit vector from first to last point. Sway at point `i` = absolute magnitude of the component of `(points[i] - points[i-1])` perpendicular to that primary direction. Jerk at point `i` = `|acceleration[i] - acceleration[i-1]| / dt`, where acceleration is the discrete second derivative of position. `instabilityScore[i] = normalizedSway[i] + normalizedJerk[i]`, where normalization divides each raw series by its own max value across the trajectory (so both contribute on a comparable 0-1 scale) — if a series' max is `0.0`, that series contributes `0.0` everywhere (avoids divide-by-zero on a perfectly still trajectory).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetricsEngineStabilityTest {
    @Test
    fun `perfectly straight constant-velocity climb has zero instability`() {
        val trajectory = Trajectory(
            points = (0..5).map { i -> TrajectoryPoint(i * 1000L, Point3D(0.0, i.toDouble(), 0.0)) },
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeStabilityCurve(trajectory)
        curve.forEach { assertEquals(0.0, it.instabilityScore, 1e-9) }
    }

    @Test
    fun `lateral wobble increases instability relative to a straight climb`() {
        val straight = Trajectory(
            points = (0..5).map { i -> TrajectoryPoint(i * 1000L, Point3D(0.0, i.toDouble(), 0.0)) },
            units = DistanceUnit.METERS
        )
        val wobbly = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0)),
                TrajectoryPoint(1000, Point3D(0.5, 1.0, 0.0)),
                TrajectoryPoint(2000, Point3D(-0.5, 2.0, 0.0)),
                TrajectoryPoint(3000, Point3D(0.5, 3.0, 0.0)),
                TrajectoryPoint(4000, Point3D(-0.5, 4.0, 0.0)),
                TrajectoryPoint(5000, Point3D(0.0, 5.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val straightMax = MetricsEngine.computeStabilityCurve(straight).maxOf { it.instabilityScore }
        val wobblyMax = MetricsEngine.computeStabilityCurve(wobbly).maxOf { it.instabilityScore }
        assertTrue(wobblyMax > straightMax)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineStabilityTest"`
Expected: FAIL — `computeStabilityCurve` unresolved.

- [ ] **Step 3: Extend `MetricsEngine.kt` with the stability computation**

```kotlin
package com.projectbeta.engine

import kotlin.math.sqrt

data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)
data class StabilitySample(val timestampMs: Long, val instabilityScore: Double)

object MetricsEngine {
    fun computeSpeedCurve(trajectory: Trajectory): List<SpeedSample> {
        val points = trajectory.points
        if (points.size < 2) return emptyList()
        return (1 until points.size).map { i ->
            val prev = points[i - 1]
            val curr = points[i]
            val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
            val speed = if (dtSeconds > 0.0) curr.centerOfMass.distanceTo(prev.centerOfMass) / dtSeconds else 0.0
            SpeedSample(curr.timestampMs, speed)
        }
    }

    fun computeStabilityCurve(trajectory: Trajectory): List<StabilitySample> {
        val points = trajectory.points
        if (points.size < 3) return points.map { StabilitySample(it.timestampMs, 0.0) }

        val primaryDirection = unitVector(points.last().centerOfMass - points.first().centerOfMass)

        val sway = points.indices.map { i ->
            if (i == 0) 0.0 else perpendicularMagnitude(points[i].centerOfMass - points[i - 1].centerOfMass, primaryDirection)
        }

        val velocities = points.indices.map { i ->
            if (i == 0) Point3D(0.0, 0.0, 0.0) else points[i].centerOfMass - points[i - 1].centerOfMass
        }
        val accelerations = velocities.indices.map { i ->
            if (i == 0) Point3D(0.0, 0.0, 0.0) else velocities[i] - velocities[i - 1]
        }
        val jerk = accelerations.indices.map { i ->
            if (i == 0) 0.0 else accelerations[i].distanceTo(accelerations[i - 1])
        }

        val normalizedSway = normalize(sway)
        val normalizedJerk = normalize(jerk)

        return points.indices.map { i ->
            StabilitySample(points[i].timestampMs, normalizedSway[i] + normalizedJerk[i])
        }
    }

    private fun unitVector(v: Point3D): Point3D {
        val length = v.distanceTo(Point3D(0.0, 0.0, 0.0))
        if (length == 0.0) return Point3D(0.0, 0.0, 0.0)
        return Point3D(v.x / length, v.y / length, v.z / length)
    }

    private fun perpendicularMagnitude(v: Point3D, unitDirection: Point3D): Double {
        val dot = v.x * unitDirection.x + v.y * unitDirection.y + v.z * unitDirection.z
        val parallel = Point3D(unitDirection.x * dot, unitDirection.y * dot, unitDirection.z * dot)
        val perpendicular = v - parallel
        return perpendicular.distanceTo(Point3D(0.0, 0.0, 0.0))
    }

    private fun normalize(values: List<Double>): List<Double> {
        val max = values.maxOrNull() ?: 0.0
        if (max == 0.0) return values.map { 0.0 }
        return values.map { it / max }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineStabilityTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Re-run Task 4's test to confirm no regression**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineSpeedTest"`
Expected: PASS (3 tests, unchanged).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/MetricsEngine.kt \
        app/src/test/java/com/projectbeta/engine/MetricsEngineStabilityTest.kt
git commit -m "feat: compute stability (sway + jerk) curve from trajectory"
```

---

## Task 6: `MetricsEngine` — Crux Detection + `AnalysisReport`

**Files:**
- Modify: `app/src/main/java/com/projectbeta/engine/MetricsEngine.kt`
- Create: `app/src/main/java/com/projectbeta/engine/AnalysisReport.kt`
- Test: `app/src/test/java/com/projectbeta/engine/MetricsEngineCruxTest.kt`

**Interfaces:**
- Consumes: `SpeedSample`, `StabilitySample`, `Trajectory` (Tasks 4-5).
- Produces:
  - `data class CruxSegment(val startMs: Long, val endMs: Long, val difficultyScore: Double)`
  - `fun MetricsEngine.detectCrux(speedCurve: List<SpeedSample>, stabilityCurve: List<StabilitySample>, segmentWindowMs: Long = 1000): CruxSegment?`
  - `data class AnalysisReport(val trajectory: Trajectory, val speedCurve: List<SpeedSample>, val stabilityCurve: List<StabilitySample>, val crux: CruxSegment?, val averageSpeed: Double, val peakSpeed: Double)`
  - `fun MetricsEngine.buildReport(trajectory: Trajectory): AnalysisReport`
  - Difficulty score per timestamp `t`: `(1 / (speed[t] + epsilon)) + instability[t]`, where `epsilon = 0.01` avoids divide-by-zero on a full stop. Explicitly documented as the unvalidated hypothesis formula from the spec — flagged in a code comment, not silently presented as ground truth.
  - Crux segment = the `segmentWindowMs`-wide sliding window (grouped by timestamp) with the highest average difficulty score; returns `null` if either curve is empty (nothing to analyze).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MetricsEngineCruxTest {
    @Test
    fun `identifies the segment with low speed and high instability as the crux`() {
        val speedCurve = listOf(
            SpeedSample(1000, 2.0),
            SpeedSample(2000, 2.0),
            SpeedSample(3000, 0.1),
            SpeedSample(4000, 0.1),
            SpeedSample(5000, 2.0)
        )
        val stabilityCurve = listOf(
            StabilitySample(1000, 0.1),
            StabilitySample(2000, 0.1),
            StabilitySample(3000, 0.9),
            StabilitySample(4000, 0.9),
            StabilitySample(5000, 0.1)
        )
        val crux = MetricsEngine.detectCrux(speedCurve, stabilityCurve, segmentWindowMs = 1000)
        assertNotNull(crux)
        assertEquals(3000L, crux!!.startMs)
    }

    @Test
    fun `empty curves yield no crux`() {
        assertNull(MetricsEngine.detectCrux(emptyList(), emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineCruxTest"`
Expected: FAIL — `detectCrux` unresolved.

- [ ] **Step 3: Extend `MetricsEngine.kt` with crux detection**

Add to the `MetricsEngine` object (alongside the existing functions):

```kotlin
    data class CruxSegment(val startMs: Long, val endMs: Long, val difficultyScore: Double)

    // Difficulty formula is an unvalidated hypothesis (per spec) — needs tuning
    // against coach-labeled climbs before the output can be trusted.
    fun detectCrux(
        speedCurve: List<SpeedSample>,
        stabilityCurve: List<StabilitySample>,
        segmentWindowMs: Long = 1000
    ): CruxSegment? {
        if (speedCurve.isEmpty() || stabilityCurve.isEmpty()) return null

        val stabilityByTimestamp = stabilityCurve.associateBy { it.timestampMs }
        val epsilon = 0.01
        val difficultyByTimestamp = speedCurve.mapNotNull { speedSample ->
            val stability = stabilityByTimestamp[speedSample.timestampMs] ?: return@mapNotNull null
            speedSample.timestampMs to (1.0 / (speedSample.speedPerSecond + epsilon) + stability.instabilityScore)
        }
        if (difficultyByTimestamp.isEmpty()) return null

        val sorted = difficultyByTimestamp.sortedBy { it.first }
        var bestStart = sorted.first().first
        var bestEnd = sorted.first().first
        var bestAvg = Double.NEGATIVE_INFINITY

        for (window in sorted.indices) {
            val windowStart = sorted[window].first
            val windowEnd = windowStart + segmentWindowMs
            val inWindow = sorted.filter { it.first in windowStart..windowEnd }
            val avg = inWindow.sumOf { it.second } / inWindow.size
            if (avg > bestAvg) {
                bestAvg = avg
                bestStart = windowStart
                bestEnd = inWindow.last().first
            }
        }

        return CruxSegment(bestStart, bestEnd, bestAvg)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.MetricsEngineCruxTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement `AnalysisReport.kt`**

```kotlin
package com.projectbeta.engine

data class AnalysisReport(
    val trajectory: Trajectory,
    val speedCurve: List<SpeedSample>,
    val stabilityCurve: List<StabilitySample>,
    val crux: MetricsEngine.CruxSegment?,
    val averageSpeed: Double,
    val peakSpeed: Double
)

fun MetricsEngine.buildReport(trajectory: Trajectory): AnalysisReport {
    val speedCurve = MetricsEngine.computeSpeedCurve(trajectory)
    val stabilityCurve = MetricsEngine.computeStabilityCurve(trajectory)
    val crux = MetricsEngine.detectCrux(speedCurve, stabilityCurve)
    val average = if (speedCurve.isEmpty()) 0.0 else speedCurve.sumOf { it.speedPerSecond } / speedCurve.size
    val peak = speedCurve.maxOfOrNull { it.speedPerSecond } ?: 0.0
    return AnalysisReport(trajectory, speedCurve, stabilityCurve, crux, average, peak)
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/MetricsEngine.kt \
        app/src/main/java/com/projectbeta/engine/AnalysisReport.kt \
        app/src/test/java/com/projectbeta/engine/MetricsEngineCruxTest.kt
git commit -m "feat: detect crux segment and assemble AnalysisReport"
```

---

## Task 7: `PoseEstimator` Interface + MediaPipe Implementation

**Files:**
- Create: `app/src/main/java/com/projectbeta/pose/PoseEstimator.kt`
- Create: `app/src/main/java/com/projectbeta/pose/MediaPipePoseEstimator.kt`
- Test: `app/src/test/java/com/projectbeta/pose/PoseEstimatorMappingTest.kt`

**Interfaces:**
- Consumes: `PoseFrame`, `JointObservation`, `Joint`, `Point3D` (Task 2).
- Produces:
  - `interface PoseEstimator { fun estimate(videoFilePath: String): List<PoseFrame> }`
  - `class MediaPipePoseEstimator(context: android.content.Context) : PoseEstimator` — real implementation wrapping MediaPipe Tasks Vision `PoseLandmarker` in `VIDEO` running mode.
  - `object MediaPipeJointMapping { fun toJoint(landmarkIndex: Int): Joint? }` — maps MediaPipe's 33-point `PoseLandmarker` indices to this project's `Joint` enum (index 11/12 = shoulders, 23/24 = hips, 15/16 = wrists, 27/28 = ankles per the standard MediaPipe Pose topology); returns `null` for landmarks this project doesn't track, so callers filter them out rather than guessing a mapping.

This is the one Android/MediaPipe-dependent boundary. `MediaPipePoseEstimator` itself requires a real device/emulator and a `.task` model file to run, so it is verified manually (Task 10's manual verification step), not by JVM unit test. What *is* unit-testable and where the real bug risk lives is the index-to-joint mapping table — that gets full test coverage here.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.pose

import com.projectbeta.engine.Joint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PoseEstimatorMappingTest {
    @Test
    fun `maps known MediaPipe landmark indices to project joints`() {
        assertEquals(Joint.LEFT_SHOULDER, MediaPipeJointMapping.toJoint(11))
        assertEquals(Joint.RIGHT_SHOULDER, MediaPipeJointMapping.toJoint(12))
        assertEquals(Joint.LEFT_HIP, MediaPipeJointMapping.toJoint(23))
        assertEquals(Joint.RIGHT_HIP, MediaPipeJointMapping.toJoint(24))
        assertEquals(Joint.LEFT_WRIST, MediaPipeJointMapping.toJoint(15))
        assertEquals(Joint.RIGHT_WRIST, MediaPipeJointMapping.toJoint(16))
        assertEquals(Joint.LEFT_ANKLE, MediaPipeJointMapping.toJoint(27))
        assertEquals(Joint.RIGHT_ANKLE, MediaPipeJointMapping.toJoint(28))
    }

    @Test
    fun `unmapped landmark indices return null instead of guessing`() {
        assertNull(MediaPipeJointMapping.toJoint(0))
        assertNull(MediaPipeJointMapping.toJoint(99))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.pose.PoseEstimatorMappingTest"`
Expected: FAIL — `MediaPipeJointMapping` unresolved.

- [ ] **Step 3: Implement `PoseEstimator.kt`**

```kotlin
package com.projectbeta.pose

import com.projectbeta.engine.Joint
import com.projectbeta.engine.PoseFrame

interface PoseEstimator {
    fun estimate(videoFilePath: String): List<PoseFrame>
}

object MediaPipeJointMapping {
    private val indexToJoint = mapOf(
        11 to Joint.LEFT_SHOULDER,
        12 to Joint.RIGHT_SHOULDER,
        23 to Joint.LEFT_HIP,
        24 to Joint.RIGHT_HIP,
        15 to Joint.LEFT_WRIST,
        16 to Joint.RIGHT_WRIST,
        27 to Joint.LEFT_ANKLE,
        28 to Joint.RIGHT_ANKLE
    )

    fun toJoint(landmarkIndex: Int): Joint? = indexToJoint[landmarkIndex]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.pose.PoseEstimatorMappingTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement `MediaPipePoseEstimator.kt`**

```kotlin
package com.projectbeta.pose

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import java.io.File

class MediaPipePoseEstimator(private val context: Context) : PoseEstimator {

    override fun estimate(videoFilePath: String): List<PoseFrame> {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setMinPoseDetectionConfidence(0.5f)
            .build()

        PoseLandmarker.createFromOptions(context, options).use { landmarker ->
            val frames = mutableListOf<PoseFrame>()
            VideoFrameSource(File(videoFilePath)).forEachFrame { bitmap, timestampMs ->
                val result = landmarker.detectForVideo(
                    com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build(),
                    timestampMs
                )
                val landmarks = result.landmarks().firstOrNull() ?: return@forEachFrame
                val joints = landmarks.mapIndexedNotNull { index, landmark ->
                    val joint = MediaPipeJointMapping.toJoint(index) ?: return@mapIndexedNotNull null
                    JointObservation(
                        joint = joint,
                        position = Point3D(landmark.x().toDouble(), landmark.y().toDouble(), 0.0),
                        confidence = landmark.visibility().orElse(0.0f).toDouble(),
                        hasDepth = false
                    )
                }
                frames.add(PoseFrame(timestampMs, joints))
            }
            return frames
        }
    }
}
```

**Note for the implementer:** `VideoFrameSource` (a small helper that decodes an MP4 into per-frame `Bitmap` + timestamp pairs via `MediaMetadataRetriever`) is intentionally left out of this task — it's Android `MediaMetadataRetriever` boilerplate with no interesting logic, and this task's job is the MediaPipe wiring and joint mapping. Add it as a small private helper in the same file when implementing, following the standard `MediaMetadataRetriever.getFrameAtTime` loop pattern.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/projectbeta/pose/PoseEstimator.kt \
        app/src/main/java/com/projectbeta/pose/MediaPipePoseEstimator.kt \
        app/src/test/java/com/projectbeta/pose/PoseEstimatorMappingTest.kt
git commit -m "feat: add PoseEstimator interface and MediaPipe implementation"
```

---

## Task 8: `AnalysisPipeline` Orchestrator

**Files:**
- Create: `app/src/main/java/com/projectbeta/pipeline/AnalysisPipeline.kt`
- Test: `app/src/test/java/com/projectbeta/pipeline/AnalysisPipelineTest.kt`

**Interfaces:**
- Consumes: `PoseEstimator` (Task 7), `TrajectoryBuilder`, `MetricsEngine`, `AnalysisReport` (Tasks 3, 6).
- Produces: `class AnalysisPipeline(private val poseEstimator: PoseEstimator) { fun run(videoFilePath: String, scaleMetersPerUnit: Double?): AnalysisReport }`

- [ ] **Step 1: Write the failing test using a fake `PoseEstimator`**

```kotlin
package com.projectbeta.pipeline

import com.projectbeta.engine.Joint
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import com.projectbeta.pose.PoseEstimator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FakePoseEstimator(private val frames: List<PoseFrame>) : PoseEstimator {
    override fun estimate(videoFilePath: String): List<PoseFrame> = frames
}

class AnalysisPipelineTest {
    @Test
    fun `runs the full pipeline from pose frames to a report`() {
        val frames = (0..4).map { i ->
            PoseFrame(
                timestampMs = i * 1000L,
                joints = listOf(
                    JointObservation(Joint.LEFT_HIP, Point3D(0.0, i.toDouble(), 0.0), 0.9, false),
                    JointObservation(Joint.RIGHT_HIP, Point3D(0.0, i.toDouble(), 0.0), 0.9, false)
                )
            )
        }
        val pipeline = AnalysisPipeline(FakePoseEstimator(frames))

        val report = pipeline.run("unused/path.mp4", scaleMetersPerUnit = null)

        assertEquals(5, report.trajectory.points.size)
        assertEquals(4, report.speedCurve.size)
        assertNotNull(report.crux)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.pipeline.AnalysisPipelineTest"`
Expected: FAIL — `AnalysisPipeline` unresolved.

- [ ] **Step 3: Implement `AnalysisPipeline.kt`**

```kotlin
package com.projectbeta.pipeline

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.MetricsEngine
import com.projectbeta.engine.TrajectoryBuilder
import com.projectbeta.engine.buildReport
import com.projectbeta.pose.PoseEstimator

class AnalysisPipeline(private val poseEstimator: PoseEstimator) {
    fun run(videoFilePath: String, scaleMetersPerUnit: Double?): AnalysisReport {
        val frames = poseEstimator.estimate(videoFilePath)
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit)
        return MetricsEngine.buildReport(trajectory)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.pipeline.AnalysisPipelineTest"`
Expected: PASS.

- [ ] **Step 5: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all tests from Tasks 2-8).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/projectbeta/pipeline/AnalysisPipeline.kt \
        app/src/test/java/com/projectbeta/pipeline/AnalysisPipelineTest.kt
git commit -m "feat: wire pose estimation, trajectory, and metrics into AnalysisPipeline"
```

---

## Task 9: `CalibrationCalculator`

**Files:**
- Create: `app/src/main/java/com/projectbeta/engine/CalibrationCalculator.kt`
- Test: `app/src/test/java/com/projectbeta/engine/CalibrationCalculatorTest.kt`

**Interfaces:**
- Produces: `object CalibrationCalculator { fun computeScale(referencePixelDistance: Double, referenceRealMeters: Double): Double? }`
- Returns `null` (triggering the `TrajectoryBuilder` relative-units fallback) if either input is non-positive — this is the calibration-skipped/failed case from the spec's Error Handling section, made explicit rather than silently producing a nonsensical scale.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalibrationCalculatorTest {
    @Test
    fun `computes meters-per-pixel-unit scale from a known reference`() {
        val scale = CalibrationCalculator.computeScale(referencePixelDistance = 100.0, referenceRealMeters = 1.7)
        assertEquals(0.017, scale!!, 1e-9)
    }

    @Test
    fun `returns null when pixel distance is zero or negative`() {
        assertNull(CalibrationCalculator.computeScale(0.0, 1.7))
        assertNull(CalibrationCalculator.computeScale(-10.0, 1.7))
    }

    @Test
    fun `returns null when real-world reference is zero or negative`() {
        assertNull(CalibrationCalculator.computeScale(100.0, 0.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.CalibrationCalculatorTest"`
Expected: FAIL — `CalibrationCalculator` unresolved.

- [ ] **Step 3: Implement `CalibrationCalculator.kt`**

```kotlin
package com.projectbeta.engine

object CalibrationCalculator {
    fun computeScale(referencePixelDistance: Double, referenceRealMeters: Double): Double? {
        if (referencePixelDistance <= 0.0 || referenceRealMeters <= 0.0) return null
        return referenceRealMeters / referencePixelDistance
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.projectbeta.engine.CalibrationCalculatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/projectbeta/engine/CalibrationCalculator.kt \
        app/src/test/java/com/projectbeta/engine/CalibrationCalculatorTest.kt
git commit -m "feat: compute pixel-to-real-world calibration scale"
```

---

## Task 10: Capture Activity (CameraX + Calibration UI)

**Files:**
- Create: `app/src/main/java/com/projectbeta/capture/CalibrationOverlayView.kt`
- Create: `app/src/main/java/com/projectbeta/capture/CaptureActivity.kt`

**Interfaces:**
- Consumes: `CalibrationCalculator` (Task 9), `AnalysisPipeline`, `MediaPipePoseEstimator` (Tasks 7-8).
- Produces: a running Android activity — no new types consumed by later tasks (this is the outermost layer in Phase 1).

This task is UI/CameraX glue with no meaningful JVM-testable logic (per the spec's own testing strategy: capture/pose integration is sanity-checked manually, not exact-match tested). It is verified via the manual steps below instead of a unit test.

- [ ] **Step 1: Implement `CalibrationOverlayView.kt`**

```kotlin
package com.projectbeta.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CalibrationOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { color = 0xFF00FF00.toInt(); strokeWidth = 6f }
    private var topTapY: Float? = null
    private var bottomTapY: Float? = null
    var onCalibrationComplete: ((pixelDistance: Double) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (topTapY == null) {
            topTapY = event.y
        } else if (bottomTapY == null) {
            bottomTapY = event.y
            onCalibrationComplete?.invoke(kotlin.math.abs(bottomTapY!! - topTapY!!).toDouble())
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        topTapY?.let { canvas.drawLine(0f, it, width.toFloat(), it, paint) }
        bottomTapY?.let { canvas.drawLine(0f, it, width.toFloat(), it, paint) }
    }
}
```

- [ ] **Step 2: Implement `CaptureActivity.kt`**

```kotlin
package com.projectbeta.capture

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recording
import com.projectbeta.engine.CalibrationCalculator
import com.projectbeta.pipeline.AnalysisPipeline
import com.projectbeta.pose.MediaPipePoseEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CaptureActivity : AppCompatActivity() {
    private var calibrationScale: Double? = null
    private var activeRecording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CameraX PreviewView + VideoCapture setup goes here, following the
        // standard CameraX video-capture-use-case pattern from the CameraX docs.
        // On the user's known-height tap sequence completing (CalibrationOverlayView
        // .onCalibrationComplete), call:
        //   calibrationScale = CalibrationCalculator.computeScale(pixelDistance, knownHeightMeters)
        // On recording stopped, call analyzeRecording(outputFilePath).
    }

    private fun analyzeRecording(videoFilePath: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val pipeline = AnalysisPipeline(MediaPipePoseEstimator(applicationContext))
            val report = pipeline.run(videoFilePath, calibrationScale)
            // Phase 2 (separate plan) renders `report` as charts + skeleton-overlay
            // video. For Phase 1, log it so the pipeline is verifiable end-to-end:
            android.util.Log.i(
                "ProjectBeta",
                "avgSpeed=${report.averageSpeed} peakSpeed=${report.peakSpeed} crux=${report.crux}"
            )
        }
    }
}
```

**Note for the implementer:** the CameraX preview/recording wiring (`PreviewView`, `Recorder`, `VideoCapture`, permission request for `CAMERA`) is standard boilerplate from the CameraX video-capture documentation — not reproduced here since it carries no project-specific logic. Focus implementation effort on correctly threading `calibrationScale` from the overlay into `analyzeRecording`.

- [ ] **Step 3: Manual verification (no automated test — per spec's testing strategy for capture/pose integration)**

1. Build and install: `./gradlew :app:installDebug`
2. Launch the app on a physical Android device (emulator camera is not representative).
3. Mount the phone on a fixed tripod facing a boulder wall.
4. Tap top-of-head then top-of-foot to calibrate (or skip to test the fallback path).
5. Record a short (10-20s) climb attempt.
6. Check `adb logcat -s ProjectBeta` for the logged `avgSpeed`/`peakSpeed`/`crux` line.
7. Sanity-check: does the average speed look like a plausible climbing pace? Does the reported crux segment roughly line up with the hardest-looking part of the climb by eye?

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/projectbeta/capture/CalibrationOverlayView.kt \
        app/src/main/java/com/projectbeta/capture/CaptureActivity.kt
git commit -m "feat: add CameraX capture activity with calibration overlay"
```

---

## Self-Review Notes

- **Spec coverage:** capture (Task 10), calibration + relative-unit fallback (Tasks 9, 3), pose estimation via MediaPipe (Task 7), depth-optional seam (`JointObservation.hasDepth`, `TrajectoryBuilder` scale param — Tasks 2-3), trajectory smoothing (Task 3), speed/stability/crux metrics (Tasks 4-6), low-confidence-drop and occlusion-as-skipped-frame error handling (Task 3), batch (not real-time) processing (Task 8's `run()` is a single blocking call), unit-testable metrics engine + manually-verified capture (Tasks 2-9 vs. Task 10) all map to explicit tasks. Report UI (charts, skeleton-overlay video) is explicitly out of scope for this plan per the Goal statement — follow-up plan.
- **Type consistency checked:** `Trajectory`/`TrajectoryPoint` (Task 2) match usage in `TrajectoryBuilder` (Task 3), `MetricsEngine` (Tasks 4-6), and `AnalysisPipeline` (Task 8). `PoseFrame`/`JointObservation`/`Joint` (Task 2) match `TrajectoryBuilder.build` (Task 3) and `PoseEstimator.estimate` (Task 7). `PoseEstimator` interface (Task 7) matches `AnalysisPipeline`'s constructor parameter (Task 8) and the `FakePoseEstimator` test double.
- **No placeholders:** all steps contain complete, concrete code. The two "implementer note" callouts (Tasks 7 and 10) explicitly scope out only well-known Android boilerplate (`MediaMetadataRetriever` frame decoding, CameraX preview/recording setup) with no project-specific logic — not a substitute for real steps.
