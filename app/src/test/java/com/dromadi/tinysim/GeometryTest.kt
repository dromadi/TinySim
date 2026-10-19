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
    /** Unit normals that agree with the winding, so culling and lighting match on every face. */
    private fun assertConsistent(name: String,mesh: Geometry) {
        assertTrue(mesh.triangleCount>0)
        for(t in triangles(mesh)) {
            val (a,b,c)=t.map { it.first };val face=(b-a).cross(c-a)
            for((_,n) in t) {
                assertEquals("$name unit normal",1f,kotlin.math.sqrt(n.dot(n)),1e-3f)
                assertTrue("$name normal agrees with winding",face.dot(n)>0f)
            }
        }
    }
    @Test fun vehiclePartsAreConsistentAndStayInTheirWheelEnvelope() {
        for((name,mesh) in listOf("tyre" to Geometry.tyre(),"rim" to Geometry.rim(),"shoe" to Geometry.trackShoe(),"sprocket" to Geometry.sprocket()))assertConsistent(name,mesh)
        val tyre=Geometry.tyre().vertexData()
        val radii=(tyre.indices step 6).map { kotlin.math.hypot(tyre[it],tyre[it+2]) }
        assertTrue("tyre fits its unit diameter",radii.all { it<=.5f+1e-4f })
        assertTrue("tyre fits its unit width",(tyre.indices step 6).all { kotlin.math.abs(tyre[it+1])<=.5f })
        // The tread has raised lugs at the full radius and grooves between them.
        assertTrue(radii.any { it>.499f });assertTrue(radii.any { it in .465f..0.475f })
        val rim=Geometry.rim().vertexData()
        assertTrue("rim sits inside the tyre bead",(rim.indices step 6).all { kotlin.math.hypot(rim[it],rim[it+2])<=.35f+1e-4f })
        assertTrue("lug nuts stand on the rim face",(rim.indices step 6).any { rim[it+1]>.39f && kotlin.math.hypot(rim[it],rim[it+2]) in .15f..0.23f })
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
}
