package com.projectbeta.engine

object CalibrationCalculator {
    fun computeScale(referencePixelDistance: Double, referenceRealMeters: Double): Double? {
        if (!referencePixelDistance.isFinite() || !referenceRealMeters.isFinite()) return null
        if (referencePixelDistance <= 0.0 || referenceRealMeters <= 0.0) return null
        return referenceRealMeters / referencePixelDistance
    }
}
