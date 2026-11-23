package com.auracurve.app.di

import com.auracurve.app.auto.AppAutoCatalog
import com.auracurve.app.player.playback.AutoCatalog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// What the car (Android Auto) may browse and play: the app's playlists. AuraCurveApplication hands
// it to the player module's AudioPlayerService through AutoCatalogProvider (roadmap 3: replaces the
// former global AutoCatalogHolder that the application filled in at start-up).
@Module
@InstallIn(SingletonComponent::class)
abstract class AutoModule {
    @Binds
    abstract fun bindAutoCatalog(impl: AppAutoCatalog): AutoCatalog
}
