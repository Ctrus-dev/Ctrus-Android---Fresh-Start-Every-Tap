package mo.dev.ctrus.ui.dashboard

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import mo.dev.ctrus.model3d.CTRUS_MODEL_ASSET_PATH
import mo.dev.ctrus.model3d.CtrusModelGlRenderer

private const val DEGREES_PER_PIXEL = 0.4f

/** Matches RotatingModel3DView.swift's `var size: CGFloat = 380` default exactly (1pt ≈ 1dp). */
val DefaultModel3DSize = 380.dp

/**
 * Android equivalent of RotatingModel3DView.swift: free drag-to-rotate (yaw unrestricted, pitch
 * clamped so the model can't be flipped upside down disorientingly), theme-colored fruit. See
 * [CtrusModelGlRenderer] for why this is a small hand-rolled OpenGL ES renderer instead of a
 * glTF-based engine.
 */
@Composable
fun RotatingModel3DView(themeColor: Color, modifier: Modifier = Modifier.size(DefaultModel3DSize)) {
    val rendererRef = remember { mutableStateOf<CtrusModelGlRenderer?>(null) }
    val glSurfaceViewRef = remember { mutableStateOf<GLSurfaceView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val renderer = CtrusModelGlRenderer(context, CTRUS_MODEL_ASSET_PATH)
            rendererRef.value = renderer

            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                setZOrderOnTop(true)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

                var lastX = 0f
                var lastY = 0f
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.x
                            lastY = event.y
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            renderer.yawDegrees += dx * DEGREES_PER_PIXEL
                            renderer.pitchDegrees = (renderer.pitchDegrees + dy * DEGREES_PER_PIXEL).coerceIn(-80f, 80f)
                            lastX = event.x
                            lastY = event.y
                            requestRender()
                        }
                    }
                    true
                }

                glSurfaceViewRef.value = this
            }
        },
        // A setZOrderOnTop SurfaceView composites via its own SurfaceFlinger layer, outside the
        // normal View z-order — hiding it (visibility = GONE) alone still leaves a window where
        // the last composited frame can linger on real hardware, because that layer isn't torn
        // down in lockstep with the View tree. onPause() synchronously blocks the render thread
        // before it draws another frame, which combined with hiding the view gives Settings the
        // best chance of a clean handoff without whatever frame was already in flight showing
        // through. (NavHost also has no animated transition between routes for the same reason:
        // see CtrusNavHost — that removes the other source of lingering, an animated crossfade
        // keeping "home" composed for its duration.)
        onRelease = { view ->
            view.onPause()
            view.visibility = android.view.View.GONE
        },
    )

    LaunchedEffect(themeColor) {
        val argb = themeColor.toArgb()
        rendererRef.value?.accentColor = floatArrayOf(
            android.graphics.Color.red(argb) / 255f,
            android.graphics.Color.green(argb) / 255f,
            android.graphics.Color.blue(argb) / 255f,
            1f,
        )
        glSurfaceViewRef.value?.requestRender()
    }
}
