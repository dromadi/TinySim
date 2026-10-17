package com.dromadi.tinysim.scene

import kotlin.math.*

/** Shared camera math keeps picking and GPU projection in the same coordinate system. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(b: Vec3)=Vec3(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: Vec3)=Vec3(x-b.x,y-b.y,z-b.z)
    operator fun times(s: Float)=Vec3(x*s,y*s,z*s)
    fun dot(b: Vec3)=x*b.x+y*b.y+z*b.z
    fun cross(b: Vec3)=Vec3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun normalized(): Vec3 { val length=sqrt(dot(this));require(length>1e-6f);return this*(1f/length) }
}

data class CameraSpec(val eye: Vec3, val target: Vec3, val fov: Float=40f) {
    val forward=(target-eye).normalized()
    val right=forward.cross(Vec3(0f,1f,0f)).normalized()
    val up=right.cross(forward).normalized()
    private val tangent=tan(fov*PI.toFloat()/360f)
    fun project(point: Vec3): Pair<Float,Float> {
        val d=point-eye;val depth=d.dot(forward)
        require(depth>0f)
        return (600f+600f*d.dot(right)/(depth*tangent*WorldCamera.ASPECT)) to
            (337.5f-337.5f*d.dot(up)/(depth*tangent))
    }
    fun armPlane(screenX: Float, screenY: Float): Vec3? {
        val nx=screenX/600f-1f;val ny=1f-screenY/337.5f
        val ray=(forward+right*(nx*WorldCamera.ASPECT*tangent)+up*(ny*tangent)).normalized()
        if(abs(ray.z)<1e-5f)return null
        val distance=-eye.z/ray.z
        return if(distance>0f) eye+ray*distance else null
    }
}
object WorldCamera {
    const val ASPECT=1200f/675f
    val presets=listOf(
        CameraSpec(Vec3(12f,12f,24f),Vec3(-.3f,.7f,0f)),
        CameraSpec(Vec3(0f,15f,25f),Vec3(-.3f,.7f,0f)),
        CameraSpec(Vec3(-12f,11f,24f),Vec3(-.3f,.7f,0f))
    )
    fun spec(index: Int)=presets[index.coerceIn(0,2)]
    fun x(gameX: Float)=(gameX-600f)/60f
    fun y(gameY: Float)=(480f-gameY)/60f
    fun gameX(worldX: Float)=600f+60f*worldX
    fun gameY(worldY: Float)=480f-60f*worldY
}
