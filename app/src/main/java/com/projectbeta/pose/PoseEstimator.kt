package com.projectbeta.pose

import com.projectbeta.engine.Joint
import com.projectbeta.engine.PoseFrame

interface PoseEstimator {
    fun estimate(videoFilePath: String): List<PoseFrame>
}

object MediaPipeJointMapping {
    private val indexToJoint = mapOf(
        11 to Joint.LEFT_SHOULDER,
        12 to Joint.RIGHT_SHOULDER,
        23 to Joint.LEFT_HIP,
        24 to Joint.RIGHT_HIP,
        15 to Joint.LEFT_WRIST,
        16 to Joint.RIGHT_WRIST,
        27 to Joint.LEFT_ANKLE,
        28 to Joint.RIGHT_ANKLE
    )

    fun toJoint(landmarkIndex: Int): Joint? = indexToJoint[landmarkIndex]
}
