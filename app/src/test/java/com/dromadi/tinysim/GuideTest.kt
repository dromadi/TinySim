package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test

class GuideTest {
    private fun settle(g:GameState) { repeat(400) { g.update(.016f) } }
    private fun arrive(g:GameState) { repeat(9000) { if(!g.driving)return;g.update(.016f) };fail("route stuck") }
    /** Follow only the hand: select what it points at, press what it points at, touch where it points. */
    private fun follow(g:GameState,hint:Hint) {
        when(hint) {
            is Hint.Card -> g.selected=hint.vehicle
            Hint.Action -> g.action()
            Hint.Blocks -> { g.stopMovement();g.blockAction() }
            is Hint.Pipe -> g.pipes.tap(g,hint.id)
            is Hint.Garden -> g.village.plant(g,hint.house)
            is Hint.Ground -> if(!(g.selected==GameState.Vehicle.EXCAVATOR && g.bucket==0 && g.digAt(hint.x,hint.z)))
                g.driveTo(g.selected,WorldMap.gameX(hint.x),hint.z)
        }
        arrive(g);settle(g)
        if(g.selected==GameState.Vehicle.EXCAVATOR) { g.scoop();g.pour() }
        if(g.houseReady && g.houseFill>=8 && g.houseStage<3 && g.buildClock==0f)g.buildHouse()
    }
    @Test fun firstHintsWalkThroughTheSandLoop() {
        val g=GameState()
        assertEquals(Hint.Action,Guide.next(g))
        g.selected=GameState.Vehicle.TRUCK;assertEquals(Hint.Card(GameState.Vehicle.EXCAVATOR),Guide.next(g))
        g.selected=GameState.Vehicle.EXCAVATOR;g.bucket=2;g.source=34;assertEquals(Hint.Action,Guide.next(g))
        g.bucket=0;g.load=4;assertEquals(Hint.Card(GameState.Vehicle.TRUCK),Guide.next(g))
        g.load=0;g.sand[WorldMap.cell(5.5f,0.5f)]=4;g.source=28;assertEquals(Hint.Card(GameState.Vehicle.DOZER),Guide.next(g))
    }
    @Test fun aChildFollowingOnlyTheHandFinishesTheHouse() {
        val g=GameState()
        var steps=0
        while(g.houseStage<3) {
            follow(g,Guide.next(g))
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
            assertTrue("no progress: stage=${g.houseStage} fill=${g.houseFill} found=${g.foundationProgress} load=${g.load} source=${g.source}",++steps<300)
        }
        repeat(400) { g.update(.016f) }
        assertEquals(3,g.houseStage)
        // The hand then leads to the bridge: deliver to the yard, the crane builds three stages.
        while(!g.bridgeOpen) {
            follow(g,Guide.next(g))
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
            assertTrue("bridge: fill=${g.bridgeFill} stage=${g.bridgeStage} load=${g.load}",++steps<600)
        }
        while(!g.blocks.complete) {
            follow(g,Guide.next(g))
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
            assertTrue("blocks=${g.blocks.placed} carried=${g.blocks.carried}",++steps<650)
        }
    }
    @Test fun dozerActionDrivesThroughTheNearestTallPileWithTheBladeDown() {
        val g=GameState();g.bladeDown=false
        val cell=WorldMap.cell(12.5f,2.5f);g.sand[cell]=5;g.source=31
        g.selected=GameState.Vehicle.DOZER;g.action()
        assertTrue(g.bladeDown);assertNotNull(g.dozerGoal)
        arrive(g);assertTrue("pile spread",g.sand[cell]<5);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        // Without tall piles the same button still lifts and lowers the blade.
        java.util.Arrays.fill(g.sand,0);g.source=36+0;val before=g.bladeDown;g.action();assertNotEquals(before,g.bladeDown)
    }
}
