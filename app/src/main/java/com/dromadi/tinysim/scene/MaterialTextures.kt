package com.dromadi.tinysim.scene

import android.opengl.GLES20 as GL
import android.util.Log
import org.json.JSONObject

/**
 * CC0 photo materials (Poly Haven) as albedo, normal and roughness textures. Loaded once per EGL
 * context on the GL thread; slots match the material table in SceneRenderer3D.
 */
class MaterialTextures(private val uploader:TextureUploader?) {
    private val albedo=IntArray(SLOTS.size);private val normal=IntArray(SLOTS.size);private val rough=IntArray(SLOTS.size)
    /** Mean sRGB albedo per slot, used to recolour a texture toward the game palette. */
    val mean=Array(SLOTS.size) { floatArrayOf(.5f,.5f,.5f) }
    var loaded=false
        private set
    fun load() {
        loaded=false
        val source=uploader ?: return
        val index=source.readText("textures/index.json")?.let { runCatching { JSONObject(it).getJSONObject("materials") }.getOrNull() }
        if(index==null) { Log.w("TinySim3D","Texture index missing; using procedural surfaces");return }
        val anisotropic=(GL.glGetString(GL.GL_EXTENSIONS) ?: "").contains("GL_EXT_texture_filter_anisotropic")
        var ok=true
        SLOTS.forEachIndexed { i,slot->
            index.optJSONObject(slot)?.optJSONArray("mean")?.let { m-> for(c in 0..2)mean[i][c]=m.getDouble(c).toFloat().coerceAtLeast(.02f) }
            albedo[i]=texture("textures/${slot}_albedo.jpg",anisotropic,source)
            normal[i]=texture("textures/${slot}_normal.jpg",anisotropic,source)
            rough[i]=texture("textures/${slot}_rough.jpg",anisotropic,source)
            if(albedo[i]==0 || normal[i]==0 || rough[i]==0)ok=false
        }
        loaded=ok
        Log.i("TinySim3D","Material textures ${if(ok)"loaded" else "incomplete"}: ${SLOTS.size} materials")
    }
    private fun texture(path:String,anisotropic:Boolean,source:TextureUploader):Int {
        val ids=IntArray(1);GL.glGenTextures(1,ids,0)
        GL.glBindTexture(GL.GL_TEXTURE_2D,ids[0])
        if(!source.upload(path)) { GL.glDeleteTextures(1,ids,0);return 0 }
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_REPEAT)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_REPEAT)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR_MIPMAP_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
        if(anisotropic)GL.glTexParameterf(GL.GL_TEXTURE_2D,TEXTURE_MAX_ANISOTROPY,8f)
        GL.glGenerateMipmap(GL.GL_TEXTURE_2D)
        return ids[0]
    }
    /** Binds a slot's maps to texture units 1–3 (unit 0 holds the shadow map). */
    fun bind(slot:Int) {
        GL.glActiveTexture(GL.GL_TEXTURE1);GL.glBindTexture(GL.GL_TEXTURE_2D,albedo[slot])
        GL.glActiveTexture(GL.GL_TEXTURE2);GL.glBindTexture(GL.GL_TEXTURE_2D,normal[slot])
        GL.glActiveTexture(GL.GL_TEXTURE3);GL.glBindTexture(GL.GL_TEXTURE_2D,rough[slot])
        GL.glActiveTexture(GL.GL_TEXTURE0)
    }
    companion object {
        val SLOTS=listOf("sand","soil","grass","asphalt","concrete","paint","wood","plaster","roof")
        const val SAND=0;const val SOIL=1;const val GRASS=2;const val ASPHALT=3;const val CONCRETE=4
        const val PAINT=5;const val WOOD=6;const val PLASTER=7;const val ROOF=8
        private const val TEXTURE_MAX_ANISOTROPY=0x84FE
    }
}
