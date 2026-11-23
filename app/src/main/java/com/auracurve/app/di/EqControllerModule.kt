package com.auracurve.app.di

import android.util.Log
import com.auracurve.app.diagnostics.DiagnosticsRecorder
import com.auracurve.app.eq.EqControllerScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EqControllerModule {
    private const val TAG = "EqControllerScope"

    // Process lifetime, and the main thread as in viewModelScope: the controller's state was written
    // from the main thread only before E-008 stage 2, and stays so. Callers on other threads (the
    // remote server) switch to the main thread before calling it.
    //
    // The scope lives as long as the process, so one failing job (a database write, a damaged
    // profile) must not take the process down with it: the handler logs the error and notes it for
    // the diagnostics report; the SupervisorJob keeps the other jobs (route and volume collectors)
    // running. Only the exception's class goes into the report, never its message, which may name
    // a device or file.
    @Provides
    @Singleton
    @EqControllerScope
    fun provideEqControllerScope(diagnostics: DiagnosticsRecorder): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Uncaught error in an equalizer job", throwable)
                    diagnostics.record("Equalizer job failed: ${throwable::class.java.simpleName}")
                },
        )
}
