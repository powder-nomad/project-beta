package com.projectbeta.history

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

private const val CLIP_HEIGHT_DP = 160
private const val SPARKLINE_HEIGHT_DP = 48

/**
 * Each row gets its own [ExoPlayer], created lazily on bind and released in [onViewRecycled] —
 * RecyclerView's own view-recycling bounds how many rows (and therefore players) exist at once
 * to roughly the visible + prefetch window, so there's no unbounded growth as the list scrolls.
 */
class HistoryAdapter(
    private val onCardClick: (ClimbCardData) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items: List<ClimbCardData> = emptyList()

    fun submitList(newItems: List<ClimbCardData>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(buildItemView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], onCardClick)

    override fun onViewRecycled(holder: ViewHolder) = holder.releasePlayer()

    override fun getItemCount(): Int = items.size

    private fun buildItemView(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val playerView = PlayerView(context).apply { useController = false }
        val clipContainer = FrameLayout(context).apply {
            addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            tag = playerView
        }

        val statsText = TextView(context).apply {
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val sparkline = SparklineView(context)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
            addView(clipContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CLIP_HEIGHT_DP)))
            addView(statsText)
            addView(sparkline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SPARKLINE_HEIGHT_DP)))
        }
    }

    class ViewHolder(itemView: LinearLayout) : RecyclerView.ViewHolder(itemView) {
        private val clipContainer = itemView.getChildAt(0) as FrameLayout
        private val playerView = clipContainer.tag as PlayerView
        private val statsText = itemView.getChildAt(1) as TextView
        private val sparkline = itemView.getChildAt(2) as SparklineView
        private var player: ExoPlayer? = null

        fun bind(data: ClimbCardData, onClick: (ClimbCardData) -> Unit) {
            itemView.setOnClickListener { onClick(data) }
            sparkline.setValues(data.speedSparkline)

            val cruxText = data.cruxDifficultyScore?.let { "Crux difficulty: %.2f".format(Locale.US, it) }
                ?: "No crux detected"
            statsText.text = "%s\nPeak speed: %.2f  %s".format(
                Locale.US,
                DateFormat.format("MMM d, yyyy h:mm a", data.recordedAt),
                data.peakSpeed,
                cruxText
            )

            attachLoopingCruxClip(data)
        }

        private fun attachLoopingCruxClip(data: ClimbCardData) {
            releasePlayer()
            val start = data.cruxStartMs
            val end = data.cruxEndMs
            val videoFile = File(data.videoFilePath)
            if (start == null || end == null || !videoFile.exists()) return

            val exoPlayer = ExoPlayer.Builder(itemView.context).build()
            player = exoPlayer
            playerView.player = exoPlayer

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(start)
                .setEndPositionMs(end)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(videoFile.toUri())
                .setClippingConfiguration(clipping)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.volume = 0f
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }

        fun releasePlayer() {
            playerView.player = null
            player?.release()
            player = null
        }
    }
}
