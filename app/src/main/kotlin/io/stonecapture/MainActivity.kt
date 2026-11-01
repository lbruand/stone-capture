package io.stonecapture

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadDebugGame()
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    GameScreen(viewModel<GameViewModel>())
                }
            }
        }
    }

    /**
     * Debug builds only: `am start -n io.stonecapture/.MainActivity --es saved_game '<json>'` replaces the
     * saved game so a specific position can be inspected on a device. Ignored in release builds.
     */
    private fun loadDebugGame() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val text = intent.getStringExtra(EXTRA_SAVED_GAME) ?: return
        if (debuggable) runBlocking { GameStore(this@MainActivity).saveRawGame(text) }
    }

    private companion object {
        const val EXTRA_SAVED_GAME = "saved_game"
    }
}
