package com.edubuddy.app.core

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Attendance arithmetic. "Cancelled" classes never count towards the total.
 * [target] is a fraction in (0, 1), e.g. 0.75.
 */
object AttendanceMath {

    data class Summary(
        val attended: Int,
        val total: Int,
        val target: Double,
    ) {
        val percent: Double get() = if (total == 0) 0.0 else attended * 100.0 / total
        val ratio: Float get() = if (total == 0) 0f else attended.toFloat() / total
        val onTrack: Boolean get() = total == 0 || attended.toDouble() / total >= target - 1e-9
        val canSkip: Int get() = classesYouCanSkip(attended, total, target)
        val mustAttend: Int get() = classesNeededToReach(attended, total, target)
    }

    /**
     * Largest k such that attended / (total + k) >= target.
     * i.e. how many upcoming classes you can miss and still stay at target.
     */
    fun classesYouCanSkip(attended: Int, total: Int, target: Double): Int {
        require(target > 0 && target < 1) { "target must be in (0,1)" }
        if (total == 0) return 0
        val k = floor(attended / target - total + 1e-9).toInt()
        return k.coerceAtLeast(0)
    }

    /**
     * Smallest n such that (attended + n) / (total + n) >= target.
     * i.e. how many consecutive classes you must attend to get back to target.
     */
    fun classesNeededToReach(attended: Int, total: Int, target: Double): Int {
        require(target > 0 && target < 1) { "target must be in (0,1)" }
        if (total == 0) return 0
        if (attended.toDouble() / total >= target - 1e-9) return 0
        val n = ceil((target * total - attended) / (1 - target) - 1e-9).toInt()
        return n.coerceAtLeast(0)
    }
}
