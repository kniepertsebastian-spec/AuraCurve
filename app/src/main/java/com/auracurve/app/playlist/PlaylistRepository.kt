package com.auracurve.app.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Playlists imported from share links, kept on the device. Stored as one JSON
// document in its own DataStore rather than in the Room database: a playlist is
// read and written as a whole, and this way the existing database schema (and
// its migration story) stays untouched.
interface PlaylistRepository {
    val playlists: Flow<List<SavedPlaylist>>

    /**
     * How many tracks of files on the device were taken out of the playlists because this phone
     * cannot open them (roadmap 2, M-4: playlists restored from a backup of another phone); 0 = none
     * or already shown. Reset with [acknowledgeRemovedTracks].
     */
    val removedUnreadableTracks: StateFlow<Int> get() = NoneRemoved

    fun acknowledgeRemovedTracks() {}

    // Upsert by id - importing the same link again refreshes its playlist.
    suspend fun save(playlist: SavedPlaylist)

    suspend fun delete(id: String)
}

private val NoneRemoved: StateFlow<Int> = MutableStateFlow(0).asStateFlow()

/** Which addresses of files on the device this phone can no longer open. */
fun interface LocalAddressAccess {
    fun unreadable(addresses: Set<String>): Set<String>
}

/**
 * A file picked with the system file picker stays readable through the grant the app keeps for it
 * (`takePersistableUriPermission`). Without that grant a `content://` address cannot be opened
 * again - after a restore onto another phone none of the old grants exist. Other addresses are
 * left alone. Only asks the system for the list of grants; no file is opened.
 */
@Singleton
class AndroidLocalAddressAccess
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LocalAddressAccess {
        override fun unreadable(addresses: Set<String>): Set<String> {
            val content = addresses.filter { Uri.parse(it).scheme == ContentResolver.SCHEME_CONTENT }
            if (content.isEmpty()) return emptySet()
            val granted =
                context.contentResolver.persistedUriPermissions
                    .filter { it.isReadPermission }
                    .mapTo(HashSet()) { it.uri.toString() }
            return content.filterNot { it in granted }.toSet()
        }
    }

private val Context.playlistDataStore by preferencesDataStore(name = "playlists")
private val PLAYLISTS_KEY = stringPreferencesKey("playlists_json")

@Singleton
class DataStorePlaylistRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val access: LocalAddressAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaylistRepository {
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        access: LocalAddressAccess,
    ) : this(context.playlistDataStore, access)

    private val checkLock = Mutex()

    @Volatile
    private var checked = false

    private val removed = MutableStateFlow(0)
    override val removedUnreadableTracks: StateFlow<Int> = removed.asStateFlow()

    // The first reader in this process waits until tracks this phone cannot open are taken out, so
    // nobody (the player screen, the car) is handed one of them.
    override val playlists: Flow<List<SavedPlaylist>> =
        store.data
            .map { prefs -> SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY]) }
            .onStart { removeUnreadableTracksOnce() }

    override fun acknowledgeRemovedTracks() {
        removed.value = 0
    }

    override suspend fun save(playlist: SavedPlaylist) {
        store.edit { prefs ->
            val current = SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY])
            prefs[PLAYLISTS_KEY] = SavedPlaylistJson.encode(current.filterNot { it.id == playlist.id } + playlist)
        }
    }

    override suspend fun delete(id: String) {
        store.edit { prefs ->
            val current = SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY])
            prefs[PLAYLISTS_KEY] = SavedPlaylistJson.encode(current.filterNot { it.id == id })
        }
    }

    internal suspend fun removeUnreadableTracksOnce() {
        if (checked) return
        checkLock.withLock {
            if (checked) return
            try {
                withContext(ioDispatcher) {
                    var count = 0
                    store.edit { prefs ->
                        val current = SavedPlaylistJson.decode(prefs[PLAYLISTS_KEY])
                        val unreadable = access.unreadable(UnreadableLocalTracks.localAddresses(current))
                        val result = UnreadableLocalTracks.remove(current, unreadable)
                        count = result.removedTracks
                        if (count > 0) prefs[PLAYLISTS_KEY] = SavedPlaylistJson.encode(result.playlists)
                    }
                    if (count > 0) {
                        Log.i(TAG, "Removed $count playlist tracks that can no longer be opened on this device")
                        removed.value += count
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Playlists not checked for unreadable files", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Playlists not checked for unreadable files", e)
            }
            checked = true
        }
    }

    private companion object {
        const val TAG = "PlaylistRepository"
    }
}
