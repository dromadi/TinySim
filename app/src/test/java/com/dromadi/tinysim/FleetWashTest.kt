package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame

class FleetWashTest {
    private fun tick(g:GameState,n:Int=1) { repeat(n){g.update(.04f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)} }
    private fun arrive(g:GameState,v:GameState.Vehicle) {
        g.wash.enter(g,v);var n=0
        while(g.driving) { tick(g);assertTrue("fleet wash stuck: $v",++n<16000) }
        tick(g);assertTrue("not at bay: $v",g.wash.ready(g))
        g.wash.targets(g).forEach { p->assertTrue("unreachable patch on $v: $p",p.first in 0f..1200f && p.second in 100f..535f) }
    }
    private fun sprayAll(g:GameState,tool:WashBay.Tool) {
        g.wash.choose(tool)
        for(p in g.wash.targets(g)) { g.wash.spray(g,p.first,p.second);tick(g,30) }
        g.wash.stop()
    }
    @Test fun allSixVehiclesTravelSoapAndRinseWithoutLosingCargoOrBlockingEachOther() {
        val g=GameState();g.bucket=2;g.load=3;g.craneCargo=1;g.source-=6
        for(v in GameState.Vehicle.entries)g.wash.soil(v,1f)
        for(v in GameState.Vehicle.entries) {
            arrive(g,v);assertEquals(v,g.selected);val reward=g.rewardSerial
            sprayAll(g,WashBay.Tool.SOAP);assertTrue(g.wash.foam.any { it>.5f });assertFalse(g.wash.clean)
            assertEquals(Hint.Rinse,Guide.next(g));assertEquals(reward,g.rewardSerial)
            sprayAll(g,WashBay.Tool.WATER);assertTrue("still dirty: $v",g.wash.clean);assertEquals(reward+1,g.rewardSerial)
            assertEquals(2,g.bucket);assertEquals(3,g.load);assertEquals(1,g.craneCargo)
        }
        assertEquals(6,g.wash.cycles)
        assertTrue(g.wash.dirt.all { row->row.all { it<=.005f } })
    }
    @Test fun soapOnCleanVehiclesDoesNotFarmRewardsAndWaterWorksWithoutSoap() {
        val g=GameState();arrive(g,GameState.Vehicle.TRUCK);val reward=g.rewardSerial
        sprayAll(g,WashBay.Tool.SOAP);sprayAll(g,WashBay.Tool.WATER)
        assertEquals(reward,g.rewardSerial);assertEquals(0,g.wash.cycles)
        g.wash.soil(GameState.Vehicle.TRUCK,1f);sprayAll(g,WashBay.Tool.WATER)
        assertTrue(g.wash.clean);assertEquals(1,g.wash.cycles);assertEquals(reward+1,g.rewardSerial)
        assertTrue(g.wash.dirt[0].all { it==0f })
    }
    @Test fun perVehicleFoamAndToolPersistButSprayingAndModeDoNotResume() {
        val g=GameState();g.wash.soil(GameState.Vehicle.MIXER,1f);arrive(g,GameState.Vehicle.MIXER)
        g.wash.choose(WashBay.Tool.SOAP);val p=g.wash.targets(g)[0];g.wash.spray(g,p.first,p.second);tick(g,5)
        val frame=SceneFrame.capture(g,0,1f);val before=g.wash.snapshot();val snapshot=g.snapshot()
        val c=GameState();c.restore(snapshot);assertEquals(before,c.wash.snapshot());assertFalse(c.wash.open);assertFalse(c.wash.spraying)
        assertEquals(WashBay.Tool.SOAP,c.wash.tool);assertEquals(GameState.Vehicle.MIXER,c.wash.vehicle)
        assertTrue(c.wash.bubbles[4][0]>0f);assertTrue(c.wash.bubbles[0].all { it==0f })
        g.wash.choose(WashBay.Tool.WATER);assertFalse(g.wash.spraying);g.wash.reset()
        assertTrue(frame.wash.foam[4][0]>0f);assertTrue(frame.wash.dirt[4][0]>0f)
    }
    @Test fun pauseCancelsQueuedArrivalUntilExplicitRetry() {
        val g=GameState();arrive(g,GameState.Vehicle.EXCAVATOR)
        g.wash.enter(g,GameState.Vehicle.TRUCK);assertTrue(g.driving)
        g.stopMovement();val before=GameState.Vehicle.entries.map { g.vehicleBody(it).point }
        tick(g,100);assertFalse(g.driving)
        assertEquals(before,GameState.Vehicle.entries.map { g.vehicleBody(it).point })
        arrive(g,GameState.Vehicle.TRUCK);assertTrue(g.wash.ready(g))
    }
    @Test fun faintTravelDirtSurvivesSaveBeforeVisibleThreshold() {
        val g=GameState();g.wash.soil(GameState.Vehicle.DOZER,.0001f)
        assertTrue(g.wash.dirt[2].all { it>0f && it<.005f })
        val snapshot=g.snapshot();val c=GameState();c.restore(snapshot)
        assertEquals(snapshot,c.snapshot());assertArrayEquals(g.wash.dirt[2],c.wash.dirt[2],0f)
        assertEquals(Terrain.INITIAL_MASS,c.totalMaterial)
    }
    @Test fun alpha24SaveMigratesExcavatorDirtAndCyclesWithoutCreatingOtherDirt() {
        val g=GameState();g.wash.soil();val parts=g.snapshot().substringAfter('|').split(';').toMutableList()
        parts[15]="3,0#"+g.wash.mud.joinToString(",")
        val c=GameState();c.restore("18|"+parts.joinToString(";"))
        assertArrayEquals(g.wash.mud,c.wash.mud,0f);assertEquals(3,c.wash.cycles)
        assertTrue(c.wash.dirt.drop(1).all { row->row.all { it==0f } });assertTrue(c.wash.bubbles.all { row->row.all { it==0f } })
        assertEquals(g.totalMaterial,c.totalMaterial);assertTrue(c.snapshot().startsWith("19|"))
    }
    @Test fun rapidVehicleChoicesCancelWaterAndKeepActualPositionsUntilTravelUpdates() {
        val g=GameState();arrive(g,GameState.Vehicle.EXCAVATOR);g.wash.soil()
        val p=g.wash.targets(g)[0];g.wash.spray(g,p.first,p.second)
        val before=g.vehicleBody(GameState.Vehicle.EXCAVATOR).point
        g.wash.enter(g,GameState.Vehicle.TRUCK);assertFalse(g.wash.spraying)
        assertEquals(before,g.vehicleBody(GameState.Vehicle.EXCAVATOR).point)
        g.wash.enter(g,GameState.Vehicle.MIXER);g.wash.enter(g,GameState.Vehicle.WRECKER)
        var n=0;while(g.driving) { tick(g);assertTrue(++n<16000) }
        assertTrue(g.wash.ready(g));assertEquals(GameState.Vehicle.WRECKER,g.wash.vehicle)
    }
}
