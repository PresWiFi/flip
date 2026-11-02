package com.blakeriding.flip

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.blakeriding.flip.ui.FlipTheme
import com.blakeriding.flip.ui.HomeActions
import com.blakeriding.flip.ui.HomeNav
import com.blakeriding.flip.ui.HomeUi
import com.blakeriding.flip.ui.ModernHome
import com.blakeriding.flip.ui.PhoneStatus
import com.blakeriding.flip.ui.Screen
import com.blakeriding.flip.ui.V3Phone
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders the main screens to PNGs for a visual check. Skipped unless a folder is given:
 * ./gradlew testDebugUnitTest --tests '*ScreensTest*' -PscreensDir=/some/folder
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val dir = System.getProperty("screens.dir").orEmpty()

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val apps = listOf(
        AppEntry("com.android.camera", "Camera"), AppEntry("com.google.android.gm", "Gmail"), AppEntry(ig, "Instagram"),
        AppEntry("com.spotify.music", "Spotify"), AppEntry(yt, "YouTube"), AppEntry("com.google.android.apps.maps", "Maps"),
        AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.reddit.frontpage", "Reddit"),
    )
    private val now = ZonedDateTime.of(2026, 11, 1, 21, 47, 0, 0, ZoneId.systemDefault())
    private val state = AppState(
        rules = listOf(AppRule(ig, dailyLimitMin = 30), AppRule("com.reddit.frontpage", always = true)),
        groups = listOf(Group("g", "Video", setOf(yt), 60)),
        settings = Settings(favorites = apps.map { it.pkg } + null),
    )
    private val usage = mapOf(ig to 31 * MINUTE_MS, yt to 22 * MINUTE_MS)
    private val actions = HomeActions({}, {}, {})

    @Before fun setUp() {
        assumeTrue(dir.isNotEmpty())
        Store.init(ApplicationProvider.getApplicationContext())
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { FlipTheme { content() } }
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ui(s: AppState = state) = HomeUi(s, usage, now, PhoneStatus(78, false, 3), apps, listOf(Contact("Mom", "5551234567")))

    @Test fun v3Idle() = shot("v3-idle") { V3Phone(ui(), HomeNav(), actions, outer = false) }

    @Test fun v3Menu() = shot("v3-menu") { V3Phone(ui(), HomeNav().apply { screen = Screen.MENU }, actions, outer = false) }

    @Test fun v3Search() = shot("v3-t9") { V3Phone(ui(), HomeNav().apply { digits = "46" }, actions, outer = false) }

    @Test fun v3NoKeypad() = shot("v3-nokeypad") {
        V3Phone(ui(state.copy(settings = state.settings.copy(keypad = KeypadMode.NONE))), HomeNav(), actions, outer = false)
    }

    @Test fun v3Outer() = shot("v3-outer") { V3Phone(ui(), HomeNav(), actions, outer = true) }

    @Test fun modern() = shot("modern") { ModernHome(ui(), HomeNav(), actions) }

    @Test fun block() {
        Store.update { state.copy(tags = listOf(Tag("t", "Garage", TagKind.NFC, "AB12")), settings = state.settings.copy(codeHash = "x", codeSalt = "y")) }
        shot("block") { BlockScreen("com.reddit.frontpage", MutableSharedFlow(), {}, {}) }
    }
}
