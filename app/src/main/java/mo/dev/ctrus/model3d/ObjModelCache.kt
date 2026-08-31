package mo.dev.ctrus.model3d

import android.content.Context

/** Bundled asset path for the Ctrus mascot mesh — shared by [ObjModelCache] and the renderer. */
const val CTRUS_MODEL_ASSET_PATH = "models/VF_Ctrus_Cima_FV.obj"

/**
 * Caches the parsed Ctrus mascot mesh at process scope. The OBJ text is a few MB and re-parsing
 * it from scratch — reading every line, `toFloat()`-ing every coordinate — is what made the
 * model visibly slow to appear on *every* visit to Home, not just the first: Compose Navigation
 * disposes the `GLSurfaceView` when you navigate away and creates a brand new one (with a fresh
 * `onSurfaceCreated` call) each time you come back, so without this cache the full text parse ran
 * again on every single visit. Parsed once per process here; each renderer still uploads its own
 * VBOs from the cached float data, since a GL context — and the buffers bound to it — can't
 * outlive its own `GLSurfaceView`.
 */
object ObjModelCache {
    @Volatile private var cached: ObjModel? = null

    fun get(context: Context, assetPath: String = CTRUS_MODEL_ASSET_PATH): ObjModel {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val parsed = context.applicationContext.assets.open(assetPath).use { ObjParser.parse(it) }
            cached = parsed
            return parsed
        }
    }
}
