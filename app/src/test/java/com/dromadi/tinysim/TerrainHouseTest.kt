package com.dromadi.tinysim

import com.dromadi.tinysim.scene.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TerrainHouseTest {
    private fun settle(g:GameState) { repeat(240){g.update(.016f)} }
    private fun arrive(g:GameState) { repeat(7000) { if(!g.driving)return;g.update(.016f) };fail("route stuck") }
    private fun dig(g:GameState,x:Float,z:Float) {
        assertTrue(g.digAt(x,z));arrive(g);settle(g);assertTrue(g.scoop())
    }
    private fun load(g:GameState) { g.action();arrive(g);settle(g);assertTrue(g.pour()) }
    @Test fun realDiggingChangesHeightAndNormalsAndConservesAllMaterial() {
        val g=GameState();g.excavatorX=WorldMap.gameX(27.5f);g.excavatorZ=8.5f
        val x=30.5f;val z=8.5f;val before=g.surface(x,z);dig(g,x,z)
        assertEquals(2,g.bucket);assertEquals(before-.44f,g.surface(x,z),.001f)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        val mesh=Geometry.terrain(g.earth,g.sand).vertexData();var low=1f
        for(i in mesh.indices step 6) { low=minOf(low,mesh[i+1]);assertTrue(mesh[i+4]>0f);assertTrue(mesh[i+1].isFinite()) }
        assertTrue(low<0f)
    }
    @Test fun digLoadDumpAndDozerCanRefillTheActualHole() {
        val g=GameState();g.excavatorX=WorldMap.gameX(27.5f);g.excavatorZ=8.5f
        dig(g,30.5f,8.5f);load(g)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(32.3f),8.5f);arrive(g);g.truckHeading=0f
        g.selected=GameState.Vehicle.TRUCK;g.action();settle(g)
        val i=WorldMap.cell(30.5f,8.5f);assertEquals(2,g.sand[i]);assertEquals(2,g.earth[i])
        g.dozerX=WorldMap.gameX(27f);g.dozerZ=8.5f;g.dozerHeading=0f
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(34f),8.5f);arrive(g)
        assertEquals(4,g.earth[i]);assertEquals(0,g.sand[i]);assertEquals(.07f,g.surface(30.5f,8.5f),.001f)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun completeHouseUsesFourDigSitesAndEightRealDeliveredUnits() {
        val g=GameState();g.excavatorX=WorldMap.gameX(30.5f);g.excavatorZ=6.5f
        assertFalse(g.buildHouse())
        for(x in listOf(33.5f,34.5f,35.5f,36.5f)) {
            g.selected=GameState.Vehicle.EXCAVATOR;dig(g,x,6.5f);load(g)
        }
        assertEquals(4,g.foundationProgress);assertEquals(8,g.load);assertFalse(g.buildHouse())
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(37.3f),7.5f);arrive(g);g.truckHeading=0f
        g.selected=GameState.Vehicle.TRUCK;g.action();settle(g)
        assertEquals(8,g.houseFill);assertEquals(0,g.load)
        repeat(3) { stage->assertTrue(g.buildHouse());assertFalse(g.buildHouse());repeat(400){g.update(.016f)};assertEquals(stage+1,g.houseStage);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) }
        assertFalse(g.buildHouse());assertFalse(g.truckX in WorldMap.gameX(31f)..WorldMap.gameX(41f) && g.truckZ in 3f..11f);assertTrue(g.celebrating>0f || g.rewardSerial>0)
        val copy=GameState();copy.restore(g.snapshot());assertEquals(g.snapshot(),copy.snapshot());assertEquals(3,copy.houseStage)
        val frame=SceneFrame.capture(g,0,1f);g.earth[0]=0;assertEquals(4,frame.earth[0])
    }
    @Test fun terrainPickingMatchesTheVisibleHeightfield() {
        val g=GameState();g.earth[WorldMap.cell(35.5f,7.5f)]=3;g.sand[WorldMap.cell(37.5f,7.5f)]=8
        val cam=WorldCamera.spec(0,0f,20f,1f,36f,7f)
        for(x in listOf(35.5f,36f,37.5f)) {
            val y=g.surface(x,7.5f);val p=cam.project(Vec3(x,y,7.5f));val hit=cam.ground(p.first,p.second,g::surface)!!
            assertEquals(x,hit.x,.002f);assertEquals(y,hit.y,.002f);assertEquals(7.5f,hit.z,.002f)
        }
    }
    @Test fun alpha6MigrationAndCorruptEarthOrHouseCannotCreateMaterial() {
        val g=GameState();val pieces=g.snapshot().removePrefix("7|").split(';')
        val old="6|"+pieces.take(4).joinToString(";");val copy=GameState();copy.restore(old)
        assertEquals(36,copy.totalSand);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        val damaged=g.snapshot().substringBeforeLast(';')+";8,3";copy.restore(damaged)
        assertEquals(0,copy.houseStage);assertEquals(0,copy.houseFill);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        val terrain=g.snapshot().replaceFirst(";4,4,4,4,",";99,4,4,4,");copy.restore(terrain);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
    }
    @Test fun largeStockpilesKeepMassAndRestoreEvenAboveTheVisualHeightCap() {
        val g=GameState();repeat(14){g.earth[it]=0};g.sand[WorldMap.cell(20.5f,6.5f)]=56
        val copy=GameState();copy.restore(g.snapshot());assertEquals(56,copy.sand[WorldMap.cell(20.5f,6.5f)])
        assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial);assertTrue(copy.surface(20.5f,6.5f)<4f)
    }
    @Test fun diggingStopsAtBedrockAndCompletedRoadIsProtected() {
        val g=GameState();val i=WorldMap.cell(26.5f,6.5f);g.excavatorX=WorldMap.gameX(23.5f);g.excavatorZ=6.5f
        repeat(2) { dig(g,26.5f,6.5f);load(g) }
        assertEquals(0,g.earth[i]);assertFalse(g.digAt(26.5f,6.5f))
        assertFalse(g.digAt(17.5f,8.5f));assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
}
