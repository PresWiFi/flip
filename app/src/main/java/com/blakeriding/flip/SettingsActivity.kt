package com.blakeriding.flip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blakeriding.flip.ui.FlipTheme
import com.blakeriding.flip.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow

/** Flip's own settings: setup, rules, look, tags and the log. */
class SettingsActivity : ComponentActivity() {
    /** Card taps while this screen is open, for registering NFC tags. */
    private val nfc = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        setContent {
            FlipTheme {
                val state by Store.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(state, nfc, Modifier.safeDrawingPadding())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startCardReader { nfc.tryEmit(it) }
    }

    override fun onPause() {
        stopCardReader()
        super.onPause()
    }
}
