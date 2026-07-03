package com.projectbeta.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.projectbeta.engine.CalibrationCalculator
import com.projectbeta.pipeline.AnalysisPipeline
import com.projectbeta.pose.MediaPipePoseEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ProjectBeta"
private const val RECORD_BUTTON_MARGIN_DP = 32

/**
 * Fixed-camera capture screen: shows a CameraX preview, records a climb attempt to local
 * storage, and (optionally) lets the user calibrate pixel-to-meter scale by tapping the top
 * of their head and the top of their foot while standing in frame at a known height.
 *
 * This activity intentionally does not track or compensate for camera movement — the camera
 * is assumed fixed (e.g. on a tripod) for the duration of the recording.
 */
class CaptureActivity : AppCompatActivity() {

    private var calibrationScale: Double? = null
    private var knownHeightMeters: Double? = null
    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var pendingOutputFile: File? = null

    private lateinit var rootLayout: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private var calibrationOverlay: CalibrationOverlayView? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to record a climb.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        promptForCalibration()
    }

    // --- UI construction -----------------------------------------------------------------

    private fun buildUi() {
        rootLayout = FrameLayout(this)

        previewView = PreviewView(this)
        rootLayout.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        recordButton = Button(this).apply {
            text = "Record"
            setOnClickListener { toggleRecording() }
        }
        val buttonParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(RECORD_BUTTON_MARGIN_DP)
        }
        rootLayout.addView(recordButton, buttonParams)

        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // --- Calibration -----------------------------------------------------------------------

    /**
     * Asks the user for their known height in meters, then (if provided) attaches the
     * tap-to-calibrate overlay. Calibration is optional: if the user skips it (or enters an
     * invalid value), [calibrationScale] simply stays null and the rest of the pipeline falls
     * back to relative units via [com.projectbeta.engine.TrajectoryBuilder].
     */
    private fun promptForCalibration() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "e.g. 1.75"
        }

        AlertDialog.Builder(this)
            .setTitle("Calibrate (optional)")
            .setMessage(
                "Enter your height in meters, then tap the top of your head and the top of " +
                    "your foot on the preview to calibrate real-world distances. Skip to " +
                    "record in relative units instead."
            )
            .setView(input)
            .setPositiveButton("Calibrate") { _, _ ->
                val height = input.text.toString().toDoubleOrNull()
                if (height == null || height <= 0.0) {
                    Toast.makeText(this, "Invalid height — skipping calibration.", Toast.LENGTH_LONG)
                        .show()
                } else {
                    knownHeightMeters = height
                    attachCalibrationOverlay()
                }
            }
            .setNegativeButton("Skip", null)
            .setCancelable(false)
            .show()
    }

    private fun attachCalibrationOverlay() {
        val overlay = CalibrationOverlayView(this, null).apply {
            onCalibrationComplete = { pixelDistance -> onCalibrationTapsComplete(pixelDistance) }
        }
        calibrationOverlay = overlay
        rootLayout.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        Toast.makeText(
            this,
            "Tap the top of your head, then the top of your foot.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun onCalibrationTapsComplete(pixelDistance: Double) {
        val height = knownHeightMeters
        calibrationScale = if (height != null) {
            CalibrationCalculator.computeScale(pixelDistance, height)
        } else {
            null
        }

        calibrationOverlay?.let { rootLayout.removeView(it) }
        calibrationOverlay = null

        val message = if (calibrationScale != null) {
            "Calibration complete."
        } else {
            "Calibration failed — recording will use relative units."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- CameraX ------------------------------------------------------------------------------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "CameraX use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // --- Recording ------------------------------------------------------------------------------

    private fun toggleRecording() {
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            return
        }
        startRecording()
    }

    private fun startRecording() {
        val capture = videoCapture
        if (capture == null) {
            Toast.makeText(this, "Camera is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val outputDir = (getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir).apply {
            mkdirs()
        }
        val outputFile = File(outputDir, "capture_${System.currentTimeMillis()}.mp4")
        pendingOutputFile = outputFile

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event -> onVideoRecordEvent(event) }

        recordButton.text = "Stop"
    }

    private fun onVideoRecordEvent(event: VideoRecordEvent) {
        if (event !is VideoRecordEvent.Finalize) return

        activeRecording = null
        recordButton.text = "Record"

        val outputFile = pendingOutputFile
        pendingOutputFile = null

        if (event.hasError()) {
            Log.e(TAG, "Video recording finished with error: ${event.error}", event.cause)
            Toast.makeText(this, "Recording failed.", Toast.LENGTH_LONG).show()
            return
        }

        if (outputFile == null) {
            Log.e(TAG, "Recording finalized but no output file was tracked.")
            return
        }

        // Batch processing: analysis runs only now, after recording has fully stopped —
        // never live during capture.
        analyzeRecording(outputFile.absolutePath)
    }

    // --- Analysis (Phase 1: batch, log-only) ---------------------------------------------------

    private fun analyzeRecording(videoFilePath: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val pipeline = AnalysisPipeline(MediaPipePoseEstimator(applicationContext))
            val report = pipeline.run(videoFilePath, calibrationScale)
            // Phase 2 (separate plan) renders `report` as charts + skeleton-overlay
            // video. For Phase 1, log it so the pipeline is verifiable end-to-end:
            Log.i(
                TAG,
                "avgSpeed=${report.averageSpeed} peakSpeed=${report.peakSpeed} crux=${report.crux}"
            )
        }
    }
}
