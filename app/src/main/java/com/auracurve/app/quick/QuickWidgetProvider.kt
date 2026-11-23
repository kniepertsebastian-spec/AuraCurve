package com.auracurve.app.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.auracurve.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen widget: shows the EQ state and the active preset; one button switches the EQ on or
 * off, the other steps to the next preset (roadmap 06, P5-1).
 *
 * While the app's view model is alive the buttons are broadcasts that it handles silently; when it
 * is not, they open the app with the command instead (a broadcast receiver may not start an
 * activity from the background, and nothing else can drive the engine).
 */
@AndroidEntryPoint
class QuickWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var controls: QuickControls

    @Inject
    lateinit var reader: QuickStateReader

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        runAsync(finish = pending::finish) {
            val state = reader.current()
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views(context, state, controls.hasConsumer)) }
        }
    }

    companion object {
        private const val TAG = "QuickWidgetProvider"

        /**
         * Runs [block] off the main thread for one broadcast (roadmap 2, M-2). A failure (the saved
         * state cannot be read, the launcher rejects the views) is logged, never rethrown: in a
         * scope without a handler it would take the whole process down. [finish] - the broadcast's
         * `PendingResult.finish()` - is always called, and the scope made for this one call is
         * cancelled right after, so nothing outlives the broadcast.
         */
        internal fun runAsync(
            finish: () -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            block: suspend () -> Unit,
        ): Job {
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            return scope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Widget not updated", e)
                } finally {
                    try {
                        finish()
                    } finally {
                        scope.cancel()
                    }
                }
            }
        }

        /** Redraws every placed widget with [state] (null: ask for the saved state again). */
        fun updateAll(
            context: Context,
            state: QuickState?,
            hasConsumer: Boolean,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isEmpty()) return
            if (state == null) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(ComponentName(context, QuickWidgetProvider::class.java))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
                return
            }
            ids.forEach { manager.updateAppWidget(it, views(context, state, hasConsumer)) }
        }

        private fun views(
            context: Context,
            state: QuickState?,
            hasConsumer: Boolean,
        ): RemoteViews {
            val on = state?.masterEnabled == true
            return RemoteViews(context.packageName, R.layout.widget_quick_controls).apply {
                setTextViewText(R.id.widget_eq_toggle, context.getString(if (on) R.string.tile_eq_on_caps else R.string.tile_eq_off_caps))
                setTextColor(
                    R.id.widget_eq_toggle,
                    ContextCompat.getColor(context, if (on) R.color.widget_accent else R.color.widget_muted),
                )
                setTextViewText(R.id.widget_preset, state?.presetName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
                setOnClickPendingIntent(R.id.widget_eq_toggle, commandIntent(context, QuickCommand.TOGGLE_MASTER, hasConsumer))
                setOnClickPendingIntent(R.id.widget_preset, commandIntent(context, QuickCommand.NEXT_PRESET, hasConsumer))
            }
        }

        private fun commandIntent(
            context: Context,
            command: QuickCommand,
            hasConsumer: Boolean,
        ): PendingIntent =
            if (hasConsumer) {
                PendingIntent.getBroadcast(
                    context,
                    command.ordinal,
                    // Roadmap 03, 2.5: the widget provider must be exported for the launcher, so any
                    // app could send it a command; the commands go to a receiver only this app reaches.
                    QuickWidgetCommandReceiver.intent(context, command),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                QuickCommandIntents.pendingIntent(context, command)
            }
    }
}
