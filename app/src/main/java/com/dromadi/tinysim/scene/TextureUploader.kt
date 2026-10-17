package com.dromadi.tinysim.scene

/** Reads bundled assets and uploads an image into the currently bound GL_TEXTURE_2D (level 0). */
interface TextureUploader {
    fun readText(path:String):String?
    fun upload(path:String):Boolean
}
