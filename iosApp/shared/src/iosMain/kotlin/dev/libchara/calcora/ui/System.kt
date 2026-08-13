package dev.libchara.calcora.ui

import kotlin.time.TimeSource

/** Minimal java.lang.System clock surface used by the shared calculator UI. */
object System {
    private val origin = TimeSource.Monotonic.markNow()

    fun nanoTime(): Long = origin.elapsedNow().inWholeNanoseconds
}
