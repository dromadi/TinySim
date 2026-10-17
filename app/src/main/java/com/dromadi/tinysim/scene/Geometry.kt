package com.dromadi.tinysim.scene

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Original procedural meshes. Positions and flat face normals are uploaded once per EGL context. */
class Geometry private constructor(private val vertices: FloatArray) {
    private var buffer=0
    val triangleCount get()=vertices.size/18
    fun upload() {
        val ids=IntArray(1);GLES20.glGenBuffers(1,ids,0);buffer=ids[0]
        val bytes=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,vertices.size*4,bytes,GLES20.GL_STATIC_DRAW)
    }
    fun draw(position: Int, normal: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,24,0)
        GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/6)
    }
    companion object {
        private fun build(block: MeshBuilder.()->Unit): Geometry { val b=MeshBuilder();b.block();return Geometry(b.values.toFloatArray()) }
        fun cube()=build {
            val v=arrayOf(Vec3(-.5f,-.5f,-.5f),Vec3(.5f,-.5f,-.5f),Vec3(.5f,.5f,-.5f),Vec3(-.5f,.5f,-.5f),Vec3(-.5f,-.5f,.5f),Vec3(.5f,-.5f,.5f),Vec3(.5f,.5f,.5f),Vec3(-.5f,.5f,.5f))
            for(q in arrayOf(intArrayOf(0,3,2,1),intArrayOf(4,5,6,7),intArrayOf(0,4,7,3),intArrayOf(1,2,6,5),intArrayOf(0,1,5,4),intArrayOf(3,7,6,2))) {
                triangle(v[q[0]],v[q[1]],v[q[2]]);triangle(v[q[0]],v[q[2]],v[q[3]])
            }
        }
        fun cylinder(segments: Int=16)=build {
            for(i in 0 until segments) {
                val a=i*2*PI/segments;val b=(i+1)*2*PI/segments
                val p=Vec3((cos(a)*.5).toFloat(),-.5f,(sin(a)*.5).toFloat())
                val q=Vec3((cos(b)*.5).toFloat(),-.5f,(sin(b)*.5).toFloat())
                val pt=p+Vec3(0f,1f,0f);val qt=q+Vec3(0f,1f,0f)
                triangle(p,pt,qt);triangle(p,qt,q)
                triangle(Vec3(0f,.5f,0f),qt,pt);triangle(Vec3(0f,-.5f,0f),p,q)
            }
        }
        // Cone base at y=0, apex at y=1.
        fun cone(segments: Int=18)=build {
            for(i in 0 until segments) {
                val a=i*2*PI/segments;val b=(i+1)*2*PI/segments
                val p=Vec3((cos(a)*.5).toFloat(),0f,(sin(a)*.5).toFloat())
                val q=Vec3((cos(b)*.5).toFloat(),0f,(sin(b)*.5).toFloat())
                triangle(p,Vec3(0f,1f,0f),q);triangle(Vec3(0f,0f,0f),p,q)
            }
        }
        fun sphere(slices: Int=12,stacks: Int=8)=build {
            fun point(i:Int,j:Int):Vec3 {
                val theta=i*2*PI/slices;val phi=-PI/2+j*PI/stacks
                return Vec3((cos(phi)*cos(theta)*.5).toFloat(),(sin(phi)*.5).toFloat(),(cos(phi)*sin(theta)*.5).toFloat())
            }
            for(j in 0 until stacks)for(i in 0 until slices) {
                if(j>0)triangle(point(i,j),point(i,j+1),point(i+1,j))
                if(j<stacks-1)triangle(point(i+1,j),point(i,j+1),point(i+1,j+1))
            }
        }
    }
    private class MeshBuilder {
        val values=ArrayList<Float>()
        fun triangle(a:Vec3,b:Vec3,c:Vec3) {
            val cross=(b-a).cross(c-a)
            if(cross.dot(cross)<1e-12f)return
            val n=cross.normalized()
            for(v in arrayOf(a,b,c)) { values.add(v.x);values.add(v.y);values.add(v.z);values.add(n.x);values.add(n.y);values.add(n.z) }
        }
    }
}
