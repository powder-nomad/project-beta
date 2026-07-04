package com.projectbeta.report

import com.projectbeta.engine.Joint
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PoseOverlayMathTest {

    private fun frame(timestampMs: Long) = PoseFrame(
        timestampMs = timestampMs,
        joints = listOf(JointObservation(Joint.LEFT_WRIST, Point3D(0.0, 0.0, 0.0), 1.0, false))
    )

    @Test
    fun `nearestFrame returns null for an empty frame list`() {
        assertNull(PoseOverlayMath.nearestFrame(emptyList(), 1000L))
    }

    @Test
    fun `nearestFrame picks the frame with the closest timestamp`() {
        val frames = listOf(frame(0), frame(1000), frame(2000))
        assertEquals(1000L, PoseOverlayMath.nearestFrame(frames, 1300L)?.timestampMs)
        assertEquals(2000L, PoseOverlayMath.nearestFrame(frames, 1600L)?.timestampMs)
    }

    @Test
    fun `nearestFrame clamps to the nearest edge when position is outside the range`() {
        val frames = listOf(frame(500), frame(1500))
        assertEquals(500L, PoseOverlayMath.nearestFrame(frames, 0L)?.timestampMs)
        assertEquals(1500L, PoseOverlayMath.nearestFrame(frames, 10_000L)?.timestampMs)
    }

    @Test
    fun `computeDisplayRect fills the view when aspect ratios match`() {
        val rect = PoseOverlayMath.computeDisplayRect(
            viewWidth = 1080f, viewHeight = 1920f, videoWidth = 1080f, videoHeight = 1920f
        )
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(1080f, rect.width)
        assertEquals(1920f, rect.height)
    }

    @Test
    fun `computeDisplayRect letterboxes top and bottom when video is wider than the view`() {
        // Landscape video (16:9) shown in a portrait view -> pillarboxed to full width,
        // letterboxed top/bottom.
        val rect = PoseOverlayMath.computeDisplayRect(
            viewWidth = 1000f, viewHeight = 1000f, videoWidth = 1600f, videoHeight = 900f
        )
        assertEquals(1000f, rect.width)
        assertEquals(1000f * 900f / 1600f, rect.height, 0.01f)
        assertEquals(0f, rect.left)
        assertEquals((1000f - rect.height) / 2f, rect.top, 0.01f)
    }

    @Test
    fun `toViewSpace maps normalized coordinates into the display rect`() {
        val rect = DisplayRect(left = 100f, top = 50f, width = 800f, height = 600f)
        val (x, y) = PoseOverlayMath.toViewSpace(Point3D(0.5, 0.25, 0.0), rect)
        assertEquals(100f + 0.5f * 800f, x)
        assertEquals(50f + 0.25f * 600f, y)
    }
}
