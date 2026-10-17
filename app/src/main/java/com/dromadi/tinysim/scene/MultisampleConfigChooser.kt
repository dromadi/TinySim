package com.dromadi.tinysim.scene

import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** Prefers 4x MSAA for smooth vehicle edges and falls back to the original RGBA8888/16-bit depth config. */
class MultisampleConfigChooser: GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10,display: EGLDisplay): EGLConfig {
        for(samples in intArrayOf(4,0)) {
            val attributes=intArrayOf(
                EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_ALPHA_SIZE,8,
                EGL10.EGL_DEPTH_SIZE,16,EGL10.EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SAMPLE_BUFFERS,if(samples>0)1 else 0,EGL10.EGL_SAMPLES,samples,
                EGL10.EGL_NONE
            )
            val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
            if(egl.eglChooseConfig(display,attributes,configs,1,count) && count[0]>0 && configs[0]!=null) {
                Log.i("TinySim3D","EGL config with ${samples}x MSAA")
                return configs[0]!!
            }
        }
        throw IllegalStateException("No OpenGL ES 2.0 RGBA8888 config")
    }
    private companion object { const val EGL_OPENGL_ES2_BIT=4 }
}
