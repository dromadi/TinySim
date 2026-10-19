package com.dromadi.tinysim

import com.dromadi.tinysim.scene.Geometry
import com.dromadi.tinysim.scene.Vec3
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    private fun triangles(mesh: Geometry): List<List<Pair<Vec3,Vec3>>> {
        val v=mesh.vertexData()
        return (0 until v.size/18).map { t -> (0..2).map { k -> val o=t*18+k*6
            Vec3(v[o],v[o+1],v[o+2]) to Vec3(v[o+3],v[o+4],v[o+5]) } }
    }
    /** Back-face culling needs counter-clockwise winding seen from outside, matching the shading normals. */
    private fun assertOutward(name: String,mesh: Geometry,center: Vec3) {
        assertTrue(mesh.triangleCount>0)
        for(t in triangles(mesh)) {
            val (a,b,c)=t.map { it.first }
            val face=(b-a).cross(c-a)
            val centroid=(a+b+c)*(1f/3f)
            for((_,n) in t) {
                assertEquals("$name unit normal",1f,kotlin.math.sqrt(n.dot(n)),1e-3f)
                assertTrue("$name normal agrees with winding",face.dot(n)>0f)
            }
            assertTrue("$name faces outward at $centroid",face.dot(centroid-center)>=-1e-6f)
        }
    }
    @Test fun closedMeshesFaceOutward() {
        assertOutward("gable",Geometry.gable(),Vec3(0f,.33f,0f))
        assertOutward("cube",Geometry.cube(),Vec3(0f,0f,0f))
        assertOutward("bevel",Geometry.bevelCube(),Vec3(0f,0f,0f))
        assertOutward("sphere",Geometry.sphere(),Vec3(0f,0f,0f))
        assertOutward("cylinder",Geometry.cylinder(),Vec3(0f,0f,0f))
    }
    @Test fun bevelledBoxStaysInsideUnitBounds() {
        val v=Geometry.bevelCube().vertexData()
        for(i in v.indices step 6)for(k in 0..2)assertTrue(kotlin.math.abs(v[i+k])<=.5f+1e-6f)
        // 6 faces, 12 edge strips and 8 corners.
        assertEquals(6*2+12*2+8,Geometry.bevelCube().triangleCount)
    }
    @Test fun sandMoundIsAnUpwardFacingPile() {
        var top=0f
        for(t in triangles(Geometry.mound()))for((p,n) in t) {
            assertTrue(p.y>=-1e-6f && p.y<=1.05f)
            assertTrue("normals point to the sky",n.y>0f)
            top=maxOf(top,p.y)
        }
        assertTrue(top>.9f)
        for(t in triangles(Geometry.mound())) {
            val (a,b,c)=t.map { it.first }
            assertTrue("seen from above",(b-a).cross(c-a).y>0f)
        }
    }
    @Test fun tyreHasAHollowHubAndStaysInsideTheWheelEnvelope() {
        val mesh=Geometry.tyre();var inner=0;var outer=0
        for(t in triangles(mesh))for((p,_) in t) {
            val r=kotlin.math.hypot(p.x,p.y)
            assertTrue("radial envelope",r>=.2999f && r<=.51f)
            assertTrue("axial envelope",kotlin.math.abs(p.z)<=.2001f)
            if(r<.301f)inner++
            if(r>.49f)outer++
        }
        assertTrue("hub opening",inner>0);assertTrue("raised tread",outer>0)
    }
    @Test fun tyreSidewallsBoreAndTreadHaveFiniteNormalsAndMatchingWinding() {
        var inwardBore=false
        for(t in triangles(Geometry.tyre())) {
            val (a,b,c)=t.map { it.first };val face=(b-a).cross(c-a)
            assertTrue("nondegenerate triangle",face.dot(face)>1e-12f)
            for((p,n) in t) {
                assertTrue(listOf(p.x,p.y,p.z,n.x,n.y,n.z).all { it.isFinite() })
                assertEquals(1f,kotlin.math.sqrt(n.dot(n)),1e-3f)
                assertTrue("CCW agrees with shading",face.dot(n)>0f)
                if(kotlin.math.hypot(p.x,p.y)<.301f && n.x*p.x+n.y*p.y<-.29f)inwardBore=true
            }
        }
        assertTrue("inner wall faces the open hub",inwardBore)
    }

}
