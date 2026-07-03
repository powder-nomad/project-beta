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
