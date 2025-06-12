package com.belizwp.lqg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.belizwp.lqg.ui.theme.LqgTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LqgTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val hazeState = rememberHazeState()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentScale = ContentScale.Crop,
            painter = painterResource(R.drawable.sample_bg),
            contentDescription = null,
        )

        var displacementScale by remember { mutableFloatStateOf(80f) }
        var aberrationIntensity by remember { mutableFloatStateOf(10f) }
        var blurAmount by remember { mutableFloatStateOf(8f) }
        var cornerRadius by remember { mutableFloatStateOf(50f) }

        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(200.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .clip(RoundedCornerShape(cornerRadius.dp))
                .border(
                    // Could use custom brush
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(cornerRadius.dp),
                )
                .liquidGlass(
                    hazeState = hazeState,
                    displacementScale = displacementScale,
                    aberrationIntensity = aberrationIntensity,
                    blurAmount = blurAmount.dp,
                )
        )

        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                .padding(16.dp)
        ) {
            Text(text = "Displacement Scale: $displacementScale")
            Slider(
                value = displacementScale,
                onValueChange = { displacementScale = it },
                valueRange = 0f..200f,
            )

            Text(text = "Blur Amount: $blurAmount")
            Slider(
                value = blurAmount,
                onValueChange = { blurAmount = it },
                valueRange = 0f..20f,
            )

            Text(text = "Aberration Intensity: $aberrationIntensity")
            Slider(
                value = aberrationIntensity,
                onValueChange = { aberrationIntensity = it },
                valueRange = 0f..50f,
            )

            Text(text = "Corner Radius: $cornerRadius")
            Slider(
                value = cornerRadius,
                onValueChange = { cornerRadius = it },
                valueRange = 0f..100f,
            )
        }
    }
}
