package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame

class WashBayTest {
    private fun tick(g:GameState,n:Int=1) { repeat(n){g.update(.04f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)} }
    private fun dirty():GameState {
        val g=GameState();g.moveArm(365f,440f);tick(g,100);assertTrue(g.scoop());assertFalse(g.wash.clean)
        return g
    }
    private fun arrive(g:GameState) {
        g.wash.enter(g);var n=0
        while(g.driving) { tick(g);assertTrue("wash bay route stuck",++n<12000) }
        tick(g);assertTrue(g.wash.ready(g));assertEquals(0f,g.excavatorHeading,0f)
    }
    @Test fun realExcavationDirtiesWithoutConsumingExtraMaterialAndBayUsesRealTravel() {
        val g=dirty();val bucket=g.bucket;arrive(g);assertEquals(bucket,g.bucket)
        assertTrue(Guide.next(g) is Hint.Wash)
        g.wash.targets(g).forEach { p->assertTrue("patch off screen: $p",p.first in 0f..1200f && p.second in 100f..535f) }
        val frame=SceneFrame.capture(g,0,1f);g.wash.soil();assertTrue(frame.wash.mud[0]<g.wash.mud[0])
        g.wash.leave(g);assertFalse(g.wash.open);assertEquals(bucket,g.bucket)
    }
    @Test fun heldSprayCleansOnlyHitPatchesAndReleaseStopsIt() {
        val g=dirty();arrive(g);val before=g.wash.mud.copyOf()
        g.wash.spray(g,20f,120f);tick(g,10);assertArrayEquals(before,g.wash.mud,0f)
        val p=g.wash.targets(g)[0];g.wash.spray(g,p.first,p.second);tick(g,2)
        assertTrue(g.wash.mud[0]<before[0]);assertEquals(before[7],g.wash.mud[7],0f)
        g.wash.stop();val stopped=g.wash.mud.copyOf();tick(g,20);assertArrayEquals(stopped,g.wash.mud,0f)
        g.wash.spray(g,Float.NaN,200f);assertFalse(g.wash.spraying)
    }
    @Test fun completeWashRewardsOnceAndCleanVehicleStillWorks() {
        val g=dirty();arrive(g);val reward=g.rewardSerial
        for(p in g.wash.targets(g)) { g.wash.spray(g,p.first,p.second);tick(g,30) }
        assertTrue(g.wash.clean);assertEquals(1,g.wash.cycles);assertEquals(reward+1,g.rewardSerial)
        tick(g,100);assertEquals(reward+1,g.rewardSerial)
        g.wash.leave(g);assertTrue(g.bucket>0);assertFalse(g.wash.spraying)
        g.wash.soil();assertFalse(g.wash.clean)
    }
    @Test fun pauseAndRestoreKeepPartialDirtButNeverResumeWater() {
        val g=dirty();arrive(g);val p=g.wash.targets(g)[0];g.wash.spray(g,p.first,p.second);tick(g,2)
        g.stopMovement();assertFalse(g.wash.spraying);val snapshot=g.snapshot()
        val c=GameState();c.restore(snapshot);assertEquals(snapshot,c.snapshot());assertFalse(c.wash.open);assertFalse(c.wash.spraying)
        arrive(c);assertArrayEquals(g.wash.mud,c.wash.mud,0f)
        val old="17|"+snapshot.substringAfter('|').split(';').take(15).joinToString(";")
        val legacy=GameState();legacy.restore(old);assertTrue(legacy.wash.clean);assertEquals(g.bucket,legacy.bucket);assertEquals(g.totalMaterial,legacy.totalMaterial)
    }
    @Test fun malformedDirtCannotCorruptWorldOrRecreateRewards() {
        val g=dirty()
        for(bad in listOf("0,0,0#"+List(48){"NaN"}.joinToString(",")+"#"+List(48){"0.0"}.joinToString(",")+"#1,1,1,1,1,1", "0,0,0#"+List(48){"1.0"}.joinToString(",")+"#"+List(48){"0.0"}.joinToString(",")+"#1,1,1,1,1,1", "-1,0,0#"+List(48){"0.0"}.joinToString(",")+"#"+List(48){"0.0"}.joinToString(",")+"#1,1,1,1,1,1")) {
            val c=GameState();c.restore(g.snapshot().substringBeforeLast(';')+";"+bad)
            assertEquals(GameState().snapshot(),c.snapshot())
        }
        g.wash.spray(g,500f,300f);assertFalse(g.wash.spraying)
    }
}
