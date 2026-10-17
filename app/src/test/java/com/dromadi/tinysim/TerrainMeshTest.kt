package com.dromadi.tinysim

import com.dromadi.tinysim.scene.TerrainMesh
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class TerrainMeshTest {
    private fun fresh(earth:IntArray,sand:IntArray)=TerrainMesh().also { it.update(earth,sand) }.vertices
    @Test fun partialUpdatesMatchAFullRebuildEverywhere() {
        val earth=IntArray(WorldMap.CELL_COUNT){Terrain.INITIAL_EARTH};val sand=IntArray(WorldMap.CELL_COUNT)
        val mesh=TerrainMesh();mesh.update(earth,sand)
        val random=Random(11)
        // Corners and edges exercise the clamped bilinear lookups as well as interior cells.
        val cells=listOf(0,WorldMap.COLUMNS-1,WorldMap.CELL_COUNT-WorldMap.COLUMNS,WorldMap.CELL_COUNT-1)+List(40) { random.nextInt(WorldMap.CELL_COUNT) }
        for(cell in cells) {
            if(random.nextBoolean())earth[cell]=(earth[cell]+1+random.nextInt(Terrain.INITIAL_EARTH))%(Terrain.INITIAL_EARTH+1) else sand[cell]+=1+random.nextInt(6)
            assertNotNull(mesh.update(earth,sand))
            assertArrayEquals(fresh(earth,sand),mesh.vertices,0f)
        }
    }
    @Test fun unchangedWorldDoesNoWorkAndOneCellTouchesFewRows() {
        val earth=IntArray(WorldMap.CELL_COUNT){Terrain.INITIAL_EARTH};val sand=IntArray(WorldMap.CELL_COUNT)
        val mesh=TerrainMesh();assertEquals(0 until TerrainMesh.QUADS_Z,mesh.update(earth,sand))
        assertNull(mesh.update(earth.copyOf(),sand.copyOf()))
        sand[WorldMap.cell(20.5f,4.5f)]++
        val rows=mesh.update(earth,sand)!!
        assertTrue("rows=$rows",rows.last-rows.first+1<=12)
    }
    @Test fun normalsPointUpAndWindingFacesTheSky() {
        val earth=IntArray(WorldMap.CELL_COUNT){Terrain.INITIAL_EARTH};val sand=IntArray(WorldMap.CELL_COUNT)
        earth[WorldMap.cell(30.5f,8.5f)]=0;sand[WorldMap.cell(5.5f,2.5f)]=24
        val v=fresh(earth,sand)
        for(t in 0 until v.size/18) {
            val o=t*18
            val ux=v[o+6]-v[o];val uz=v[o+8]-v[o+2];val vx=v[o+12]-v[o];val vz=v[o+14]-v[o+2]
            assertTrue((uz*vx-ux*vz)>0f)
            for(k in 0..2)assertTrue(v[o+k*6+4]>0f)
        }
    }
}
