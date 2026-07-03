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
