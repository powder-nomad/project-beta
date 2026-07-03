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
