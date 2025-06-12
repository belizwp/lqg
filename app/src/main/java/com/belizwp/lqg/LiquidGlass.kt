package com.belizwp.lqg

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import org.intellij.lang.annotations.Language
import androidx.core.graphics.createBitmap

@Language("AGSL")
val LIQUID_GLASS = """
    uniform float2 resolution;
    uniform shader contents;
    uniform shader displacementMap;
    uniform float displacementScale;
    uniform float aberrationIntensity;
    
    // Main liquid glass effect using displacement map
    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        
        // Sample the displacement map
        half4 displacementColor = displacementMap.eval(fragCoord);
        
        // Extract displacement values from red and green channels
        // Convert from [0,1] range to [-1,1] range
        float2 displacement = (displacementColor.rg - 0.5) * 2.0;
        
        // Apply displacement scale
        displacement *= displacementScale * 0.01;
        
        // Create edge mask for stronger effects at edges
        float2 center = float2(0.5, 0.5);
        float2 pos = uv - center;
        float edgeDistance = length(pos);
        float edgeMask = smoothstep(0.2, 0.8, edgeDistance);
        
        // Apply edge masking to displacement
        displacement *= edgeMask;
        
        // Calculate chromatic aberration offsets
        float aberrationOffset = aberrationIntensity * 0.002 * edgeMask;
        
        // Sample RGB channels with different displacement amounts for chromatic aberration
        float2 redUV = uv + displacement + float2(aberrationOffset, 0.0);
        float2 greenUV = uv + displacement * 0.95; // Slightly less displacement
        float2 blueUV = uv + displacement * 0.9 - float2(aberrationOffset, 0.0);
        
        // Ensure UV coordinates are within bounds
        redUV = clamp(redUV, float2(0.0), float2(1.0));
        greenUV = clamp(greenUV, float2(0.0), float2(1.0));
        blueUV = clamp(blueUV, float2(0.0), float2(1.0));
        
        // Sample the content with displacement
        half4 redChannel = contents.eval(redUV * resolution);
        half4 greenChannel = contents.eval(greenUV * resolution);
        half4 blueChannel = contents.eval(blueUV * resolution);
        
        // Combine channels for chromatic aberration
        half4 distortedColor = half4(
            redChannel.r,
            greenChannel.g,
            blueChannel.b,
            greenChannel.a
        );
        
        // For areas with minimal displacement, use original content
        float displacementIntensity = length(displacement);
        float mixFactor = smoothstep(0.0, 0.05, displacementIntensity);
        
        half4 originalColor = contents.eval(fragCoord);
        
        return mix(originalColor, distortedColor, mixFactor);
    }
""".trimIndent()

@Composable
fun Modifier.liquidGlass(
    hazeState: HazeState = rememberHazeState(),
    displacementScale: Float = 25f,
    aberrationIntensity: Float = 2f,
    blurAmount: Dp = 12.dp,
) = composed {
    val shader = remember { RuntimeShader(LIQUID_GLASS) }
    onSizeChanged {
        shader.setFloatUniform(
            "resolution",
            it.width.toFloat(),
            it.height.toFloat()
        )
        val displacementBitmap = DisplacementMapGenerator.createLiquidGlassDisplacementMap(
            it.width,
            it.height
        )
        val displacementMap = BitmapShader(
            displacementBitmap,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        )
        shader.setInputShader("displacementMap", displacementMap)
    }
        .graphicsLayer {
            shader.setFloatUniform("displacementScale", displacementScale)
            shader.setFloatUniform("aberrationIntensity", aberrationIntensity)
            renderEffect = android.graphics.RenderEffect
                .createRuntimeShaderEffect(
                    shader,
                    "contents"
                )
                .asComposeRenderEffect()
            clip = true
        }
        .hazeEffect(hazeState) {
            blurRadius = blurAmount
        }
}

// Displacement map generator
class DisplacementMapGenerator {
    companion object {
        fun createLiquidGlassDisplacementMap(width: Int, height: Int): Bitmap {
            val bitmap = createBitmap(width, height)
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val uv = Pair(x.toFloat() / width, y.toFloat() / height)
                    val pos = liquidGlassFragment(uv)

                    // Calculate displacement
                    val dx = pos.first * width - x
                    val dy = pos.second * height - y

                    // Normalize to [0, 255] range
                    val red = ((dx / width + 0.5f) * 255f).coerceIn(0f, 255f).toInt()
                    val green = ((dy / height + 0.5f) * 255f).coerceIn(0f, 255f).toInt()
                    val blue = green // Use green for blue channel too
                    val alpha = 255

                    val pixelIndex = y * width + x
                    pixels[pixelIndex] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        }

        private fun liquidGlassFragment(uv: Pair<Float, Float>): Pair<Float, Float> {
            val ix = uv.first - 0.5f
            val iy = uv.second - 0.5f
            val distanceToEdge = roundedRectSDF(ix, iy, 0.3f, 0.2f, 0.6f)
            val displacement = smoothStep(0.8f, 0.0f, distanceToEdge - 0.15f)
            val scaled = smoothStep(0.0f, 1.0f, displacement)
            return Pair(ix * scaled + 0.5f, iy * scaled + 0.5f)
        }

        private fun roundedRectSDF(
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float
        ): Float {
            val qx = kotlin.math.abs(x) - width + radius
            val qy = kotlin.math.abs(y) - height + radius
            return kotlin.math.min(kotlin.math.max(qx, qy), 0.0f) +
                    kotlin.math.sqrt(
                        kotlin.math.max(qx, 0.0f) * kotlin.math.max(qx, 0.0f) +
                                kotlin.math.max(qy, 0.0f) * kotlin.math.max(qy, 0.0f)
                    ) - radius
        }

        private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
            return t * t * (3.0f - 2.0f * t)
        }
    }
}
