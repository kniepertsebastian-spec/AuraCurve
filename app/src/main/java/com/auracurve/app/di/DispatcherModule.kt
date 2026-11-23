package com.auracurve.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

// One thread at a time for the audio effects (Equalizer, DynamicsProcessing, AudioEffect.queryEffects).
// Every call on them is a binder transaction to the audio server and may block for a while; they run
// here instead of on the main thread, and one after the other, in the order they were asked for.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudioIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    // A singleton: each limitedParallelism() view has its own limit, so every user must share this one.
    @Provides
    @Singleton
    @AudioIoDispatcher
    fun provideAudioIoDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
