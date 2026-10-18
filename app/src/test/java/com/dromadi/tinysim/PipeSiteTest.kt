package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import com.dromadi.tinysim.scene.SceneFrame
import com.dromadi.tinysim.scene.WorldCamera

class PipeSiteTest {
    private fun idle(g:GameState) {
        repeat(15000) { if(!g.driving)return;g.update(.016f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) }
        fail("task did not finish: next=${g.pipes.next(g)} bucket=${g.bucket} pos=${WorldMap.worldX(g.excavatorX)},${g.excavatorZ}")
    }
    private fun dig(g:GameState) { for(i in 0..5) { assertTrue("tap $i",g.pipes.tap(g,i));idle(g);assertTrue("dug $i",g.pipes.dug(g,i)) } }
    private fun install(g:GameState) {
        for(i in 0..2) {
            assertTrue(g.pipes.tap(g,i+6));idle(g);assertEquals(i,g.pipes.carried)
            assertTrue(g.pipes.tap(g,i+6));idle(g);assertTrue(g.pipes.hasPipe(i));assertEquals(-1,g.pipes.carried)
        }
    }
    @Test fun childCanDigLiftThreePipesAndBuryThemUsingOnlyPictureHints() {
        val g=GameState();g.startPipes()
        repeat(60) {
            if(g.pipes.complete)return@repeat
            when(val h=Guide.next(g)) {
                is Hint.Card -> g.selected=h.vehicle
                is Hint.Pipe -> assertTrue("marker ${h.id}",g.pipes.tap(g,h.id))
                Hint.Action -> g.action()
                else -> fail("unexpected guide $h")
            }
            idle(g)
        }
        assertTrue("pipeline finished next=${g.pipes.next(g)}",g.pipes.complete)
        assertEquals(7,g.pipes.installed);assertEquals(7,g.pipes.filled)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        assertEquals(0,g.bucket);assertEquals(0,(0..5).sumOf { g.sand[WorldMap.cell(PipeSite.x(it),PipeSite.SPOIL_Z)] })
    }
    @Test fun actualContinuousTrenchIsCutAndSpoilContainsEveryRemovedUnit() {
        val g=GameState();g.startPipes();val before=g.earth.sum();dig(g)
        assertEquals(24,before-g.earth.sum())
        assertEquals(24,(0..5).sumOf { g.sand[WorldMap.cell(PipeSite.x(it),PipeSite.SPOIL_Z)] })
        assertEquals(-.37f,g.surface(13f,PipeSite.Z),.001f)
        val camera=WorldCamera.spec(0,-12f,8f,1.2f,13f,5.8f)
        for((id,point) in GameRenderer.pipeTargets(g,camera)) {
            val pipe=camera.project(com.dromadi.tinysim.scene.Vec3(PipeSite.markerX(id),-.15f,PipeSite.Z))
            assertTrue("badge hides trench",point.second-pipe.second>55f)
        }
        assertEquals(0,g.bucket);assertEquals(0,g.pipes.installed)
    }
    @Test fun contactAndTravelPrecedeSoilChangesAndEachScoopHasVisibleCargo() {
        val g=GameState();g.startPipes();assertTrue(g.pipes.tap(g,5));g.update(.016f)
        assertEquals(4,g.earth[PipeSite.cells(5)[0]]);assertNotNull(g.excavatorGoal)
        var seen=false
        repeat(15000) { if(g.driving) { g.update(.016f);seen=seen || g.bucket>0;assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) } }
        assertTrue(seen);assertTrue(g.pipes.dug(g,5))
    }
    @Test fun cannotInstallBeforeDiggingOrBackfillBeforePipesAndBadTouchesAreHarmless() {
        val g=GameState();g.startPipes();val save=g.snapshot()
        for(i in listOf(-1,6,7,8,9,10,11,99))assertFalse(g.pipes.tap(g,i))
        assertEquals(save,g.snapshot());g.source-=2;g.bucket=2;assertFalse(g.pipes.tap(g,0))
    }
    @Test fun pauseCancelsTasksButPreservesSoilCargoAndInstalledPipes() {
        val g=GameState();g.startPipes();g.pipes.tap(g,0)
        repeat(10000) { if(g.bucket==0 && g.driving)g.update(.016f) }
        assertEquals(2,g.bucket);g.stopMovement();val save=g.snapshot()
        val copy=GameState();copy.restore(save);assertFalse(copy.driving);assertEquals(2,copy.bucket)
        repeat(500) { copy.update(.016f) };assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        assertEquals(g.earth.toList(),copy.earth.toList());assertFalse(copy.canAutoScoop)
        copy.leavePipes();repeat(500) { copy.update(.016f) };assertFalse(copy.scoop())
    }
    @Test fun carriedPipeAndInstalledPipeSurviveColdRestoreWithoutDuplicatingProps() {
        val g=GameState();g.startPipes();dig(g);g.pipes.tap(g,6);idle(g);g.stopMovement()
        val copy=GameState();copy.restore(g.snapshot());assertEquals(0,copy.pipes.carried)
        copy.selected=GameState.Vehicle.CRANE;copy.action();idle(copy);assertTrue(copy.pipes.hasPipe(0))
        val restored=GameState();restored.restore(copy.snapshot());assertEquals(1,restored.pipes.installed);assertEquals(-1,restored.pipes.carried)
        val frame=SceneFrame.capture(restored,0,1f);restored.reset();assertEquals(1,frame.pipeInstalled)
    }
    @Test fun migrateV10AndV11PreservesWorldAndBlockCargo() {
        val g=GameState();g.load=3;g.source-=3;g.blocks.pick(1)
        val old=g.snapshot().substringAfter('|').split(';').take(9).mapIndexed { i,s->if(i in listOf(1,3,4))s.split(',').take(WorldMap.COLUMNS*WorldMap.LEGACY_ROWS).joinToString(",") else s }.joinToString(";")
        for(v in listOf("10|$old","11|$old;1")) {
            val copy=GameState();copy.restore(v);assertEquals(3,copy.load);assertEquals(1,copy.blocks.carried);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        }
    }
    @Test fun malformedSavesAndParentResetCannotCreatePipesOrSoil() {
        val g=GameState()
        for(s in listOf("1,1,7,-1","1,8,0,-1","1,1,0,0","1,0,0,4","NaN")) {
            g.restore(g.snapshot().substringAfter('|').split(';').toMutableList().also { it[9]=s }.joinToString(";",prefix="16|"));assertEquals(GameState().snapshot(),g.snapshot())
        }
        g.startPipes();dig(g);install(g);g.reset();assertEquals(0,g.pipes.installed);assertFalse(g.pipes.active);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun projectedMarkersAreLargeAndInsideTheChildViewport() {
        val g=GameState();g.startPipes()
        val camera=WorldCamera.spec(0,-12f,8f,1.2f,13f,5.8f)
        val marks=GameRenderer.pipeTargets(g,camera);assertEquals(6,marks.size)
        for(i in marks.indices)for(j in 0 until i) {
            val a=marks[i].second;val b=marks[j].second
            assertTrue("overlap ${marks[i].first},${marks[j].first}",kotlin.math.hypot(a.first-b.first,a.second-b.second)>48f)
        }
    }
    @Test fun rapidActionTapsDoNotRestartDiggingAndDirectTravelCancelsIt() {
        val g=GameState();g.startPipes();g.pipes.tap(g,0)
        repeat(15000) { if(g.driving) { g.action();g.update(.016f) } }
        assertTrue(g.pipes.dug(g,0));assertEquals(4,g.sand[WorldMap.cell(PipeSite.x(0),PipeSite.SPOIL_Z)])
        g.pipes.tap(g,1);g.driveTo(GameState.Vehicle.EXCAVATOR,WorldMap.gameX(4f),10f)
        assertFalse(g.pipes.busy);idle(g);assertEquals(4,g.earth[PipeSite.cells(1)[0]])
    }
    @Test fun partialBackfillRestoresAndCanFinishWithoutResettingTheTrench() {
        val g=GameState();g.startPipes();dig(g);install(g);g.pipes.tap(g,9)
        repeat(15000) { if(g.earth[PipeSite.cells(0)[0]]==2)g.update(.016f) }
        assertTrue(g.earth[PipeSite.cells(0)[0]]>2);g.stopMovement()
        val copy=GameState();copy.restore(g.snapshot());assertEquals(g.earth.toList(),copy.earth.toList())
        assertEquals(7,copy.pipes.installed)
        for(id in 9..11) { assertTrue(copy.pipes.tap(copy,id));idle(copy) }
        assertTrue(copy.pipes.complete);assertFalse(copy.pipes.active);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
    }

}
