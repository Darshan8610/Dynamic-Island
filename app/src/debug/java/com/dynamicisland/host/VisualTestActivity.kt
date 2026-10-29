package com.dynamicisland.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dynamicisland.engine.core.model.MediaPlaybackSnapshot
import com.dynamicisland.engine.settings.BehaviorOptions
import com.dynamicisland.engine.ui.FixedIslandCapsule

/** Debug-only test harness. Never registered or packaged in release builds. */
class VisualTestActivity : ComponentActivity() {
    var scenario by mutableStateOf("Idle")
    var taps by mutableIntStateOf(0)
    var plays by mutableIntStateOf(0)
    var outsideTaps by mutableIntStateOf(0)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme()) {
                Column(Modifier.fillMaxSize().background(Color(0xFF202420)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Simulation · $scenario",color=Color.White)
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.size(130.dp,38.dp)) {
                        FixedIslandCapsule(previewState(scenario),MediaPlaybackSnapshot(isActive=scenario=="Music",isPlaying=scenario=="Music",supportedActions=android.media.session.PlaybackState.ACTION_PLAY_PAUSE),false,0f,{taps++},{plays++},BehaviorOptions(reducedMotion=true))
                    }
                    Text("Tap count: $taps",color=Color.White)
                    Spacer(Modifier.height(100.dp))
                    Button(onClick={outsideTaps++}){Text("Underlying touch target")}
                }
            }
        }
    }
}
