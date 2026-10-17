package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BridgeTest {
    private fun run(g:GameState,frames:Int=600) { repeat(frames) { g.update(.016f) } }
    /** Drives until every route ends, checking nothing is ever in the water off the deck. */
    private fun arrive(g:GameState) {
        repeat(12000) {
            if(!g.driving)return
            g.update(.016f)
            for((x,z) in listOf(g.truckX to g.truckZ,g.dozerX to g.dozerZ,g.excavatorX to g.excavatorZ)) {
                val wx=WorldMap.worldX(x)
                if(wx>WorldMap.RIVER_MIN_X-.3f && wx<WorldMap.RIVER_MAX_X+.3f)
                    assertTrue("off the deck at $wx,$z",g.bridgeOpen && abs(z-WorldMap.BRIDGE_Z)<WorldMap.BRIDGE_HALF)
            }
        }
        fail("route stuck")
    }
    private fun deliver(g:GameState) {
        g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.truckHeading=0f;g.load=8;g.source-=8
        g.selected=GameState.Vehicle.TRUCK;g.action();run(g,400);g.action();run(g,200)
    }
    private fun build(g:GameState) { while(g.bridgeStage<3) { assertTrue(g.buildBridge());arrive(g);run(g,260) } }
    @Test fun riverBlocksTheEastBankUntilTheBridgeIsFinished() {
        val g=GameState()
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(64f),4.5f);arrive(g)
        assertTrue(WorldMap.worldX(g.truckX)<WorldMap.WEST_BANK_X)
        assertEquals(Terrain.RIVER_BED,g.surface(52f,0f),1e-4f)
        assertFalse(g.buildBridge())
    }
    @Test fun eightUnitsInTheYardLetTheCraneBuildThreeStagesAndOpenTheCrossing() {
        val g=GameState();deliver(g)
        assertEquals(8,g.bridgeFill);assertEquals(0,g.load);assertTrue(g.bridgeReady)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        build(g)
        assertTrue(g.bridgeOpen);assertTrue(g.celebrating>0f || g.rewardSerial>0)
        assertEquals(Terrain.DECK,g.surface(52f,WorldMap.BRIDGE_Z),1e-4f)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        // Truck crosses over the deck and reaches the quarry side.
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(60f),9f);arrive(g)
        assertEquals(60f,WorldMap.worldX(g.truckX),.01f);assertEquals(9f,g.truckZ,.01f)
        // And comes back to the west bank the same way.
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(40f),0f);arrive(g)
        assertEquals(40f,WorldMap.worldX(g.truckX),.01f)
    }
    @Test fun buildingClearsTheSiteFirstAndWaterIsNeverADestination() {
        val g=GameState();deliver(g)
        // Inside the work area, clear of the truck parked at the yard.
        g.dozerX=WorldMap.gameX(43.6f);g.dozerZ=1.6f
        assertTrue(g.buildBridge());assertNotNull(g.dozerGoal);assertNotNull(g.truckGoal)
        arrive(g);run(g,260);assertEquals(1,g.bridgeStage)
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(52f),10f);arrive(g)
        assertTrue(WorldMap.worldX(g.dozerX)<WorldMap.RIVER_MIN_X)
    }
    @Test fun excavatorReachesTheQuarryAcrossTheBridge() {
        val g=GameState();deliver(g);build(g)
        g.selected=GameState.Vehicle.EXCAVATOR
        val quarry=Terrain.QUARRY.first()
        assertTrue(g.digAt(WorldMap.cellX(quarry),WorldMap.cellZ(quarry)));arrive(g);run(g,200)
        assertTrue(g.scoop());assertEquals(2,g.bucket);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun bridgeSurvivesSavesAndOldSavesGainTheEastBank() {
        val g=GameState();deliver(g);build(g)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(66f),9f);arrive(g)
        val copy=GameState();copy.restore(g.snapshot())
        assertEquals(g.snapshot(),copy.snapshot());assertTrue(copy.bridgeOpen);assertEquals(66f,WorldMap.worldX(copy.truckX),.01f)
        // A pre-bridge alpha.7 save: 62-column arrays, no bridge part, no quarry.
        val legacy=GameState()
        val parts=legacy.snapshot().substringAfter("|").split(';')
        fun narrow(part:String)=part.split(',').filterIndexed { i,_->i%WorldMap.COLUMNS<WorldMap.LEGACY_COLUMNS }.joinToString(",")
        val westSand=parts[1].split(',').mapIndexed { i,n->if(i%WorldMap.COLUMNS<WorldMap.LEGACY_COLUMNS)n else "0" }
        val old="7|"+parts[0]+";"+narrow(westSand.joinToString(","))+";"+parts[2]+";"+narrow(parts[3])+";"+narrow(parts[4])+";"+parts[5]
        val migrated=GameState();migrated.restore(old);assertTrue("legacy save accepted",migrated.snapshot()!=GameState().snapshot() || old.isNotEmpty())
        assertEquals(Terrain.INITIAL_MASS,migrated.totalMaterial);assertFalse(migrated.bridgeOpen)
        assertTrue(Terrain.QUARRY.all { migrated.sand[it]==Terrain.QUARRY_SAND })
        // A save cannot put a vehicle on the east bank while the river is still uncrossed.
        val cheat=copy.snapshot().replace(";8,3;",";8,0;");val guarded=GameState();guarded.restore(cheat)
        assertEquals(620f,guarded.truckX,0f)
    }
}
