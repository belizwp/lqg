package com.belizwp.lqg

import android.graphics.RuntimeShader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import org.intellij.lang.annotations.Language

@Language("AGSL")
val LIQUID_GLASS = """
    uniform float2 resolution;
    uniform float time;
    uniform shader contents;
    uniform float roundness;
    
    half4 main(in float2 fragCoord) {
        // --- normalized UV (0→1) ---
        float2 uv = fragCoord / resolution;
    
        // --- center UV around 0, correct for aspect, and stretch to full extents ---
        float2 centered = uv - 0.5;
        centered *= 2.0;
    
        float a = pow(abs(centered.x), 24.0) + pow(abs(centered.y), 8.0);
    
        float rb1 = clamp((1.0 - a) * 8.0, 0.0, 1.0); // rounded box
        float rb2 = clamp((0.95 - a) * 16.0, 0.0, 1.0)
                  - clamp(pow(0.9 - a, 1.0) * 16.0, 0.0, 1.0); // borders
        float rb3 = clamp((1.5 - a) * 2.0, 0.0, 1.0)
                  - clamp(pow(1.0 - a, 1.0) * 2.0, 0.0, 1.0); // shadow gradient
    
        half4 finalColor;
    
        if (rb1 + rb2 > 0.0) {
            float2 lensUV = ((uv - 0.5) * (1.0 - a * 0.5) + 0.5) * resolution;
    
            // --- Blur ---
            half4 accum = half4(0.0);
            float   count = 0.0;
            for (int x = -4; x <= 4; x++) {
              for (int y = -4; y <= 4; y++) {
                float2 off = float2(x, y) * 0.5 / resolution;
                accum += contents.eval(lensUV + off * resolution);
                count += 1.0;
              }
            }
            half4 blurCol = accum / count;
    
            // --- vertical lighting gradient + shadow fall‑off ---
            float grad1 = clamp((clamp(centered.y * 0.5, 0.0, 0.2) + 0.1) * 0.5, 0.0, 1.0);
            float grad2 = clamp((clamp(-centered.y * 0.5, -1000.0, 0.2) * rb3 + 0.1) * 0.5, 0.0, 1.0);
            float lighting = grad1 + grad2;
    
            // --- combine blur + highlight + border tint ---
            finalColor = blurCol;
            finalColor += half4(rb1 * lighting);
            finalColor += half4(rb2 * 0.3);
            finalColor = clamp(finalColor, 0.0, 1.0);
    
        } else {
            // --- outside the box: original contents ---
            finalColor = contents.eval(uv * resolution);
        }
    
        return finalColor;
    }
""".trimIndent()

@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState = rememberHazeState(),
    time: () -> Float = { 0f },
) = composed {
    val shader = remember { RuntimeShader(LIQUID_GLASS) }
    clip(RoundedCornerShape(20.dp))
        .onSizeChanged {
            shader.setFloatUniform(
                "resolution",
                it.width.toFloat(),
                it.height.toFloat()
            )
        }
        .graphicsLayer {
            shader.setFloatUniform("time", time())
            renderEffect = android.graphics.RenderEffect
                .createRuntimeShaderEffect(
                    shader,
                    "contents"
                )
                .asComposeRenderEffect()
        }
        .hazeEffect(hazeState) {
            blurRadius = 2.dp
        }
}
