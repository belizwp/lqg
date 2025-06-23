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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import org.intellij.lang.annotations.Language
import kotlin.math.min

@Language("AGSL")
val LIQUID_GLASS = """
    uniform float2 resolution;
    uniform shader contents;
    uniform shader displacementMap;
    uniform float aberrationIntensity;
    
    // Main liquid glass effect using displacement map
    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        
        // Sample the displacement map
        half4 displacementColor = displacementMap.eval(fragCoord);
        
        // Extract displacement values from red and green channels
        // Convert from [0,1] range to [-1,1] range
        float2 displacement = (displacementColor.rg - 0.5) * 2.0;
        
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
    displacementScale: Float = 1f,
    aberrationIntensity: Float = 2f,
    blurAmount: Dp = 12.dp,
    cornerRadius: Dp = 0.dp,
    thickness: Dp = 20.dp,
) = composed {
    val shader = remember { RuntimeShader(LIQUID_GLASS) }
    graphicsLayer {
        shader.setFloatUniform(
            "resolution",
            size.width,
            size.height
        )
        val cornerRadiusPx = cornerRadius.toPx().coerceAtMost(min(size.width, size.height) / 2f)
        val effectThicknessPx = thickness.toPx()
        val displacementBitmap = DisplacementMapGenerator.createLiquidGlassDisplacementMap(
            width = size.width.toInt(),
            height = size.height.toInt(),
            cornerRadius = cornerRadiusPx,
            effectThickness = effectThicknessPx,
            displacementScale = displacementScale,
        )
        val displacementMap = BitmapShader(
            displacementBitmap,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        )
        shader.setInputShader("displacementMap", displacementMap)
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
        fun createLiquidGlassDisplacementMap(
            width: Int,
            height: Int,
            cornerRadius: Float = 0f,
            effectThickness: Float = 12f,
            displacementScale: Float = 1f,
        ): Bitmap {
            val bitmap = createBitmap(width, height)
            val pixels = IntArray(width * height)
            val aspect = width.toFloat() / height.toFloat()

            // Normalize corner radius and thickness based on the smaller dimension
            // to make them resolution-independent and consistent with the SDF coordinate space.
            val smallerDimension = kotlin.math.min(width, height).toFloat()
            val normalizedCornerRadius = cornerRadius / smallerDimension
            val normalizedThickness = effectThickness / smallerDimension

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val uv = Pair(x.toFloat() / width, y.toFloat() / height)
                    val pos =
                        liquidGlassFragment(
                            uv = uv,
                            aspect = aspect,
                            cornerRadius = normalizedCornerRadius,
                            thickness = normalizedThickness,
                            displacementScale = displacementScale,
                        )

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

        private fun liquidGlassFragment(
            uv: Pair<Float, Float>,
            aspect: Float,
            cornerRadius: Float,
            thickness: Float,
            displacementScale: Float = 1f,
        ): Pair<Float, Float> {
            var ix = uv.first - 0.5f
            var iy = uv.second - 0.5f

            // Aspect-correct the coordinates for the SDF
            val boxHalfSize: Pair<Float, Float>
            if (aspect > 1.0) {
                ix *= aspect
                boxHalfSize = Pair(0.5f * aspect, 0.5f)
            } else {
                iy /= aspect
                boxHalfSize = Pair(0.5f, 0.5f / aspect)
            }

            // To prevent visual artifacts, we must clamp the radius and thickness
            // to ensure the geometry remains valid.

            // 1. A corner radius cannot be larger than the smallest half-dimension of the rectangle.
            val maxAllowedRadius = kotlin.math.min(boxHalfSize.first, boxHalfSize.second)
            val clampedCornerRadius = cornerRadius.coerceAtMost(maxAllowedRadius)

            // 2. The thickness cannot be larger than the smallest half-dimension, otherwise
            //    the inner rectangle would collapse and cause artifacts.
            val clampedThickness = thickness.coerceAtMost(maxAllowedRadius)

            // Define properties for the inner rounded rectangle using the clamped values.
            val innerBoxHalfWidth = boxHalfSize.first - clampedThickness
            val innerBoxHalfHeight = boxHalfSize.second - clampedThickness
            // The inner corner radius also shrinks. Because we clamped the inputs, this
            // will always result in a valid radius for the inner rectangle.
            val innerCornerRadius = (clampedCornerRadius - clampedThickness).coerceAtLeast(0f)

            // Calculate SDF for both the outer and the new inner rounded rectangle.
            val sdfOuter =
                roundedRectSDF(ix, iy, boxHalfSize.first, boxHalfSize.second, clampedCornerRadius)
            val sdfInner =
                roundedRectSDF(ix, iy, innerBoxHalfWidth, innerBoxHalfHeight, innerCornerRadius)

            // The effect is applied in the band between the inner and outer rectangles.
            // We calculate a normalized value 'd' representing the position within this band,
            // where d=0 at the inner edge and d=1 at the outer edge.
            val denominator = sdfInner - sdfOuter
            val d = if (denominator > 1e-6f) { // Avoid division by zero
                (sdfInner / denominator).coerceIn(0f, 1f)
            } else {
                0f
            }

            // Use smoothstep on 'd' to create a smooth gradient from the inner to the outer edge.
            val effectIntensity = smoothStep(0f, 1f, d)

            // Calculate a scaling factor based on the effect intensity to create the bulge
            val displacementAmount = displacementScale * effectIntensity
            val scaleFactor = 1.0f - displacementAmount

            // Apply scaling. We must un-correct the aspect ratio before returning.
            var newIx = ix * scaleFactor
            var newIy = iy * scaleFactor
            if (aspect > 1.0) {
                newIx /= aspect
            } else {
                newIy *= aspect
            }

            // Convert back to [0, 1] UV space
            return Pair(newIx + 0.5f, newIy + 0.5f)
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
