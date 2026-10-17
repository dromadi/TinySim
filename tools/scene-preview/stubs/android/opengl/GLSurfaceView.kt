package android.opengl
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
class GLSurfaceView { interface Renderer { fun onSurfaceCreated(gl:GL10?,config:EGLConfig?); fun onSurfaceChanged(gl:GL10?,w:Int,h:Int); fun onDrawFrame(gl:GL10?) } }
