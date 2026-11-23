package com.auracurve.app.player.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

// Roadmap 2, M-5: the player's remembered settings, kept in memory and saved in the background.
class PersistedStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the saved value replaces the default once it is read`() =
        runBlocking {
            val state = PersistedState(0L)
            state.start(scope, load = { 300L }, save = {})
            withTimeout(TIMEOUT_MS) { state.value.first { it == 300L } }
            Unit
        }

    @Test
    fun `a change made before the saved value is read is applied on top of it and saved`() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val saved = MutableStateFlow<Pair<Int, Int>?>(null)
            val state = PersistedState(0 to 0)
            state.start(
                scope,
                load = {
                    gate.await()
                    5 to 5
                },
                save = { saved.value = it },
            )
            // Only the second part changes; the first must come from disk, not from the default.
            state.update { it.copy(second = 9) }
            assertEquals(0 to 9, state.value.value)

            gate.complete(Unit)

            withTimeout(TIMEOUT_MS) { state.value.first { it == 5 to 9 } }
            withTimeout(TIMEOUT_MS) { saved.first { it == 5 to 9 } }
            Unit
        }

    @Test
    fun `later changes are saved, the newest last`() =
        runBlocking {
            val saved = MutableStateFlow(-1)
            val state = PersistedState(0)
            state.start(scope, load = { 0 }, save = { saved.value = it })
            withTimeout(TIMEOUT_MS) { state.value.first { it == 0 } }

            (1..50).forEach { n -> state.update { n } }

            assertEquals(50, state.value.value)
            withTimeout(TIMEOUT_MS) { saved.first { it == 50 } }
            Unit
        }

    @Test
    fun `a failing read keeps the value in memory and later changes are still saved`() =
        runBlocking {
            val errors = mutableListOf<Throwable>()
            val saved = MutableStateFlow(-1)
            val state = PersistedState(7)
            state.start(
                scope,
                load = { throw IOException("unreadable") },
                save = { saved.value = it },
                onError = { errors += it },
            )

            state.update { 8 }

            withTimeout(TIMEOUT_MS) { saved.first { it == 8 } }
            assertEquals(8, state.value.value)
            assertEquals(1, errors.size)
        }

    @Test
    fun `only the first start reads`() =
        runBlocking {
            var reads = 0
            val state = PersistedState(0)
            repeat(3) {
                state.start(
                    scope,
                    load = {
                        reads++
                        1
                    },
                    save = {},
                )
            }
            withTimeout(TIMEOUT_MS) { state.value.first { it == 1 } }
            assertEquals(1, reads)
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
