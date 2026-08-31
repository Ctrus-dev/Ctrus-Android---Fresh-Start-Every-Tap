package mo.dev.ctrus.model3d

import java.nio.FloatBuffer

/**
 * One named group from the OBJ file (Fusion 360 exports each Body as a `g` group), expanded to a
 * flat, non-indexed triangle list: `position.xyz, normal.xyz` repeated per vertex. No texcoords —
 * the model is flat-shaded with a per-group solid color, not textured.
 */
class ObjMeshGroup(val name: String, val vertexData: FloatBuffer, val triangleCount: Int)

class ObjModel(val groups: List<ObjMeshGroup>)

const val OBJ_FLOATS_PER_VERTEX = 6 // 3 position + 3 normal
