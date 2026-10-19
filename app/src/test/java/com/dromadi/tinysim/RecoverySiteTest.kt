package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame

class RecoverySiteTest {
    @Test fun workButtonSettlesStormSelectsMachineAndIgnoresRepeatedTapsWhileMoving() {
        val g=GameState();g.recovery.enter(g)
        g.recovery.work(g)
        assertFalse(g.wind.active);assertEquals(RecoverySite.Step.STORM,g.recovery.step)
        g.wind.toggle(g);repeat(90){tick(g)}
        assertTrue(g.recovery.state.any { it==1 })
        g.recovery.work(g)
        assertFalse(g.wind.active);assertEquals(0f,g.wind.strength,0f)
        assertFalse(g.recovery.state.any { it==1 })
        assertEquals(GameState.Vehicle.DOZER,g.selected);assertTrue(g.driving)
        repeat(30){g.recovery.work(g);tick(g)}
        idle(g);assertEquals(1,g.recovery.state.count { it==3 })
    }
    @Test fun oneWorkButtonCompletesDistrictAndDoesNotRestartStormAfterReward() {
        val g=GameState();storm(g)
        assertEquals(RecoverySite.Step.CLEAN,g.recovery.step)
        val seen=mutableSetOf<RecoverySite.Step>()
        repeat(100) {
            seen+=g.recovery.step
            g.recovery.work(g);idle(g)
        }
        assertTrue(seen.containsAll(listOf(RecoverySite.Step.CLEAN,RecoverySite.Step.HAUL,RecoverySite.Step.POUR,RecoverySite.Step.BUILD,RecoverySite.Step.COMPLETE)))
        assertEquals(1,g.recovery.cycles);assertTrue(g.recovery.complete);assertFalse(g.wind.active)
        val restored=GameState();restored.restore(g.snapshot())
        assertEquals(RecoverySite.Step.COMPLETE,restored.recovery.step)
        restored.recovery.work(restored);assertFalse(restored.wind.active)
        restored.wind.toggle(restored);assertTrue(restored.wind.active)
    }
    private fun tick(g:GameState) {
        g.update(.04f)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        assertEquals(16,g.recovery.state.size)
        assertEquals(16,g.recovery.concrete+g.recovery.pads.sum())
    }
    private fun idle(g:GameState) {
        repeat(12000) { if(!g.driving)return;tick(g) }
        fail("stuck: states=${g.recovery.state.toList()} goals=${g.dozerGoal}/${g.truckGoal}/${g.craneGoal}/${g.fleet.mixer.goal}")
    }
    private fun storm(g:GameState) {
        g.recovery.enter(g);g.wind.toggle(g)
        for(h in 0..3) { g.wind.aim(RecoverySite.houseX(h),RecoverySite.houseZ(h));repeat(180){tick(g)} }
        g.wind.toggle(g);idle(g)
        assertEquals(16,g.recovery.state.count { it==2 })
    }
    private fun act(g:GameState,v:GameState.Vehicle) { g.selected=v;g.action();idle(g) }
    private fun depot(g:GameState) {
        storm(g)
        repeat(16) { act(g,GameState.Vehicle.DOZER);assertEquals(it+1,g.recovery.state.count { s->s==3 }) }
        repeat(2) { act(g,GameState.Vehicle.TRUCK);assertEquals((it+1)*8,g.recovery.state.count { s->s==5 }) }
    }
    @Test fun fullDistrictLoopUsesActualVehicleTravelThenRebuildsAllOriginalParts() {
        val g=GameState();depot(g)
        repeat(4) { act(g,GameState.Vehicle.MIXER);assertEquals((it+1)*4,g.recovery.pads.sum()) }
        repeat(16) { i->
            act(g,GameState.Vehicle.CRANE);assertEquals(i,g.recovery.held)
            act(g,GameState.Vehicle.CRANE);assertEquals(-1,g.recovery.held);assertEquals(i+1,g.recovery.state.count { it==0 })
        }
        assertTrue(g.recovery.complete);assertEquals(1,g.recovery.cycles);assertEquals(0,g.recovery.damaged)
        storm(g);assertEquals(0,g.recovery.concrete);assertEquals(16,g.recovery.pads.sum())
        repeat(16){act(g,GameState.Vehicle.DOZER)};repeat(2){act(g,GameState.Vehicle.TRUCK)}
        repeat(16){act(g,GameState.Vehicle.CRANE);act(g,GameState.Vehicle.CRANE)}
        assertEquals(2,g.recovery.cycles);assertTrue(g.recovery.complete);assertEquals(0,g.recovery.concrete)
    }
    @Test fun expandedMapAndLegacyAlpha20MigrateWithoutLosingChildrenMaterial() {
        val g=GameState();g.load=3;g.source-=3
        val parts=g.snapshot().substringAfter('|').split(';').take(13).toMutableList()
        for(i in listOf(1,3,4))parts[i]=parts[i].split(',').take(86*32).joinToString(",")
        val restored=GameState();restored.restore("15|"+parts.joinToString(";"))
        assertEquals(48,WorldMap.ROWS);assertEquals(16572,Terrain.INITIAL_MASS)
        assertEquals(3,restored.load);assertEquals(g.source,restored.source);assertEquals(Terrain.INITIAL_MASS,restored.totalMaterial)
        assertTrue(restored.recovery.complete)
    }
    @Test fun pauseWhilePartsFlySettlesAndColdRestoreKeepsOwnedFrameIndependent() {
        val g=GameState();g.recovery.enter(g);g.wind.toggle(g);repeat(70){tick(g)}
        assertTrue(g.recovery.state.any { it==1 });g.stopMovement()
        val f=SceneFrame.capture(g,0,1f);val saved=g.snapshot();val c=GameState();c.restore(saved)
        assertEquals(saved,c.snapshot());assertFalse(c.driving);assertFalse(c.wind.active)
        val old=f.recovery.state.toList();g.reset();assertEquals(old,f.recovery.state.toList());assertTrue(c.recovery.damaged!=0)
    }
    @Test fun pauseOnTheBladeKeepsThePartAtItsActualPositionAndAllowsRetry() {
        val g=GameState();storm(g);g.selected=GameState.Vehicle.DOZER;g.action()
        var moved=false
        val original=g.recovery.x.copyOf()
        repeat(3000) { if(!moved) { tick(g);moved=g.recovery.x.indices.any { kotlin.math.abs(g.recovery.x[it]-original[it])>.1f } } }
        assertTrue(moved);g.stopMovement();val c=GameState();c.restore(g.snapshot())
        assertEquals(g.recovery.snapshot(),c.recovery.snapshot());assertFalse(c.driving)
        act(c,GameState.Vehicle.DOZER);assertEquals(1,c.recovery.state.count { it==3 })
    }
    @Test fun partialTruckLoadSurvivesPauseAndCanBeDeliveredWithoutDuplication() {
        val g=GameState();storm(g);repeat(8){act(g,GameState.Vehicle.DOZER)}
        g.selected=GameState.Vehicle.TRUCK;g.action()
        repeat(4000){if(g.recovery.cargo<3)tick(g)};assertEquals(3,g.recovery.cargo)
        g.stopMovement();val c=GameState();c.restore(g.snapshot());assertEquals(3,c.recovery.cargo)
        act(c,GameState.Vehicle.TRUCK);assertEquals(3,c.recovery.state.count { it==5 });assertEquals(5,c.recovery.state.count { it==3 })
        act(c,GameState.Vehicle.TRUCK);assertEquals(8,c.recovery.state.count { it==5 });assertEquals(0,c.recovery.cargo)
    }
    @Test fun pouringAndHeldCranePartSurviveColdRestoreAndFinishNormally() {
        var g=GameState();depot(g);g.selected=GameState.Vehicle.MIXER;g.action()
        repeat(5000){if(g.recovery.pads.sum()<2)tick(g)};assertEquals(2,g.recovery.pads.sum());assertEquals(90f,g.fleet.mixer.heading,0f)
        g.stopMovement();g=GameState().also { it.restore(g.snapshot()) };assertEquals(14,g.recovery.concrete)
        act(g,GameState.Vehicle.MIXER);assertEquals(4,g.recovery.pads[0])
        act(g,GameState.Vehicle.CRANE);assertEquals(0,g.recovery.held);g.stopMovement()
        val c=GameState();c.restore(g.snapshot());assertEquals(0,c.recovery.held);assertFalse(c.craneBusy)
        act(c,GameState.Vehicle.CRANE);assertEquals(0,c.recovery.state[0]);assertEquals(-1,c.recovery.held)
    }
    @Test fun guideLeadsWholeLoopUsingOnlyCardsAndActionWithoutResettingWorld() {
        val g=GameState();storm(g)
        repeat(150) {
            if(g.recovery.cycles==0) {
                when(val h=Guide.next(g)) {
                    is Hint.Card->g.selected=h.vehicle
                    Hint.Action->{g.action();idle(g)}
                    else->fail("unexpected district instruction $h")
                }
            }
        }
        assertEquals("states=${g.recovery.state.toList()},pads=${g.recovery.pads.toList()},vehicle=${g.selected},next=${Guide.next(g)}",1,g.recovery.cycles);assertTrue(g.recovery.complete)
    }
    @Test fun occupiedLoadingBayStopsSafelyAndRetryWorksAfterBlockerMoves() {
        val g=GameState();storm(g);act(g,GameState.Vehicle.DOZER)
        g.fleet.mixer.x=WorldMap.gameX(RecoverySite.LOAD_X);g.fleet.mixer.z=RecoverySite.PILE_Z
        act(g,GameState.Vehicle.TRUCK);assertEquals(1,g.recovery.state.count { it==3 });assertEquals(0,g.recovery.cargo)
        g.driveTo(GameState.Vehicle.MIXER,WorldMap.gameX(4f),27f);idle(g)
        act(g,GameState.Vehicle.TRUCK);assertEquals(1,g.recovery.state.count { it==5 })
    }
    @Test fun malformedDistrictSaveIsRejectedRatherThanManufacturingParts() {
        for(bad in listOf("NaN","Infinity","-999")) {
            val g=GameState();storm(g);g.stopMovement()
            val p=g.snapshot().substringAfter('|').split(';').toMutableList()
            val a=p[13].split('#').toMutableList();val cells=a[2].split(':').toMutableList()
            val cell=cells[0].split(',').toMutableList();cell[1]=bad;cells[0]=cell.joinToString(",");a[2]=cells.joinToString(":");p[13]=a.joinToString("#")
            val copy=GameState();copy.restore("19|"+p.joinToString(";"));assertEquals(GameState().snapshot(),copy.snapshot())
        }
    }

}
