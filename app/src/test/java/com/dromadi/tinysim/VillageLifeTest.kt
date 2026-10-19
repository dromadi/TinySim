package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame

class VillageLifeTest {
    private fun tick(g:GameState) { g.update(.04f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) }
    /** Recover one genuinely storm-damaged house through the real vehicle jobs. */
    private fun recovered():GameState {
        val g=GameState();g.recovery.enter(g);g.wind.toggle(g);repeat(100){tick(g)}
        g.stopMovement()
        repeat(40) {
            if(g.recovery.cycles==0) {
                g.recovery.work(g)
                var ticks=0
                while(g.driving) { tick(g);assertTrue("recovery route stuck",++ticks<12000) }
            }
        }
        assertEquals(1,g.recovery.cycles);assertEquals(1,g.village.welcomed)
        repeat(60){tick(g)}
        return g
    }
    @Test fun initialTrainingHousesDoNotSpawnResidentsOrAcceptTrees() {
        val g=GameState();g.recovery.enter(g);repeat(100){tick(g)}
        assertEquals(0,g.village.welcomed);assertEquals(-1,g.village.nextGarden(g))
        for(h in -1..4)assertFalse(g.village.plant(g,h))
    }
    @Test fun recoveredHouseWalksOutRequestsTreeAndPlantingRewardsOnce() {
        val g=recovered();assertTrue(g.village.outside(g,0));assertEquals(1f,g.village.arrival[0],0f)
        assertEquals(Hint.Garden(0),Guide.next(g));val reward=g.rewardSerial
        assertTrue(g.village.plant(g,0));assertFalse(g.village.plant(g,0))
        assertEquals(reward+1,g.rewardSerial);assertEquals(-1,g.village.nextGarden(g))
        repeat(60){tick(g)};assertEquals(1f,g.village.growth[0],0f);assertTrue(g.village.greet(g,0))
    }
    @Test fun stormSheltersImmediatelyAndGardensSurviveSaveAndReturn() {
        val g=recovered();assertTrue(g.village.plant(g,0));g.wind.toggle(g)
        assertFalse(g.village.outside(g,0));assertFalse(SceneFrame.capture(g,0,1f).village.outside[0])
        assertFalse(g.village.greet(g,0));assertFalse(g.village.plant(g,1))
        g.stopMovement();val saved=g.snapshot();val c=GameState();c.restore(saved)
        assertEquals(saved,c.snapshot());assertEquals(1,c.village.planted);assertFalse(c.wind.active)
        tick(c);assertTrue(c.village.outside(c,0))
        c.wind.arena.enter();assertFalse(c.village.outside(c,0))
        c.wind.arena.leave();assertEquals(1,c.village.planted)
    }
    @Test fun legacyAlpha22MigratesMaterialRecoveryAndEnablesPostBuildGardens() {
        val g=recovered();val old="16|"+g.snapshot().substringAfter('|').split(';').take(14).joinToString(";")
        val c=GameState();c.restore(old)
        assertEquals(g.totalMaterial,c.totalMaterial);assertEquals(g.recovery.snapshot(),c.recovery.snapshot())
        assertEquals(15,c.village.welcomed);assertEquals(0,c.village.planted);assertTrue(c.snapshot().startsWith("19|"))
        val fresh=GameState();val oldFresh="16|"+fresh.snapshot().substringAfter('|').split(';').take(14).joinToString(";")
        c.restore(oldFresh);assertEquals(0,c.village.welcomed)
    }
    @Test fun capturedResidentsAreOwnedCopiesAndCorruptOwnershipIsRejected() {
        val g=recovered();val frame=SceneFrame.capture(g,0,1f);assertTrue(frame.village.outside[0])
        g.village.plant(g,0);g.reset();assertEquals(1,frame.village.welcomed);assertEquals(0,frame.village.planted)
        assertTrue(frame.village.arrival[0]>0f)
        for(bad in listOf("0,1","16,0","1,NaN","1,-1","1,1,1")) {
            val s="19|"+g.snapshot().substringAfter('|').split(';').toMutableList().also { it[14]=bad }.joinToString(";")
            val c=GameState();c.restore(s);assertEquals(GameState().snapshot(),c.snapshot())
        }
    }
}
