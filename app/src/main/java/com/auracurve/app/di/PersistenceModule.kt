package com.auracurve.app.di

import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.correction.RoomCorrectionProfileRepository
import com.auracurve.app.playlist.DataStorePlaylistRepository
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.RoomPresetRepository
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.CorrectionSuggestionDismissals
import com.auracurve.app.profile.DeviceAliasStore
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.profile.RoomAppProfileRepository
import com.auracurve.app.profile.RoomDeviceProfileRepository
import com.auracurve.app.profile.SharedPreferencesCorrectionSuggestionDismissals
import com.auracurve.app.profile.SharedPreferencesDeviceAliasStore
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.DataStoreAppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: RoomPresetRepository): PresetRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: DataStoreAppSettingsRepository): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindCorrectionProfileRepository(impl: RoomCorrectionProfileRepository): CorrectionProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceProfileRepository(impl: RoomDeviceProfileRepository): DeviceProfileRepository

    @Binds
    @Singleton
    abstract fun bindAppProfileRepository(impl: RoomAppProfileRepository): AppProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceAliasStore(impl: SharedPreferencesDeviceAliasStore): DeviceAliasStore

    @Binds
    @Singleton
    abstract fun bindCorrectionSuggestionDismissals(impl: SharedPreferencesCorrectionSuggestionDismissals): CorrectionSuggestionDismissals

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: DataStorePlaylistRepository): PlaylistRepository
}
