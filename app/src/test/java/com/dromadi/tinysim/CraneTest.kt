package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test

class CraneTest {
    private fun run(g:GameState) { repeat(12000) { if(!g.driving)return;g.update(.016f) };fail("crane stuck") }
    private val spotX get()=GameState.CRANE_SPOT_X
    @Test fun grabsSandWithinReachAndDropsItIntoTheTruck() {
        val g=GameState();val cell=WorldMap.cell(42.5f,6.5f);g.sand[cell]=3;g.source-=3
        assertTrue(g.craneTap(42.5f,6.5f));assertTrue(g.craneBusy);run(g)
        assertEquals(2,g.craneCargo);assertEquals(1,g.sand[cell]);assertEquals(GameState.HOOK_REST,g.hookY,.01f)
        g.truckX=WorldMap.gameX(41f);g.truckZ=10f
        assertTrue(g.craneTap(41f,10f));run(g)
        assertEquals(0,g.craneCargo);assertEquals(2,g.load);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun aFarTouchDrivesWithinReachThenGrabs() {
        val g=GameState()
        assertTrue(g.craneTap(WorldMap.worldX(365f),0f));assertNotNull(g.craneGoal);run(g)
        assertEquals(2,g.craneCargo);assertEquals(34,g.source);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun dropsIntoTheBridgeYardAndNeverIntoTheRiver() {
        val g=GameState();g.craneCargo=2;g.source-=2
        assertTrue(g.craneTap(46.5f,4.5f));run(g);assertEquals(2,g.bridgeFill)
        g.craneCargo=2;g.source-=2;g.craneX=WorldMap.gameX(47.5f);g.craneZ=10f
        assertTrue(g.craneTap(52f,10f));run(g)
        assertEquals(0,(0 until WorldMap.CELL_COUNT).filter { WorldMap.river(it) }.sumOf { g.sand[it] })
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun craneActionDrivesBackToItsSpotAndBuildsEachBridgeStage() {
        val g=GameState()
        g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.truckHeading=0f;g.load=8;g.source-=8
        g.selected=GameState.Vehicle.TRUCK;g.action();repeat(400) { g.update(.016f) };g.action();repeat(200) { g.update(.016f) }
        assertTrue(g.bridgeReady)
        g.craneX=WorldMap.gameX(38f);g.craneZ=9f;g.selected=GameState.Vehicle.CRANE
        repeat(3) {
            g.action();run(g)
            assertTrue("stage starts",g.bridgeClock>0f || g.bridgeStage==it+1)
            repeat(300) { g.update(.016f) }
        }
        assertTrue(g.bridgeOpen);assertEquals(spotX,g.craneWorldX,.01f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun craneStateSurvivesSavesAndOlderSavesGetADefaultCrane() {
        val g=GameState();g.craneCargo=1;g.source-=1;g.craneAngle=30f;g.craneReach=6f;g.selected=GameState.Vehicle.CRANE
        g.craneX=WorldMap.gameX(30f);g.craneZ=2f
        val copy=GameState();copy.restore(g.snapshot())
        assertEquals(g.snapshot(),copy.snapshot());assertEquals(GameState.Vehicle.CRANE,copy.selected);assertEquals(1,copy.craneCargo)
        val v8="8|"+g.snapshot().removePrefix("9|").substringBeforeLast(';').replace(",3,1,1,0,1,",",3,1,1,0,1,")
        val old=GameState();old.restore(v8.replace(Regex("^8\\\\|([^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,)(\\\\d+)"),"8|$1${33+1}"))
        assertEquals(spotX,old.craneWorldX,.01f);assertEquals(0,old.craneCargo)
    }
}
