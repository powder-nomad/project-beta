package com.projectbeta.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.projectbeta.data.ClimbMapper
import com.projectbeta.data.ClimbRecord
import com.projectbeta.data.ClimbRepository
import com.projectbeta.report.ReportActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Scrollable list of past climbs, newest first. Each card previews a looped clip of the crux
 * segment, the peak speed / crux difficulty KPIs, and a speed sparkline. Tapping a card opens
 * its full [ReportActivity]. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: TextView
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadHistory()
    }

    private fun buildUi() {
        val root = FrameLayout(this)

        adapter = HistoryAdapter { card -> ReportActivity.start(this, card.id) }
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }
        root.addView(recyclerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        emptyStateView = TextView(this).apply {
            text = "No climbs recorded yet."
            gravity = Gravity.CENTER
            visibility = android.view.View.GONE
        }
        root.addView(
            emptyStateView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        )

        setContentView(root)
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val repository = ClimbRepository(applicationContext)
            val cards = withContext(Dispatchers.IO) { loadCards(repository) }

            adapter.submitList(cards)
            recyclerView.visibility = if (cards.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            emptyStateView.visibility = if (cards.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private suspend fun loadCards(repository: ClimbRepository): List<ClimbCardData> =
        repository.getAll().map { record -> toCardData(record) }

    private fun toCardData(record: ClimbRecord): ClimbCardData {
        val payload = ClimbMapper.toReportPayload(record)
        return ClimbCardData(
            id = record.id,
            recordedAt = record.recordedAt,
            videoFilePath = record.videoFilePath,
            peakSpeed = record.peakSpeed,
            cruxDifficultyScore = record.cruxDifficultyScore,
            cruxStartMs = record.cruxStartMs,
            cruxEndMs = record.cruxEndMs,
            speedSparkline = payload.report.speedCurve.map { it.speedPerSecond }
        )
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, HistoryActivity::class.java))
        }
    }
}
