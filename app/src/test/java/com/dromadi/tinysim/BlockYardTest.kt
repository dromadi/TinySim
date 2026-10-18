package com.dromadi.tinysim

import com.dromadi.tinysim.scene.SceneFrame
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class BlockYardTest {
    private fun idle(g:GameState) {
        repeat(12000) {
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
            if(!g.driving)return
            g.update(.016f)
        }
        fail("block task did not finish")
    }
    @Test fun childCanBuildTheEntireTowerWithTheLargeActionButton() {
        val g=GameState();g.selected=GameState.Vehicle.CRANE
        repeat(3) { id->
            assertTrue(g.blockAction());idle(g)
            assertEquals(id,g.blocks.carried)
            g.action();idle(g)
            assertEquals(-1,g.blocks.carried);assertEquals(id+1,g.blocks.placed)
        }
        assertTrue(g.blocks.complete);assertEquals(3,g.rewardSerial)
        assertFalse(g.blockAction());assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun directTouchesPickUpAndTolerantlySnapToTheTarget() {
        val g=GameState();g.craneX=WorldMap.gameX(29f);g.craneZ=6f
        assertTrue(g.craneTap(24.6f,3.4f));idle(g);assertEquals(2,g.blocks.carried)
        assertTrue(g.craneTap(BlockYard.TARGET_X+1.2f,BlockYard.TARGET_Z+.4f));idle(g)
        assertEquals(0,g.blocks.level[2]);assertEquals(BlockYard.TARGET_X,g.blocks.x[2],0f)
        assertFalse(g.blockTap(2));assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun groundDropCanBePickedUpAgainAndWaterIsRejected() {
        val g=GameState();g.selected=GameState.Vehicle.CRANE;assertTrue(g.blockAction());idle(g)
        assertFalse(g.craneTap(Float.NaN,2f));assertFalse(g.craneTap(52f,2f));assertEquals(0,g.blocks.carried)
        assertTrue(g.craneTap(30f,5f));idle(g);assertEquals(0,g.blocks.placed);assertEquals(-1,g.blocks.carried)
        assertEquals(30f,g.blocks.x[0],0f);assertTrue(g.blockTap(0));idle(g);assertEquals(0,g.blocks.carried)
        assertFalse(g.blockTap(1));assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun pauseAndRestoreKeepTheHeldBlockAndCancelOnlyTheMotion() {
        val g=GameState();g.selected=GameState.Vehicle.CRANE;assertTrue(g.blockAction());idle(g)
        assertTrue(g.craneTap(BlockYard.TARGET_X,BlockYard.TARGET_Z));repeat(20) { g.update(.016f) }
        g.stopMovement();val saved=g.snapshot();val copy=GameState();copy.restore(saved)
        assertEquals(saved,copy.snapshot());assertFalse(copy.driving);assertEquals(0,copy.blocks.carried)
        copy.action();idle(copy);assertEquals(1,copy.blocks.placed)
        val frame=SceneFrame.capture(copy,0,1f);copy.blocks.reset();assertEquals(0,frame.blockLevel[0])
    }
    @Test fun savesFromBeforeBlocksMigrateWithoutLosingTheExistingWorld() {
        val g=GameState();g.load=3;g.source-=3;g.sand[WorldMap.cell(15f,4f)]=2;g.source-=2
        val v9="9|"+g.snapshot().substringAfter('|').split(';').take(8).mapIndexed { i,s->if(i in listOf(1,3,4))s.split(',').take(WorldMap.COLUMNS*WorldMap.LEGACY_ROWS).joinToString(",") else s }.joinToString(";")
        val copy=GameState();copy.restore(v9)
        assertEquals(g.snapshot(),copy.snapshot());assertEquals(3,copy.load);assertEquals(31,copy.source)
        assertEquals(0,copy.blocks.placed);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
    }
    @Test fun invalidBlockSavesAreRejectedAndResetRestoresAllThreeBlocks() {
        val g=GameState();g.blocks.pick(1)
        val bad=g.snapshot().substringAfter('|').split(';').toMutableList().also { it[8]="1:24.0,-1.0,-1:NaN,1.0,-1:24.0,3.0,-1" }.joinToString(";",prefix="13|")
        val copy=GameState();copy.restore(bad);assertEquals(GameState().snapshot(),copy.snapshot())
        g.reset();assertEquals(-1,g.blocks.carried);assertEquals(0,g.blocks.placed)
        assertEquals(listOf(24f,24f,24f),g.blocks.x.toList())
    }
    @Test fun blockSoundHasAUsableSignalGentleAttackAndDecaysToSilence() {
        val s=MachineSounds();s.blockSerial++
        val pcm=DoubleArray(24000) { s.next(1.0/48000) }
        assertTrue(abs(pcm[0])<.003)
        val rms=sqrt(pcm.take(9600).sumOf { it*it }/9600)
        assertTrue("rms=$rms",rms in .02..0.15)
        assertTrue(pcm.all { it.isFinite() && abs(it)<.3 })
        assertTrue(pcm.takeLast(4800).all { it==0.0 })
    }
}
