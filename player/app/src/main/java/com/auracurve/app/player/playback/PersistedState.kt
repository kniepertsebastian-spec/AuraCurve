package com.auracurve.app.player.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A value read synchronously from memory (the playback thread, a view model) and kept on disk in
 * the background (roadmap 2, M-5): nothing here touches the disk on the calling thread.
 *
 * Until [start] has read the saved value, [value] holds [initial]. A change made in that short
 * window is not lost: it is applied again on top of the saved value once that is read. Writes go
 * out one after the other, the newest value last; a burst (a slider being dragged) only writes the
 * latest value.
 */
class PersistedState<T>(
    initial: T,
) {
    private val state = MutableStateFlow(initial)
    val value: StateFlow<T> = state.asStateFlow()

    private val lock = Any()
    private var started = false
    private var loaded = false
    private val earlyChanges = mutableListOf<(T) -> T>()
    private val pendingWrites = Channel<T>(Channel.CONFLATED)

    /**
     * Reads the saved value with [load] (null: nothing saved) and from then on saves every change
     * with [save], all in [scope]. Only the first call does anything. A failing read keeps the
     * value in memory; a failing write is reported to [onError] and the next change tries again.
     */
    fun start(
        scope: CoroutineScope,
        load: suspend () -> T?,
        save: suspend (T) -> Unit,
        onError: (Throwable) -> Unit = {},
    ) {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch {
            val saved = guarded(onError) { load() }
            synchronized(lock) {
                var current = saved ?: state.value
                earlyChanges.forEach { current = it(current) }
                if (earlyChanges.isNotEmpty()) pendingWrites.trySend(current)
                earlyChanges.clear()
                loaded = true
                state.value = current
            }
            for (next in pendingWrites) guarded(onError) { save(next) }
        }
    }

    /** Changes the value in memory at once and queues it to be saved. */
    fun update(transform: (T) -> T) {
        synchronized(lock) {
            state.value = transform(state.value)
            if (loaded) pendingWrites.trySend(state.value) else earlyChanges += transform
        }
    }

    private inline fun <R> guarded(
        onError: (Throwable) -> Unit,
        block: () -> R,
    ): R? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            null
        }
}
