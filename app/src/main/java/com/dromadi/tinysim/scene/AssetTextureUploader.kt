package com.dromadi.tinysim.scene

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

/** Android implementation: decodes JPGs from the APK assets straight into GL textures. */
class AssetTextureUploader(private val assets:AssetManager):TextureUploader {
    override fun readText(path:String)=runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
    override fun upload(path:String):Boolean {
        val bitmap=runCatching {
            assets.open(path).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888;inScaled=false }) }
        }.getOrNull() ?: return false.also { Log.w("TinySim3D","Texture $path could not be decoded") }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
        bitmap.recycle()
        return true
    }
}
