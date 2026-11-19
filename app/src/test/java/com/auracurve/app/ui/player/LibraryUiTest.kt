package com.auracurve.app.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.ui.player.library.PlaylistRow
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// The pieces of the player tab (the library): the header and saved playlists.
// The screen itself needs the view models; these are the parts it is built from.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val playlist =
        SavedPlaylist(
            id = "p1",
            title = "Gym Hardstyle",
            tracks = listOf(SavedTrack(1, "Kick", "DJ"), SavedTrack(2, "Bass", "MC")),
        )

    @Test
    fun library_headerAndPlaylists() {
        val played = mutableListOf<String>()
        compose.setContent {
            AuraCurveTheme {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { LibraryHeader() }
                    item {
                        PlaylistRow(
                            playlist = playlist,
                            onPlay = { played.add("playlist") },
                            onPlayTrack = {},
                            onDelete = {},
                            onRemoveTrack = {},
                            onPlayNext = {},
                            onAddToQueue = {},
                            currentTrackId = null,
                            playedIds = emptySet(),
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Gym Hardstyle").assertIsDisplayed()
        compose.onRoot().saveScreenshot("library-playlists")
        compose.onNodeWithContentDescription("Play playlist").performClick()

        assertEquals(listOf("playlist"), played)
    }

    private fun SemanticsNodeInteraction.saveScreenshot(name: String) {
        val bitmap = captureToImage().asAndroidBitmap()
        val dir = File("build/player-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
