package com.dromadi.tinysim
import org.junit.Assert.*
import org.junit.Test
class GameStateTest {
    private fun settle(g:GameState) { repeat(160){g.update(.016f)} }
    private fun fill(g:GameState) { repeat(2) { g.moveArm(365f,440f);settle(g);assertTrue(g.scoop());g.moveArm(g.truckX,300f);settle(g);assertTrue(g.pour()) } }
    @Test fun fullConstructionLoopConservesSand() {
        val g=GameState();fill(g);assertEquals(4,g.load);assertEquals(36,g.totalSand)
        g.selected=GameState.Vehicle.TRUCK;g.direction=1;repeat(90){g.update(.016f)};g.direction=0
        g.action();repeat(200){g.update(.016f)}
        assertEquals(0,g.load);assertEquals(4,g.delivered);assertEquals(4,g.sand.sum())
        g.selected=GameState.Vehicle.DOZER;g.direction=-1;repeat(250){g.update(.016f)}
        g.direction=1;repeat(250){g.update(.016f)}
        assertTrue(g.flattened>0);assertEquals(36,g.totalSand)
    }
    @Test fun invalidTransfersDoNotDeleteSand() {
        val g=GameState();g.moveArm(650f,230f);settle(g)
        assertFalse(g.scoop());assertFalse(g.pour());assertEquals(36,g.totalSand)
        fill(g);fill(g);assertEquals(8,g.load)
        g.moveArm(365f,440f);settle(g);g.scoop();g.moveArm(g.truckX,300f);settle(g)
        assertFalse(g.pour());assertEquals(2,g.bucket);assertEquals(36,g.totalSand)
    }
    @Test fun snapshotRestoresWorldAndRejectsCorruption() {
        val g=GameState();fill(g);g.missionMode=true
        val restored=GameState();restored.restore(g.snapshot())
        assertEquals(g.snapshot(),restored.snapshot());assertEquals(36,restored.totalSand)
        restored.restore("broken");assertEquals(36,restored.totalSand);assertEquals(0,restored.load)
    }
    @Test fun movementAndDtAreBounded() {
        val g=GameState();g.selected=GameState.Vehicle.TRUCK;g.direction=1
        repeat(10000){g.update(99f)};assertEquals(1110f,g.truckX,.01f)
        g.direction=-1;repeat(10000){g.update(.04f)};assertEquals(510f,g.truckX,.01f)
        g.moveArm(-999f,999f);settle(g);assertTrue(g.armX>=285f);assertTrue(g.armY<=466f)
    }
    @Test fun missionsRewardAndAdvanceWithoutTimerPressure() {
        val g=GameState();g.missionMode=true;fill(g)
        assertEquals(1,g.rewardSerial);assertTrue(g.celebrating>0)
        settle(g);g.nextMission();assertEquals(1,g.mission)
        g.selected=GameState.Vehicle.TRUCK;g.tipping=true;settle(g)
        assertEquals(4,g.delivered);assertEquals(2,g.rewardSerial)
        settle(g);g.nextMission();assertEquals(2,g.mission)
    }
}
