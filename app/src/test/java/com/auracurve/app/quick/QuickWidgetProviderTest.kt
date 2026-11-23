package com.auracurve.app.quick

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Roadmap 2, M-2: the widget's update work for one broadcast.
@OptIn(ExperimentalCoroutinesApi::class)
class QuickWidgetProviderTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `a failing update is caught, the broadcast finished and the scope closed`() {
        var finished = 0
        var completionCause: Throwable? = null
        val job = QuickWidgetProvider.runAsync(finish = { finished++ }, dispatcher = dispatcher) { error("saved state unreadable") }
        job.invokeOnCompletion { completionCause = it }

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, finished)
        assertTrue(job.isCompleted)
        // The scope was cancelled after finish(); the update's own exception did not escape it.
        assertTrue(completionCause is CancellationException)
        assertFalse(completionCause?.cause is IllegalStateException)
    }

    @Test
    fun `a successful update finishes the broadcast once and closes the scope`() {
        var finished = 0
        var ran = false
        val job = QuickWidgetProvider.runAsync(finish = { finished++ }, dispatcher = dispatcher) { ran = true }

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(ran)
        assertEquals(1, finished)
        assertTrue(job.isCompleted)
    }
}
