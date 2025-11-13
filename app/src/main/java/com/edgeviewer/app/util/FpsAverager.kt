package com.edgeviewer.app.util

class FpsAverager(
    private val window: Int = 10
) {
    private val samples = ArrayDeque<Double>()

    fun track(value: Double): Double {
        samples.addLast(value)
        if (samples.size > window) {
            samples.removeFirst()
        }
        return samples.average()
    }
}

