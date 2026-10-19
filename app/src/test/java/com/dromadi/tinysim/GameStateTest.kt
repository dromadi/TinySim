package com.dromadi.tinysim
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GameStateTest {
    private fun settle(g:GameState) { repeat(180){g.update(.016f)} }
    private fun arrive(g:GameState) { repeat(3000) { if(!g.driving)return;g.update(.016f) };fail("Route did not finish") }
    private fun fill(g:GameState) { repeat(2) { g.moveArm(365f,440f);settle(g);assertTrue(g.scoop());g.moveArm(g.truckX,300f);settle(g);assertTrue(g.pour()) } }
    @Test fun fullTouchDestinationConstructionLoopConservesSand() {
        val g=GameState();fill(g)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(18f),6f);arrive(g)
        assertEquals(18f,WorldMap.worldX(g.truckX),.001f);assertEquals(6f,g.truckZ,.001f)
        g.selected=GameState.Vehicle.TRUCK;g.action();settle(g)
        assertEquals(0,g.load);assertEquals(4,g.delivered);assertEquals(4,g.westSand())
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(40f),-2f);arrive(g)
        val pile=g.sand.indices.first { g.sand[it]>0 && !WorldMap.east(WorldMap.cellX(it)) };val px=WorldMap.cellX(pile);val pz=WorldMap.cellZ(pile)
        g.dozerX=WorldMap.gameX(px-3f);g.dozerZ=pz;g.dozerHeading=0f
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(px+4f),pz);arrive(g)
        assertTrue(g.flattened>0);assertTrue(g.sand.count { it>0 }>1);assertEquals(GameState.START_SAND,g.totalSand)
    }
    @Test fun invalidTransfersAndFullTruckNeverLoseSand() {
        val g=GameState();g.moveArm(650f,230f);settle(g)
        assertFalse(g.scoop());assertFalse(g.pour());fill(g);fill(g);assertEquals(8,g.load)
        g.moveArm(365f,440f);settle(g);assertTrue(g.scoop());g.moveArm(620f,300f);settle(g)
        assertFalse(g.pour());assertEquals(2,g.bucket);assertEquals(GameState.START_SAND,g.totalSand)
        g.load=4;g.source+=4;g.truckZ=5f;assertFalse(g.pour());assertEquals(GameState.START_SAND,g.totalSand)
    }
    @Test fun mapRoutesClampAndStopAtTheDestinationWithoutOvershoot() {
        // The parked crane would otherwise occupy the far corner the truck is clamped to.
        val g=GameState();g.craneX=WorldMap.gameX(30f);g.craneZ=-2f
        g.driveTo(GameState.Vehicle.TRUCK,99999f,99999f);arrive(g)
        assertEquals(WorldMap.DRIVE_MAX_X,g.truckX,.001f);assertEquals(WorldMap.DRIVE_MAX_Z,g.truckZ,.001f)
        g.driveTo(GameState.Vehicle.DOZER,-99999f,-99999f);arrive(g)
        assertEquals(WorldMap.DRIVE_MIN_X,g.dozerX,.001f);assertEquals(WorldMap.DRIVE_MIN_Z,g.dozerZ,.001f)
        val position=g.truckX to g.truckZ;settle(g);assertEquals(position,g.truckX to g.truckZ)
        g.driveTo(GameState.Vehicle.TRUCK,Float.NaN,0f);g.update(Float.NaN)
        assertFalse(g.driving);assertTrue(g.truckX.isFinite())
    }
    @Test fun stopCancelsTravelAndTapToDriveLowersTheBedFirst() {
        val g=GameState();g.tipping=true;g.bedAngle=-42f
        g.driveTo(GameState.Vehicle.TRUCK,1800f,6f)
        g.update(.04f);assertEquals(620f,g.truckX,0f)
        arrive(g);assertEquals(1800f,g.truckX,.001f);assertFalse(g.tipping)
        g.driveTo(GameState.Vehicle.TRUCK,2000f,8f);g.driveTo(GameState.Vehicle.DOZER,2000f,8f)
        g.stopMovement();val tx=g.truckX;val tz=g.truckZ;val dx=g.dozerX;val dz=g.dozerZ;settle(g)
        assertEquals(tx,g.truckX,0f);assertEquals(tz,g.truckZ,0f);assertEquals(dx,g.dozerX,0f);assertEquals(dz,g.dozerZ,0f);assertFalse(g.driving)
    }
    @Test fun dumpingFollowsRotatedTruckRearInBothAxes() {
        for(angle in listOf(0f,90f,180f,270f)) {
            val g=GameState();fill(g);g.truckX=1800f;g.truckZ=6f;g.truckHeading=angle
            g.selected=GameState.Vehicle.TRUCK;g.action();settle(g)
            val a=angle*PI.toFloat()/180
            val expected=WorldMap.cell(20f-cos(a)*1.8f,6f+sin(a)*1.8f)
            assertEquals(4,g.sand[expected]);assertEquals(GameState.START_SAND,g.totalSand)
        }
    }
    @Test fun snapshotRestoresExpandedWorldAndRejectsCorruption() {
        val g=GameState();fill(g);g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(38f),9f);arrive(g)
        g.missionMode=true
        val restored=GameState();restored.restore(g.snapshot())
        assertEquals(g.snapshot(),restored.snapshot());assertEquals(GameState.START_SAND,restored.totalSand);assertFalse(restored.driving)
        restored.restore("broken");assertEquals(GameState.START_SAND,restored.totalSand);assertEquals(0,restored.load)
        val invalid=g.snapshot().replaceFirst("2880.0","NaN")
        restored.restore(invalid);assertEquals(620f,restored.truckX,0f)
    }
    @Test fun legacySandMigratesToCorrectWorldLocationsWithoutLoss() {
        val cells=IntArray(24);cells[3]=4;cells[4]=2
        val old="620.0,1000.0,365.0,340.0,0,0,30,6,0,0,0,1,1,0,1,0.0;"+cells.joinToString(",")
        val g=GameState();g.restore(old)
        assertEquals(GameState.START_SAND,g.totalSand);assertEquals(6,g.westSand())
        assertEquals(4,g.sand[WorldMap.cell(WorldMap.worldX(585f),1.35f)])
        assertEquals(2,g.sand[WorldMap.cell(WorldMap.worldX(615f),1.35f)])
        assertTrue(g.snapshot().startsWith("17|"))
    }
    @Test fun loadActionCanBringADistantTruckBackToTheExcavator() {
        val g=GameState();g.moveArm(365f,440f);settle(g);g.scoop()
        g.truckX=WorldMap.gameX(38f);g.truckZ=9f;g.action()
        assertNotNull(g.truckGoal);arrive(g);settle(g);assertTrue(g.pour());assertEquals(2,g.load);assertEquals(GameState.START_SAND,g.totalSand)
    }
    @Test fun missionsRewardAndAdvanceWithoutTimerPressure() {
        val g=GameState();g.missionMode=true;fill(g)
        assertEquals(1,g.rewardSerial);settle(g);g.nextMission();assertEquals(1,g.mission)
        g.selected=GameState.Vehicle.TRUCK;g.action();settle(g)
        assertEquals(4,g.delivered);assertEquals(2,g.rewardSerial)
        settle(g);g.nextMission();assertEquals(2,g.mission)
    }
}
