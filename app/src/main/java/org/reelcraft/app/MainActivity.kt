package org.reelcraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.reelcraft.app.ui.EditorScreen
import org.reelcraft.app.ui.ReelCraftTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReelCraftTheme {
                EditorScreen(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.pauseAll()
    }
}
