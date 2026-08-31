package mo.dev.ctrus.model3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val VERTEX_SHADER = """
    uniform mat4 u_MVPMatrix;
    uniform mat4 u_ModelMatrix;
    attribute vec3 a_Position;
    attribute vec3 a_Normal;
    varying vec3 v_WorldNormal;
    void main() {
        v_WorldNormal = (u_ModelMatrix * vec4(a_Normal, 0.0)).xyz;
        gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
    }
"""

// A single directional light left faces pointed away from it at a harsh 0.45 floor, reading as
// dark/muddy compared to SceneKit's soft, even `autoenablesDefaultLighting` rig on iOS. A second,
// weaker fill light from roughly the opposite side plus a higher ambient floor keeps the model
// bright all around without blowing out the lit side (still clamped to 1.0).
private const val FRAGMENT_SHADER = """
    precision mediump float;
    uniform vec4 u_Color;
    varying vec3 v_WorldNormal;
    void main() {
        vec3 normal = normalize(v_WorldNormal);
        vec3 keyDir = normalize(vec3(0.4, 0.8, 0.6));
        vec3 fillDir = normalize(vec3(-0.5, -0.3, 0.6));
        float keyDiffuse = max(dot(normal, keyDir), 0.0);
        float fillDiffuse = max(dot(normal, fillDir), 0.0);
        float lighting = min(0.6 + 0.3 * keyDiffuse + 0.2 * fillDiffuse, 1.0);
        gl_FragColor = vec4(u_Color.rgb * lighting, u_Color.a);
    }
"""

/**
 * Android equivalent of Ctrus/Components/Dashboard/RotatingModel3DView.swift — a SceneKit view
 * there, plain OpenGL ES 2.0 here (no maintained Android library loads OBJ directly; this is a
 * ~150-line renderer instead of a heavy engine dependency for one small model). Recolors every
 * `V_*` group (the Verde/fruit material) to the current theme color each frame; `B_*` groups
 * (Branco/white dividers) stay fixed, matching the exported VF_Ctrus_Cima_FV.obj/.mtl naming.
 */
class CtrusModelGlRenderer(private val context: Context, private val assetPath: String) : GLSurfaceView.Renderer {
    @Volatile var yawDegrees: Float = 25f
    @Volatile var pitchDegrees: Float = -18f
    @Volatile var accentColor: FloatArray = floatArrayOf(0.95f, 0.65f, 0.25f, 1f)

    private class GpuGroup(val vbo: Int, val isAccent: Boolean, val vertexCount: Int)

    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelMatrixHandle = 0
    private var colorHandle = 0

    private var gpuGroups: List<GpuGroup> = emptyList()
    private val staticColor = floatArrayOf(1f, 1f, 1f, 1f)

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        normalHandle = GLES20.glGetAttribLocation(program, "a_Normal")
        mvpHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")
        modelMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")

        val model = ObjModelCache.get(context, assetPath)
        gpuGroups = model.groups.map { group ->
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
            group.vertexData.position(0)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, group.vertexData.capacity() * 4, group.vertexData, GLES20.GL_STATIC_DRAW)
            GpuGroup(vbo = ids[0], isAccent = group.name.startsWith("V_"), vertexCount = group.triangleCount * 3)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 4f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 10f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (gpuGroups.isEmpty()) return

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, pitchDegrees, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, yawDegrees, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)

        val stride = OBJ_FLOATS_PER_VERTEX * 4
        for (group in gpuGroups) {
            GLES20.glUniform4fv(colorHandle, 1, if (group.isAccent) accentColor else staticColor, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, group.vbo)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, 0)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, group.vertexCount)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Failed to link Ctrus 3D model shader program: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Failed to compile Ctrus 3D model shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }
}
