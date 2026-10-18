package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame
import kotlin.math.*

class WorkFleetTest {
    private fun idle(g:GameState) {
        repeat(20000) {
            if(!g.driving)return
            g.update(.016f)
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
            assertEquals(WorkFleet.CONCRETE,g.fleet.totalConcrete)
        }
        fail("route or job stuck")
    }
    private fun bridge(g:GameState) {
        g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.load=8;g.source-=8;g.selected=GameState.Vehicle.TRUCK;g.action();repeat(400){g.update(.016f)};g.action();repeat(200){g.update(.016f)}
        repeat(3){assertTrue(g.buildBridge());idle(g);repeat(300){g.update(.016f)}};assertTrue(g.bridgeOpen)
    }
    @Test fun mixerDrivesMixesAndPoursAllEightUnitsIntoAVisiblePad() {
        val g=GameState();g.selected=GameState.Vehicle.MIXER;g.action();g.update(.016f)
        assertNotNull(g.fleet.mixer.goal);assertEquals(0,g.fleet.pad)
        var sawMix=false;var sawPour=false;var sawPartial=false
        repeat(20000) { if(g.driving) {
            g.update(.016f);sawMix=sawMix || g.fleet.mixClock>0;sawPour=sawPour || g.fleet.pouring;sawPartial=sawPartial || g.fleet.pad in 1..7
            assertEquals(8,g.fleet.totalConcrete);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        } }
        assertTrue(sawMix && sawPour && sawPartial);assertEquals(8,g.fleet.pad);assertEquals(0,g.fleet.concrete)
        assertTrue(g.fleet.drumAngle>0f);assertEquals(8,SceneFrame.capture(g,0,1f).work.pad)
    }
    @Test fun wreckerReachesTheTargetThenThreeSwingsCreateExactlyNineRubbleProps() {
        val g=GameState();g.selected=GameState.Vehicle.WRECKER
        repeat(3) { i->
            g.action();assertEquals(i,g.fleet.hits);idle(g);assertEquals(i+1,g.fleet.hits)
        }
        g.action();assertFalse(g.driving);assertEquals(3,g.fleet.hits);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun directTravelCancelsPouringAndSwingWithoutDuplicatingMaterial() {
        val g=GameState();g.fleet.mixer.x=WorldMap.gameX(WorkFleet.MIXER_X);g.fleet.mixer.z=WorkFleet.YARD_Z
        g.selected=GameState.Vehicle.MIXER;g.action();repeat(200){g.update(.016f)};assertTrue(g.fleet.pad>0)
        val count=g.fleet.pad;g.driveTo(GameState.Vehicle.MIXER,WorldMap.gameX(9f),19f);idle(g)
        assertEquals(count,g.fleet.pad);assertEquals(8,g.fleet.totalConcrete)
        g.selected=GameState.Vehicle.WRECKER;g.action();idle(g);val hits=g.fleet.hits
        g.action();repeat(10){g.update(.016f)};g.driveTo(GameState.Vehicle.WRECKER,WorldMap.gameX(40f),19f);idle(g)
        assertEquals(hits,g.fleet.hits)
    }
    @Test fun pauseAndColdRestoreRetainWetConcreteHitsAndAllSixPoses() {
        val g=GameState();g.selected=GameState.Vehicle.MIXER;g.action()
        repeat(20000) { if(g.fleet.pad<3)g.update(.016f) };assertEquals(3,g.fleet.pad);g.stopMovement()
        val saved=g.snapshot();val copy=GameState();copy.restore(saved);assertEquals(saved,copy.snapshot());assertFalse(copy.driving)
        copy.action();idle(copy);assertEquals(8,copy.fleet.pad)
        copy.selected=GameState.Vehicle.WRECKER;copy.action();idle(copy);copy.stopMovement()
        val restored=GameState();restored.restore(copy.snapshot());assertEquals(1,restored.fleet.hits);assertFalse(restored.driving)
        val f=SceneFrame.capture(restored,0,1f);restored.reset();assertEquals(1,f.work.hits);assertEquals(8,f.work.pad)
    }
    @Test fun newVehiclesShareCollisionEnvelopesAndNeverTunnelThroughTheOriginalFleet() {
        val g=GameState();g.fleet.mixer.x=WorldMap.gameX(0f);g.fleet.mixer.z=6f;g.fleet.wrecker.x=WorldMap.gameX(20f);g.fleet.wrecker.z=6f
        g.driveTo(GameState.Vehicle.MIXER,WorldMap.gameX(38f),6f)
        repeat(1400) { g.update(.04f)
            val bodies=GameState.Vehicle.entries.map { g.vehicleBody(it) }
            for(i in bodies.indices)for(j in 0 until i)assertTrue("${bodies[i].unit} vs ${bodies[j].unit}",bodies[i].point.distance(bodies[j].point)>=bodies[i].radius+bodies[j].radius+VehicleNavigation.GAP-.002f)
        }
        assertTrue(WorldMap.worldX(g.fleet.mixer.x)>30f)
    }
    @Test fun keeperWavesBeforeConstructionAndOpensOnlyWhenTheBridgeIsBuilt() {
        val g=GameState();g.guardTap();repeat(200){g.update(.016f)};assertEquals(0f,g.keeper.lift,0f)
        bridge(g);g.guardTap();assertFalse(g.keeper.open);repeat(90){g.update(.016f)};assertTrue(g.keeper.open)
        g.truckX=WorldMap.gameX(52f);g.truckZ=4.5f;repeat(900){g.update(.016f)};assertTrue("gate stays up with a vehicle on the deck",g.keeper.open)
    }
    @Test fun automaticKeeperAllowsBothNewTrucksAcrossAndNoVehicleEntersClosedGate() {
        val g=GameState();bridge(g)
        for(v in listOf(GameState.Vehicle.MIXER,GameState.Vehicle.WRECKER)) {
            g.driveTo(v,WorldMap.gameX(67f),18f)
            repeat(20000) { if(g.driving) { g.update(.016f);val x=g.vehicleBody(v).point.x
                if(x in 49f..55f)assertTrue(g.keeper.open)
            } }
            assertEquals(67f,g.vehicleBody(v).point.x,.1f);assertEquals(18f,g.vehicleBody(v).point.z,.1f)
            g.driveTo(v,WorldMap.gameX(if(v==GameState.Vehicle.MIXER)10f else 40f),19f);idle(g)
        }
    }
    @Test fun enlargedMapMigratesAnActualOldWidthPayloadAndPreservesBlockAndPipeState() {
        val g=GameState();g.load=3;g.source-=3;g.blocks.pick(2);g.pipes.active=true
        val a=g.snapshot().substringAfter('|').split(';').take(10).toMutableList()
        for(i in listOf(1,3,4))a[i]=a[i].split(',').take(WorldMap.COLUMNS*WorldMap.LEGACY_ROWS).joinToString(",")
        val copy=GameState();copy.restore(a.joinToString(";",prefix="12|"))
        assertEquals(3,copy.load);assertEquals(2,copy.blocks.carried);assertTrue(copy.pipes.active)
        assertEquals(32,WorldMap.ROWS);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        assertTrue((WorldMap.COLUMNS*22 until WorldMap.CELL_COUNT).all { copy.earth[it]==4 && copy.sand[it]==0 })
    }
    @Test fun malformedFleetSavesCannotCreateConcreteOrPlaceVehiclesInTheRiver() {
        val g=GameState();val values=g.snapshot().substringAfter('|').split(';')
        for(bad in listOf("NaN,19,0,3000,19,0,8,0,0,0","1200,19,0,3000,19,0,8,8,0,0","3720,19,0,3000,19,0,8,0,0,0")) {
            val a=values.toMutableList();a[10]=bad;g.restore(a.joinToString(";",prefix="13|"));assertEquals(GameState().snapshot(),g.snapshot())
        }
    }
    @Test fun newVehiclesCanExploreTheExtraTenMetresAndPictureHintsFinishTheirJobs() {
        val g=GameState()
        for(v in listOf(GameState.Vehicle.MIXER,GameState.Vehicle.WRECKER)) {
            g.selected=v;g.driveTo(v,WorldMap.gameX(if(v==GameState.Vehicle.MIXER)10f else 40f),21f);idle(g)
            assertEquals(21f,g.vehicleBody(v).point.z,.05f)
            repeat(if(v==GameState.Vehicle.MIXER)1 else 3) { assertEquals(Hint.Action,Guide.next(g));g.action();idle(g) }
        }
        assertEquals(8,g.fleet.pad);assertEquals(3,g.fleet.hits)
    }
    @Test fun repeatedActionDoesNotRestartTheMixOrSwingAndAFullResetRestoresProps() {
        val g=GameState();g.selected=GameState.Vehicle.MIXER;g.action()
        repeat(20000){if(g.driving){g.action();g.tapSelectedVehicle();g.update(.016f)}};assertEquals(8,g.fleet.pad)
        g.selected=GameState.Vehicle.WRECKER;g.action();repeat(20000){if(g.driving){g.action();g.tapSelectedVehicle();g.update(.016f)}};assertEquals(1,g.fleet.hits)
        g.reset();assertEquals(8,g.fleet.concrete);assertEquals(0,g.fleet.pad);assertEquals(0,g.fleet.hits)
    }
    @Test fun keeperAndImpactSoundsHaveGentleAttackAndAnAudibleDecayingSignal() {
        for(keeper in listOf(true,false)) {
            val s=MachineSounds();if(keeper)s.keeperSerial++ else s.impactSerial++
            val pcm=DoubleArray(48000){s.next(1.0/48000)}
            assertTrue(abs(pcm[0])<.003)
            assertTrue(sqrt(pcm.take(12000).sumOf{it*it}/12000)>.01)
            assertTrue(pcm.all{it.isFinite() && abs(it)<.25});assertTrue(pcm.takeLast(4800).all{it==0.0})
        }
    }
    @Test fun occupiedMixerBayStopsSafelyAndCanBeRetriedAfterTheTruckMoves() {
        val g=GameState();g.truckX=WorldMap.gameX(WorkFleet.MIXER_X);g.truckZ=WorkFleet.YARD_Z
        g.selected=GameState.Vehicle.MIXER;g.action();idle(g);assertEquals(0,g.fleet.pad);assertFalse(g.driving)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(8f),13f);idle(g)
        g.action();idle(g);assertEquals(8,g.fleet.pad)
    }

    @Test fun bridgeConstructionMovesTheMixerOutBeforeAssemblingTheDeck() {
        val g=GameState();g.fleet.mixer.x=WorldMap.gameX(43.6f);g.fleet.mixer.z=1.6f
        bridge(g)
        assertTrue(g.fleet.mixer.z>7.5f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }

}
