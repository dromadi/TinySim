package com.dromadi.tinysim.scene

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Anything the renderer can draw from an interleaved position/normal buffer. */
interface Mesh { fun draw(position: Int, normal: Int) }

/** Original procedural meshes. Positions and normals are uploaded once per EGL context. */
class Geometry private constructor(private val vertices: FloatArray): Mesh {
    private var buffer=0
    val triangleCount get()=vertices.size/18
    /** Interleaved position/normal copy, used by tests to check winding and normals. */
    internal fun vertexData()=vertices.copyOf()
    fun upload() {
        if(buffer==0) { val ids=IntArray(1);GLES20.glGenBuffers(1,ids,0);buffer=ids[0] }
        val bytes=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,vertices.size*4,bytes,GLES20.GL_STATIC_DRAW)
    }
    fun contextLost() { buffer=0 }
    override fun draw(position: Int, normal: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffer)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,24,0)
        if(normal>=0)GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/6)
    }
    companion object {
        private fun build(block: MeshBuilder.()->Unit): Geometry { val b=MeshBuilder();b.block();return Geometry(b.values.toFloatArray()) }
        private fun ring(a: Double,r: Float,y: Float)=Vec3((cos(a)*r).toFloat(),y,(sin(a)*r).toFloat())
        /** Hollow funnel and helical ribbon, authored at full size; no texture or bitmap padding. */
        fun windFunnel(ribbon:Boolean=false)=build {
            val segments=if(ribbon)10 else 48
            fun point(row:Int,col:Int):Vec3 {
                val t=row/64f;val a=col.toFloat()/segments*(if(ribbon)1.15f else 2f*PI.toFloat())+t*13f
                val radius=.32f+2.1f*t.pow(1.6f)
                return Vec3(cos(a)*radius+sin(t*3f)*.35f,t*6.6f,sin(a)*radius)
            }
            for(row in 0 until 64)for(col in 0 until segments) {
                val a=point(row,col);val b=point(row+1,col);val c=point(row+1,col+1);val d=point(row,col+1)
                triangle(a,b,c);triangle(a,c,d)
            }
        }
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
        /**
         * Off-road tyre around the Y axis (diameter 1, width 1): rounded sidewalls and shoulders,
         * and a faceted tread band whose chevron lugs stand proud of the grooves.
         */
        fun tyre(lugs: Int=18)=build {
            // Sidewall profile (radius, |y|) from the bead out to the tread shoulder.
            val profile=listOf(.33f to .44f,.37f to .47f,.42f to .475f,.455f to .455f,.475f to .42f,.47f to .38f)
            fun profileNormal(k: Int): Pair<Float,Float> {
                val a=profile[(k-1).coerceAtLeast(0)];val b=profile[(k+1).coerceAtMost(profile.lastIndex)]
                val dr=b.first-a.first;val dy=b.second-a.second;val l=sqrt(dr*dr+dy*dy)
                return dy/l to -dr/l
            }
            val around=lugs*8
            for(side in floatArrayOf(-1f,1f))for(i in 0 until around) {
                val a=i*2*PI/around;val b=(i+1)*2*PI/around
                for(k in 0 until profile.lastIndex) {
                    val (r0,y0)=profile[k];val (r1,y1)=profile[k+1]
                    val (n0r,n0y)=profileNormal(k);val (n1r,n1y)=profileNormal(k+1)
                    fun p(t: Double,r: Float,y: Float)=Vec3((cos(t)*r).toFloat(),y*side,(sin(t)*r).toFloat())
                    fun n(t: Double,nr: Float,ny: Float)=Vec3((cos(t)*nr).toFloat(),ny*side,(sin(t)*nr).toFloat())
                    smooth(p(a,r0,y0),n(a,n0r,n0y),p(b,r0,y0),n(b,n0r,n0y),p(b,r1,y1),n(b,n1r,n1y))
                    smooth(p(a,r0,y0),n(a,n0r,n0y),p(b,r1,y1),n(b,n1r,n1y),p(a,r1,y1),n(a,n1r,n1y))
                }
            }
            // Tread band: cells are lug tops or grooves; walls close every height step.
            val across=8;val half=.38f;val groove=.47f;val top=.5f
            fun lug(i: Int,j: Int): Boolean {
                val v=((j+.5f)/across)*2f-1f
                val phase=(i+.5f)/around*lugs+(if(v>0f).5f else 0f)+.45f*abs(v)
                return abs(v)>.12f && phase-floor(phase)<.5f
            }
            fun radius(i: Int,j: Int)=if(j<0 || j>=across)groove else if(lug((i+around)%around,j))top else groove
            fun q(t: Double,r: Float,y: Float)=Vec3((cos(t)*r).toFloat(),y,(sin(t)*r).toFloat())
            for(i in 0 until around)for(j in 0 until across) {
                val a=i*2*PI/around;val b=(i+1)*2*PI/around
                val y0=-half+j*2*half/across;val y1=y0+2*half/across
                val r=radius(i,j);val out=Vec3(cos((a+b)/2).toFloat(),0f,sin((a+b)/2).toFloat())
                facing(q(a,r,y0),q(b,r,y0),q(b,r,y1),out);facing(q(a,r,y0),q(b,r,y1),q(a,r,y1),out)
                val next=radius(i+1,j)
                if(next!=r) {
                    val lo=min(r,next);val hi=max(r,next);val dir=if(r>next)Vec3(-sin(b).toFloat(),0f,cos(b).toFloat()) else Vec3(sin(b).toFloat(),0f,-cos(b).toFloat())
                    facing(q(b,lo,y0),q(b,hi,y0),q(b,hi,y1),dir);facing(q(b,lo,y0),q(b,hi,y1),q(b,lo,y1),dir)
                }
                val upper=radius(i,j+1)
                if(upper!=r) {
                    val lo=min(r,upper);val hi=max(r,upper);val dir=Vec3(0f,if(r>upper)1f else -1f,0f)
                    facing(q(a,lo,y1),q(b,lo,y1),q(b,hi,y1),dir);facing(q(a,lo,y1),q(b,hi,y1),q(a,hi,y1),dir)
                }
                if(j==0 && r>groove) {
                    facing(q(a,groove,y0),q(b,groove,y0),q(b,r,y0),Vec3(0f,-1f,0f));facing(q(a,groove,y0),q(b,r,y0),q(a,r,y0),Vec3(0f,-1f,0f))
                }
            }
        }
        /** Dished steel rim facing +Y: rolled lip, sloped dish, raised hub and six lug nuts. */
        fun rim(nuts: Int=6)=build {
            val profile=listOf(.345f to .40f,.35f to .46f,.33f to .49f,.30f to .47f,.22f to .36f,.16f to .34f,.15f to .40f,.11f to .44f,0f to .45f)
            val around=40
            for(i in 0 until around)for(k in 0 until profile.lastIndex) {
                val a=i*2*PI/around;val b=(i+1)*2*PI/around
                val (r0,y0)=profile[k];val (r1,y1)=profile[k+1]
                val dr=r1-r0;val dy=y1-y0;val l=sqrt(dr*dr+dy*dy)
                // Outward normal of the profile segment (towards +Y for the face, +R for the lip).
                var nr=-dy/l;var ny=dr/l;if(ny<0f || (abs(ny)<1e-3f && nr<0f)) { nr=-nr;ny=-ny }
                fun p(t: Double,r: Float,y: Float)=Vec3((cos(t)*r).toFloat(),y,(sin(t)*r).toFloat())
                fun n(t: Double)=Vec3((cos(t)*nr).toFloat(),ny,(sin(t)*nr).toFloat())
                smooth(p(a,r0,y0),n(a),p(b,r0,y0),n(b),p(b,r1,y1),n(b));smooth(p(a,r0,y0),n(a),p(b,r1,y1),n(b),p(a,r1,y1),n(a))
            }
            for(k in 0 until nuts) {
                val t=k*2*PI/nuts;val cx=(cos(t)*.19).toFloat();val cz=(sin(t)*.19).toFloat()
                block(cx,.375f,cz,.045f,.05f,.045f)
            }
        }
        /** One track shoe along +X: plate, raised grouser bar across it and the two link lugs underneath. */
        fun trackShoe()=build {
            block(0f,0f,0f,1f,.4f,1f)
            block(-.15f,.38f,0f,.28f,.42f,1f)
            for(z in floatArrayOf(-.28f,.28f))block(.05f,-.4f,z,.75f,.45f,.14f)
        }
        /** Drive sprocket around the Y axis: hub disc with ten square teeth. */
        fun sprocket(teeth: Int=10)=build {
            val around=40
            for(i in 0 until around) {
                val a=i*2*PI/around;val b=(i+1)*2*PI/around
                fun p(t: Double,r: Float,y: Float)=Vec3((cos(t)*r).toFloat(),y,(sin(t)*r).toFloat())
                val na=Vec3(cos(a).toFloat(),0f,sin(a).toFloat());val nb=Vec3(cos(b).toFloat(),0f,sin(b).toFloat())
                smooth(p(a,.4f,-.5f),na,p(b,.4f,-.5f),nb,p(b,.4f,.5f),nb);smooth(p(a,.4f,-.5f),na,p(b,.4f,.5f),nb,p(a,.4f,.5f),na)
                facing(Vec3(0f,.5f,0f),p(a,.4f,.5f),p(b,.4f,.5f),Vec3(0f,1f,0f));facing(Vec3(0f,-.5f,0f),p(a,.4f,-.5f),p(b,.4f,-.5f),Vec3(0f,-1f,0f))
            }
            for(k in 0 until teeth) {
                val t=k*2*PI/teeth;val c=cos(t).toFloat();val sn=sin(t).toFloat()
                // A tooth is a block rotated to point outward: build its 8 corners explicitly.
                val corners=ArrayList<Vec3>()
                for(dr in floatArrayOf(.36f,.5f))for(dy in floatArrayOf(-.4f,.4f))for(dt in floatArrayOf(-.06f,.06f))
                    corners.add(Vec3(c*dr-sn*dt,dy,sn*dr+c*dt))
                quadBox(corners)
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
        fun gable()=build {
            val a=Vec3(-.5f,0f,-.5f);val b=Vec3(-.5f,0f,.5f);val c=Vec3(-.5f,1f,0f)
            val d=Vec3(.5f,0f,-.5f);val e=Vec3(.5f,0f,.5f);val f=Vec3(.5f,1f,0f)
            facing(a,b,c,Vec3(-1f,0f,0f));facing(d,f,e,Vec3(1f,0f,0f))
            facing(a,d,e,Vec3(0f,-1f,0f));facing(a,e,b,Vec3(0f,-1f,0f))
            facing(a,c,f,Vec3(0f,1f,-1f));facing(a,f,d,Vec3(0f,1f,-1f))
            facing(b,e,f,Vec3(0f,1f,1f));facing(b,f,c,Vec3(0f,1f,1f))
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
        /** Axis-aligned box with flat faces, centred at (x, y, z). */
        fun block(x: Float,y: Float,z: Float,w: Float,h: Float,d: Float) {
            val c=ArrayList<Vec3>()
            for(dx in floatArrayOf(-w/2,w/2))for(dy in floatArrayOf(-h/2,h/2))for(dz in floatArrayOf(-d/2,d/2))c.add(Vec3(x+dx,y+dy,z+dz))
            quadBox(c)
        }
        /** Hexahedron from 8 corners indexed by (x-ish, y, z-ish) bits: 4*i+2*j+k. */
        fun quadBox(c: List<Vec3>) {
            var centre=Vec3(0f,0f,0f);for(p in c)centre=centre+p;centre=centre*(1f/8f)
            fun quad(a: Int,b: Int,d: Int,e: Int) {
                val mid=(c[a]+c[b]+c[d]+c[e])*.25f;val out=mid-centre
                facing(c[a],c[b],c[d],out);facing(c[a],c[d],c[e],out)
            }
            quad(0,1,3,2);quad(4,5,7,6);quad(0,1,5,4);quad(2,3,7,6);quad(0,2,6,4);quad(1,3,7,5)
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
