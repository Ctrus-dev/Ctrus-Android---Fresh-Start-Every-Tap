package mo.dev.ctrus.model3d

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal Wavefront OBJ parser for the one file this app ever loads (the Ctrus fruit icon
 * exported from Fusion 360 as one Body per `g` group, all faces pre-triangulated). Only
 * positions/normals/groups are read — texcoords are parsed (to keep face-token indices aligned)
 * but discarded, since each group is flat-shaded with a single solid color rather than textured.
 *
 * Centers and rescales the whole model to fit a unit sphere so the renderer's camera distance
 * doesn't need to know the source file's units.
 */
object ObjParser {
    fun parse(input: InputStream): ObjModel {
        val positions = ArrayList<Float>(45_000)
        val normals = ArrayList<Float>(60_000)
        val groupVertices = LinkedHashMap<String, ArrayList<Float>>()
        var currentGroup = "default"

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        BufferedReader(InputStreamReader(input)).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty()) continue
                when {
                    line.startsWith("v ") -> {
                        val p = line.substring(2).trim().split(WHITESPACE)
                        val x = p[0].toFloat(); val y = p[1].toFloat(); val z = p[2].toFloat()
                        positions.add(x); positions.add(y); positions.add(z)
                        minX = min(minX, x); maxX = max(maxX, x)
                        minY = min(minY, y); maxY = max(maxY, y)
                        minZ = min(minZ, z); maxZ = max(maxZ, z)
                    }
                    line.startsWith("vn ") -> {
                        val p = line.substring(3).trim().split(WHITESPACE)
                        normals.add(p[0].toFloat()); normals.add(p[1].toFloat()); normals.add(p[2].toFloat())
                    }
                    line.startsWith("g ") -> currentGroup = line.substring(2).trim()
                    line.startsWith("f ") -> {
                        val out = groupVertices.getOrPut(currentGroup) { ArrayList(6_000) }
                        val tokens = line.substring(2).trim().split(WHITESPACE)
                        for (token in tokens) {
                            val idx = token.split('/')
                            val vi = (idx[0].toInt() - 1) * 3
                            val ni = (idx.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toInt()?.minus(1) ?: (idx[0].toInt() - 1)) * 3
                            out.add(positions[vi]); out.add(positions[vi + 1]); out.add(positions[vi + 2])
                            out.add(normals[ni]); out.add(normals[ni + 1]); out.add(normals[ni + 2])
                        }
                    }
                }
            }
        }

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val extent = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.0001f)
        // 2.2, not a literal diameter-2 sphere: matches iOS's centerAndScale, which fits the
        // model's longest axis to 2.9 world units against its own camera/FOV — the equivalent
        // fill fraction for this renderer's 45° FOV / camera-at-4 setup works out to about 2.2.
        val scale = 2.2f / extent

        val groups = groupVertices.map { (name, verts) ->
            var i = 0
            while (i < verts.size) {
                verts[i] = (verts[i] - centerX) * scale
                verts[i + 1] = (verts[i + 1] - centerY) * scale
                verts[i + 2] = (verts[i + 2] - centerZ) * scale
                i += OBJ_FLOATS_PER_VERTEX
            }
            val buffer: FloatBuffer = ByteBuffer.allocateDirect(verts.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            for (f in verts) buffer.put(f)
            buffer.position(0)
            val vertexCount = verts.size / OBJ_FLOATS_PER_VERTEX
            check(vertexCount % 3 == 0) { "Group '$name' has $vertexCount vertices, not a whole number of triangles — is the OBJ non-triangulated?" }
            ObjMeshGroup(name, buffer, triangleCount = vertexCount / 3)
        }

        return ObjModel(groups)
    }

    private val WHITESPACE = Regex("\\s+")
}
