package com.dromadi.tinysim.scene

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Original procedural meshes. Positions and normals are uploaded once per EGL context. */
class Geometry private constructor(private val vertices: FloatArray) {
    private var buffer=0
    val triangleCount get()=vertices.size/18
    /** Interleaved position/normal copy, used by tests to check winding and normals. */
    internal fun vertexData()=vertices.copyOf()
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
        if(normal>=0)GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/6)
    }
    companion object {
        private fun build(block: MeshBuilder.()->Unit): Geometry { val b=MeshBuilder();b.block();return Geometry(b.values.toFloatArray()) }
        private fun ring(a: Double,r: Float,y: Float)=Vec3((cos(a)*r).toFloat(),y,(sin(a)*r).toFloat())
        fun cube()=build {
            val v=arrayOf(Vec3(-.5f,-.5f,-.5f),Vec3(.5f,-.5f,-.5f),Vec3(.5f,.5f,-.5f),Vec3(-.5f,.5f,-.5f),Vec3(-.5f,-.5f,.5f),Vec3(.5f,-.5f,.5f),Vec3(.5f,.5f,.5f),Vec3(-.5f,.5f,.5f))
            for(q in arrayOf(intArrayOf(0,3,2,1),intArrayOf(4,5,6,7),intArrayOf(0,4,7,3),intArrayOf(1,2,6,5),intArrayOf(0,1,5,4),intArrayOf(3,7,6,2))) {
                triangle(v[q[0]],v[q[1]],v[q[2]]);triangle(v[q[0]],v[q[2]],v[q[3]])
            }
        }
        /** Unit box with chamfered edges, so paint catches highlights like a pressed metal panel. */
        fun bevelCube(bevel: Float=.08f)=build {
            val h=.5f;val i=h-bevel
            // Each face is an inset quad; edge and corner strips connect neighbouring faces.
            fun face(n: Vec3,u: Vec3,v: Vec3) {
                val c=n*h
                facing(c+u*(-i)+v*(-i),c+u*i+v*i,c+u*i+v*(-i),n)
                facing(c+u*(-i)+v*(-i),c+u*(-i)+v*i,c+u*i+v*i,n)
            }
            val axes=listOf(Vec3(1f,0f,0f),Vec3(0f,1f,0f),Vec3(0f,0f,1f))
            for(a in 0..2)for(s in floatArrayOf(-1f,1f)) {
                val n=axes[a]*s;val u=axes[(a+1)%3];val v=axes[(a+2)%3]
                face(n,u,v)
            }
            // Edge chamfers between every pair of adjacent faces.
            for(a in 0..2) {
                val b=(a+1)%3;val c=(a+2)%3
                for(sa in floatArrayOf(-1f,1f))for(sb in floatArrayOf(-1f,1f)) {
                    fun p(t: Float,onA: Boolean): Vec3 {
                        val x=if(onA) axes[a]*(sa*h)+axes[b]*(sb*i) else axes[a]*(sa*i)+axes[b]*(sb*h)
                        return x+axes[c]*t
                    }
                    val p0=p(-i,true);val p1=p(i,true);val p2=p(i,false);val p3=p(-i,false)
                    val outward=axes[a]*sa+axes[b]*sb
                    facing(p0,p1,p2,outward);facing(p0,p2,p3,outward)
                }
            }
            // Corner triangles.
            for(sx in floatArrayOf(-1f,1f))for(sy in floatArrayOf(-1f,1f))for(sz in floatArrayOf(-1f,1f)) {
                val a=Vec3(sx*h,sy*i,sz*i);val b=Vec3(sx*i,sy*h,sz*i);val c=Vec3(sx*i,sy*i,sz*h)
                facing(a,b,c,Vec3(sx,sy,sz))
            }
        }
        fun cylinder(segments: Int=24)=build {
            for(i in 0 until segments) {
                val a=i*2*PI/segments;val b=(i+1)*2*PI/segments
                val p=ring(a,.5f,-.5f);val q=ring(b,.5f,-.5f)
                val pt=p+Vec3(0f,1f,0f);val qt=q+Vec3(0f,1f,0f)
                val np=Vec3(p.x*2,0f,p.z*2);val nq=Vec3(q.x*2,0f,q.z*2)
                smooth(p,np,pt,np,qt,nq);smooth(p,np,qt,nq,q,nq)
                triangle(Vec3(0f,.5f,0f),qt,pt);triangle(Vec3(0f,-.5f,0f),p,q)
            }
        }
        // Cone base at y=0, apex at y=1.
        fun cone(segments: Int=24)=build {
            val slope=.5f
            for(i in 0 until segments) {
                val a=i*2*PI/segments;val b=(i+1)*2*PI/segments;val m=(a+b)/2
                val p=ring(a,.5f,0f);val q=ring(b,.5f,0f)
                fun n(t: Double)=Vec3(cos(t).toFloat(),slope,sin(t).toFloat()).normalized()
                smooth(p,n(a),Vec3(0f,1f,0f),n(m),q,n(b));triangle(Vec3(0f,0f,0f),p,q)
            }
        }
        fun sphere(slices: Int=24,stacks: Int=14)=build {
            fun point(i:Int,j:Int):Vec3 {
                val theta=i*2*PI/slices;val phi=-PI/2+j*PI/stacks
                return Vec3((cos(phi)*cos(theta)*.5).toFloat(),(sin(phi)*.5).toFloat(),(cos(phi)*sin(theta)*.5).toFloat())
            }
            fun s(a: Vec3,b: Vec3,c: Vec3)=smooth(a,a*2f,b,b*2f,c,c*2f)
            for(j in 0 until stacks)for(i in 0 until slices) {
                if(j>0)s(point(i,j),point(i,j+1),point(i+1,j))
                if(j<stacks-1)s(point(i+1,j),point(i,j+1),point(i+1,j+1))
            }
        }
        /** Natural sand pile: smooth rounded dome with an uneven crest, base at y=0 and top near y=1. */
        fun mound(slices: Int=28,rings: Int=9)=build {
            fun height(r: Float)=(1f-r*r).let { it*it*.7f+it*.3f }
            fun wobble(t: Double)=1f+(sin(t*3+.7)*.06+sin(t*5+2.1)*.035).toFloat()
            fun point(i: Int,j: Int): Vec3 {
                val t=i*2*PI/slices;val r=j/rings.toFloat()
                val w=wobble(t)
                return Vec3((cos(t)*r*.5*w).toFloat(),height(r)*(1f-.04f*(1f-r)*sin(t.toFloat()*2f)),(sin(t)*r*.5*w).toFloat())
            }
            fun normal(i: Int,j: Int): Vec3 {
                if(j==0)return Vec3(0f,1f,0f)
                val c=point(i,j)
                val du=point(i+1,j)-point(i-1,j)
                val dv=point(i,(j+1).coerceAtMost(rings))-point(i,j-1)
                val n=dv.cross(du)
                return if(n.dot(n)<1e-10f) Vec3(c.x,.2f,c.z).normalized() else (if(n.y<0)n*-1f else n).normalized()
            }
            for(j in 0 until rings)for(i in 0 until slices) {
                val a=point(i,j);val b=point(i+1,j);val c=point(i+1,j+1);val d=point(i,j+1)
                if(j>0)smooth(a,normal(i,j),b,normal(i+1,j),c,normal(i+1,j+1))
                smooth(a,normal(i,j),c,normal(i+1,j+1),d,normal(i,j+1))
            }
        }
        /** Large ground patch subdivided so per-vertex lighting and fog stay smooth. */
        fun plane(cells: Int=24)=build {
            for(z in 0 until cells)for(x in 0 until cells) {
                val x0=x/cells.toFloat()-.5f;val x1=(x+1)/cells.toFloat()-.5f
                val z0=z/cells.toFloat()-.5f;val z1=(z+1)/cells.toFloat()-.5f
                triangle(Vec3(x0,0f,z0),Vec3(x0,0f,z1),Vec3(x1,0f,z1));triangle(Vec3(x0,0f,z0),Vec3(x1,0f,z1),Vec3(x1,0f,z0))
            }
        }
    }
    private class MeshBuilder {
        val values=ArrayList<Float>()
        private fun put(v: Vec3,n: Vec3) { values.add(v.x);values.add(v.y);values.add(v.z);values.add(n.x);values.add(n.y);values.add(n.z) }
        fun triangle(a:Vec3,b:Vec3,c:Vec3) {
            val cross=(b-a).cross(c-a)
            if(cross.dot(cross)<1e-12f)return
            val n=cross.normalized()
            put(a,n);put(b,n);put(c,n)
        }
        /** Flat triangle wound counter-clockwise when seen from the outward side. */
        fun facing(a:Vec3,b:Vec3,c:Vec3,outward:Vec3) { if((b-a).cross(c-a).dot(outward)>=0f)triangle(a,b,c) else triangle(a,c,b) }
        /** Per-vertex normals for curved surfaces; winding follows the supplied normals. */
        fun smooth(a:Vec3,na:Vec3,b:Vec3,nb:Vec3,c:Vec3,nc:Vec3) {
            val cross=(b-a).cross(c-a)
            if(cross.dot(cross)<1e-12f)return
            if(cross.dot(na+nb+nc)<0f) { put(a,na.normalized());put(c,nc.normalized());put(b,nb.normalized()) }
            else { put(a,na.normalized());put(b,nb.normalized());put(c,nc.normalized()) }
        }
    }
}
