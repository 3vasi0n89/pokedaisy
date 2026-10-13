package com.pokedaisy.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fast-forward on the refresh (GitHub #64: 2x ran at 8x on a 120 Hz screen). */
class FfPacingTest {
    @Test fun sixtyHzRunsSpeedFramesARefresh() {
        assertEquals(2, ffFramesPerRefresh(2f, 1))
        assertEquals(3, ffFramesPerRefresh(3f, 1))
        assertEquals(4, ffFramesPerRefresh(4f, 1))
    }

    @Test fun oneTwentyHzRunsHalfAsManyARefresh() {
        assertEquals(1, ffFramesPerRefresh(2f, 2))
        assertEquals(2, ffFramesPerRefresh(4f, 2))
    }

    @Test fun framesASecondMatchTheSpeed() {
        for ((speed, k) in listOf(2f to 1, 3f to 1, 4f to 1, 2f to 2, 4f to 2)) {
            val refreshHz = 60.0 * k
            assertEquals(60.0 * speed, ffFramesPerRefresh(speed, k) * refreshHz, 0.001)
        }
    }

    @Test fun fractionsGoToTheTimer() {
        assertEquals(0, ffFramesPerRefresh(1.5f, 1))
        assertEquals(0, ffFramesPerRefresh(1.5f, 2))
        assertEquals(0, ffFramesPerRefresh(3f, 2))
    }

    @Test fun noVsyncOrNoFfGoesToTheTimer() {
        assertEquals(0, ffFramesPerRefresh(2f, 0))
        assertEquals(0, ffFramesPerRefresh(1f, 1))
        assertEquals(0, ffFramesPerRefresh(0.5f, 1))
        assertEquals(0, ffFramesPerRefresh(Float.POSITIVE_INFINITY, 1))
    }
}
