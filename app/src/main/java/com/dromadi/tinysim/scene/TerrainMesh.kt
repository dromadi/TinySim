package com.dromadi.tinysim.scene

import android.opengl.GLES20
import com.dromadi.tinysim.Terrain
import com.dromadi.tinysim.WorldMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Deforming heightfield mesh. Only quads whose heights or normals depend on a changed cell are
 * recomputed, and only those quad rows are re-uploaded, so digging and bulldozing never rebuild
 * the whole 43k-triangle ground on the GL thread.
 */
class TerrainMesh: Mesh {
    val vertices=FloatArray(QUADS_X*QUADS_Z*FLOATS_PER_QUAD)
    private var earth=IntArray(0);private var sand=IntArray(0)
    private val heights=FloatArray(POINTS_X*POINTS_Z)
    private val normals=FloatArray(POINTS_X*POINTS_Z*3)
    private var buffer=0
    private var direct:FloatBuffer?=null
    private var dirtyFrom=Int.MAX_VALUE;private var dirtyTo=-1
    val triangleCount get()=QUADS_X*QUADS_Z*2

    /** Brings the mesh in line with the world; returns the quad rows that were recomputed. */
    fun update(earth:IntArray,sand:IntArray):IntRange? {
        if(this.earth.size!=earth.size || this.sand.size!=sand.size) {
            this.earth=earth.copyOf();this.sand=sand.copyOf()
            rebuild(0,QUADS_X-1,0,QUADS_Z-1);return 0 until QUADS_Z
        }
        var c0=Int.MAX_VALUE;var c1=-1;var r0=Int.MAX_VALUE;var r1=-1
        for(i in earth.indices)if(earth[i]!=this.earth[i] || sand[i]!=this.sand[i]) {
            val c=i%WorldMap.COLUMNS;val r=i/WorldMap.COLUMNS
            c0=min(c0,c);c1=max(c1,c);r0=min(r0,r);r1=max(r1,r)
        }
        if(c1<0)return null
        earth.copyInto(this.earth);sand.copyInto(this.sand)
        // A cell reaches points up to 1.5 cells away through bilinear interpolation, plus the
        // 0.05 normal sampling offset; quads touching those points are refreshed.
        val qx0=(floor((c0-.6f)/STEP).toInt()-1).coerceIn(0,QUADS_X-1);val qx1=ceil((c1+1.6f)/STEP).toInt().coerceIn(0,QUADS_X-1)
        val qz0=(floor((r0-.6f)/STEP).toInt()-1).coerceIn(0,QUADS_Z-1);val qz1=ceil((r1+1.6f)/STEP).toInt().coerceIn(0,QUADS_Z-1)
        rebuild(qx0,qx1,qz0,qz1);return qz0..qz1
    }
    private fun rebuild(qx0:Int,qx1:Int,qz0:Int,qz1:Int) {
        for(pz in qz0..qz1+1)for(px in qx0..qx1+1) {
            val x=WorldMap.MIN_X+px*STEP;val z=WorldMap.MIN_Z+pz*STEP;val p=pz*POINTS_X+px
            heights[p]=h(x,z)
            val nx=h(x-.05f,z)-h(x+.05f,z);val nz=h(x,z-.05f)-h(x,z+.05f)
            val length=sqrt(nx*nx+.01f+nz*nz)
            normals[p*3]=nx/length;normals[p*3+1]=.1f/length;normals[p*3+2]=nz/length
        }
        for(qz in qz0..qz1)for(qx in qx0..qx1) {
            var o=(qz*QUADS_X+qx)*FLOATS_PER_QUAD
            val a=qz*POINTS_X+qx;val b=a+POINTS_X;val c=b+1;val d=a+1
            // Same winding rule as Geometry.smooth: counter-clockwise against the summed normals.
            o=triangle(o,a,b,c);triangle(o,a,c,d)
        }
        dirtyFrom=min(dirtyFrom,qz0);dirtyTo=max(dirtyTo,qz1)
    }
    private fun h(x:Float,z:Float)=Terrain.height(earth,sand,x,z)
    private fun px(p:Int)=WorldMap.MIN_X+p%POINTS_X*STEP
    private fun pz(p:Int)=WorldMap.MIN_Z+p/POINTS_X*STEP
    private fun triangle(start:Int,a:Int,b:Int,c:Int):Int {
        val ux=px(b)-px(a);val uy=heights[b]-heights[a];val uz=pz(b)-pz(a)
        val vx=px(c)-px(a);val vy=heights[c]-heights[a];val vz=pz(c)-pz(a)
        val cx=uy*vz-uz*vy;val cy=uz*vx-ux*vz;val cz=ux*vy-uy*vx
        val sum=cx*(normals[a*3]+normals[b*3]+normals[c*3])+cy*(normals[a*3+1]+normals[b*3+1]+normals[c*3+1])+cz*(normals[a*3+2]+normals[b*3+2]+normals[c*3+2])
        var o=put(start,a)
        if(sum<0f) { o=put(o,c);o=put(o,b) } else { o=put(o,b);o=put(o,c) }
        return o
    }
    private fun put(start:Int,p:Int):Int {
        vertices[start]=px(p);vertices[start+1]=heights[p];vertices[start+2]=pz(p)
        vertices[start+3]=normals[p*3];vertices[start+4]=normals[p*3+1];vertices[start+5]=normals[p*3+2]
        return start+6
    }
    /** Uploads the whole mesh after a context change, otherwise just the dirty quad rows. */
    fun upload() {
        val data=direct ?: ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { direct=it }
        if(buffer==0) {
            val ids=IntArray(1);GLES20.glGenBuffers(1,ids,0);buffer=ids[0]
            data.position(0);data.put(vertices).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,vertices.size*4,data,GLES20.GL_DYNAMIC_DRAW)
        } else if(dirtyTo>=0) {
            val start=dirtyFrom*QUADS_X*FLOATS_PER_QUAD;val count=(dirtyTo-dirtyFrom+1)*QUADS_X*FLOATS_PER_QUAD
            data.position(start);data.put(vertices,start,count).position(start)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,start*4,count*4,data)
        }
        dirtyFrom=Int.MAX_VALUE;dirtyTo=-1
    }
    fun contextLost() { buffer=0 }
    override fun draw(position:Int,normal:Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,24,0)
        if(normal>=0)GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/6)
    }
    companion object {
        const val STEP=.25f
        const val QUADS_X=248;const val QUADS_Z=88
        const val POINTS_X=QUADS_X+1;const val POINTS_Z=QUADS_Z+1
        const val FLOATS_PER_QUAD=36
    }
}
