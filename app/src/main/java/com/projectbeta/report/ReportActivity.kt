package com.projectbeta.report

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.projectbeta.data.ClimbMapper
import com.projectbeta.data.ClimbRecord
import com.projectbeta.data.ClimbRepository
import com.projectbeta.data.ReportPayload
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val EXTRA_CLIMB_ID = "climb_id"
private const val VIDEO_PLAYER_HEIGHT_DP = 300
private const val CHART_HEIGHT_DP = 220
private const val OVERLAY_POLL_INTERVAL_MS = 33L

/** Shows one climb's full report: summary stats, speed/stability charts with the crux segment
 * highlighted, and a skeleton-overlay replay of the recorded video. */
class ReportActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var overlayView: SkeletonOverlayView
    private lateinit var statsView: TextView
    private lateinit var speedChart: LineChart
    private lateinit var stabilityChart: LineChart

    private val overlayPollHandler = Handler(Looper.getMainLooper())
    private val overlayPollRunnable = object : Runnable {
        override fun run() {
            player?.let { overlayView.updatePlaybackPosition(it.currentPosition) }
            overlayPollHandler.postDelayed(this, OVERLAY_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val climbId = intent.getLongExtra(EXTRA_CLIMB_ID, -1L)
        buildUi()

        if (climbId < 0) {
            statsView.text = "No climb specified."
            return
        }
        loadClimb(climbId)
    }

    override fun onStart() {
        super.onStart()
        overlayPollHandler.post(overlayPollRunnable)
    }

    override fun onStop() {
        super.onStop()
        overlayPollHandler.removeCallbacks(overlayPollRunnable)
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // --- UI construction -----------------------------------------------------------------

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val playerContainer = FrameLayout(this)
        val playerView = PlayerView(this)
        overlayView = SkeletonOverlayView(this)
        playerContainer.addView(playerView, matchParent())
        playerContainer.addView(overlayView, matchParent())
        root.addView(
            playerContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(VIDEO_PLAYER_HEIGHT_DP))
        )

        statsView = TextView(this).apply {
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            text = "Loading report…"
        }
        root.addView(statsView)

        speedChart = LineChart(this).apply { description.text = "Speed (units/sec)" }
        root.addView(speedChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)))

        stabilityChart = LineChart(this).apply { description.text = "Instability" }
        root.addView(stabilityChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)))

        val exoPlayer = ExoPlayer.Builder(this).build().also { player = it }
        playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                overlayView.updateVideoSize(videoSize.width.toFloat(), videoSize.height.toFloat())
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // --- Data loading ----------------------------------------------------------------------

    private fun loadClimb(climbId: Long) {
        lifecycleScope.launch {
            try {
                val repository = ClimbRepository(applicationContext)
                val record = repository.getById(climbId)
                if (record == null) {
                    statsView.text = "Climb not found."
                    return@launch
                }
                val payload = ClimbMapper.toReportPayload(record)
                renderReport(record, payload)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ReportActivity", "Failed to load climb $climbId", e)
                statsView.text = "Couldn't load this report — its saved data may be corrupted."
            }
        }
    }

    private fun renderReport(record: ClimbRecord, payload: ReportPayload) {
        val report = payload.report
        val cruxText = report.crux?.let {
            "Crux: %.1fs–%.1fs (difficulty %.2f)".format(
                Locale.US, it.startMs / 1000.0, it.endMs / 1000.0, it.difficultyScore
            )
        } ?: "No crux detected"
        statsView.text = "Avg speed: %.2f  Peak speed: %.2f\n%s".format(
            Locale.US, report.averageSpeed, report.peakSpeed, cruxText
        )

        overlayView.setPoseFrames(payload.poseFrames)
        renderChart(speedChart, report.speedCurve.map { it.timestampMs to it.speedPerSecond }, report.crux, "Speed")
        renderChart(stabilityChart, report.stabilityCurve.map { it.timestampMs to it.instabilityScore }, report.crux, "Instability")

        val videoFile = File(record.videoFilePath)
        if (videoFile.exists()) {
            playVideo(videoFile.toUri())
        } else {
            statsView.append("\n(Video file unavailable)")
        }
    }

    private fun playVideo(uri: Uri) {
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = false
    }

    private fun renderChart(
        chart: LineChart,
        samples: List<Pair<Long, Double>>,
        crux: com.projectbeta.engine.MetricsEngine.CruxSegment?,
        label: String
    ) {
        val entries = samples.map { (timestampMs, value) -> Entry(timestampMs / 1000f, value.toFloat()) }
        val dataSet = LineDataSet(entries, label).apply {
            setDrawCircles(false)
            lineWidth = 2f
        }
        chart.data = LineData(dataSet)
        chart.xAxis.removeAllLimitLines()
        if (crux != null) {
            chart.xAxis.addLimitLine(
                LimitLine(crux.startMs / 1000f, "Crux start").apply { lineColor = Color.RED }
            )
            chart.xAxis.addLimitLine(
                LimitLine(crux.endMs / 1000f, "Crux end").apply { lineColor = Color.RED }
            )
        }
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val seekSeconds = e?.x ?: return
                if (crux != null && seekSeconds >= crux.startMs / 1000f && seekSeconds <= crux.endMs / 1000f) {
                    player?.seekTo(crux.startMs)
                }
            }

            override fun onNothingSelected() = Unit
        })
        chart.invalidate()
    }

    companion object {
        fun start(context: Context, climbId: Long) {
            context.startActivity(
                Intent(context, ReportActivity::class.java).putExtra(EXTRA_CLIMB_ID, climbId)
            )
        }
    }
}
