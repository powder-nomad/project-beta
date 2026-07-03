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
