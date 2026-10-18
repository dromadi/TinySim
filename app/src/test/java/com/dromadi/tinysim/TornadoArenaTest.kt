package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import com.dromadi.tinysim.scene.SceneFrame

class TornadoArenaTest {
    private fun tick(a:TornadoArena,n:Int=200) { repeat(n) { a.update(.016f);checkInventory(a) } }
    private fun checkInventory(a:TornadoArena) {
        for(b in a.boards) {
            assertEquals(48,b.state.size);assertEquals(48,b.state.count { it in 0..3 })
            assertEquals(b.pieces.sumOf { it.mass },(0..3).sumOf { state->b.state.indices.filter { b.state[it]==state }.sumOf { b.pieces[it].mass } })
            assertTrue(b.state.indices.all { b.x[it].isFinite() && b.z[it].isFinite() && b.y[it].isFinite() && b.x[it] in TornadoArena.MIN_X..TornadoArena.MAX_X && b.z[it] in TornadoArena.MIN_Z..TornadoArena.MAX_Z && b.y[it] in 0f..5f })
            assertTrue(b.state.indices.all { b.state[it]!=2 || b.credited[it] })
        }
    }
    private fun finish(a:TornadoArena) {
        repeat(800) {
            if(a.board.complete)return
            val target=a.hintTarget()
            if(target!=null)a.aim(target.first,target.second)
            tick(a,40)
        }
        fail("round stuck map=${a.map} kind=${a.kind} collected=${a.board.collected} level=${a.board.level}")
    }
    @Test fun allThreeMapsHaveDistinctCompleteReusableInventories() {
        val a=TornadoArena();assertEquals(3,a.boards.size)
        assertNotEquals(a.boards[0].pieces,a.boards[1].pieces);assertNotEquals(a.boards[1].pieces,a.boards[2].pieces)
        for(b in a.boards) { assertEquals(48,b.pieces.size);assertEquals(1,b.level);assertEquals(0,b.contained);assertFalse(b.complete) }
        a.enter();assertTrue(a.open && a.running);checkInventory(a)
    }
    @Test fun lightPropsRiseOrbitAndBecomeVisibleContainedInventoryBeforeHeavyObjectsUnlock() {
        val a=TornadoArena();a.enter();val b=a.board;val light=0;val heavy=32
        a.aim(b.x[heavy],b.z[heavy]);tick(a,400);assertEquals(0,b.state[heavy])
        a.aim(b.x[light],b.z[light]);var airborne=false;repeat(500) { a.update(.016f);airborne=airborne || b.state[light]==1 }
        assertTrue(airborne);assertEquals(2,b.state[light]);assertTrue(b.collected>0);checkInventory(a)
    }
    @Test fun everyMapAndKindCanGrowThroughFourLevelsAndFinishWithoutAClockOrLoss() {
        for(map in 0..2)for(kind in TornadoArena.Kind.entries) {
            val a=TornadoArena();a.enter(map);a.choose(kind);finish(a);tick(a)
            assertEquals(48,a.board.collected);assertEquals(4,a.board.level);assertTrue(a.board.complete);assertEquals(1.5f,a.scale,.01f)
            assertTrue(a.rewardSerial>0);checkInventory(a)
        }
    }
    @Test fun sandHasWiderReachWindTravelsFasterAndIceCanLoosenAHeavyProp() {
        val a=TornadoArena();a.enter();val windRadius=a.radius;a.choose(TornadoArena.Kind.SAND);assertTrue(a.radius>windRadius)
        fun travel(kind:TornadoArena.Kind):Float { val t=TornadoArena();t.enter();t.choose(kind);t.aim(71f,17f);tick(t,30);return t.x }
        assertTrue(travel(TornadoArena.Kind.WIND)>travel(TornadoArena.Kind.ICE))
        val cold=TornadoArena();cold.enter();cold.choose(TornadoArena.Kind.ICE);val id=24
        cold.aim(cold.board.x[id],cold.board.z[id]);tick(cold,500);assertTrue(cold.board.credited[id]);assertTrue(cold.board.level>=1)
        val plain=TornadoArena();plain.enter();plain.aim(plain.board.x[id],plain.board.z[id]);tick(plain,500)
        assertEquals(0,plain.board.state[id]);assertEquals(0f,plain.board.frost[id],0f)
    }
    @Test fun releaseAndReabsorbDoNotFarmGrowthOrDestroyAnyProp() {
        val a=TornadoArena();a.enter();finish(a);val points=a.board.points
        assertTrue(a.release());assertFalse(a.running);assertEquals(0,a.board.contained);assertTrue(a.board.state.all { it==3 });assertTrue(a.board.y.all { it>0f });tick(a,100);assertTrue(a.board.state.all { it==0 })
        a.aim(a.x,a.z);tick(a,1500);assertEquals(points,a.board.points);assertEquals(48,a.board.collected);checkInventory(a)
    }
    @Test fun releasedBuildingPiecesStayOnTheGroundInsteadOfReassemblingThemselves() {
        val a=TornadoArena();a.enter(1);finish(a);assertTrue(a.release());tick(a,300)
        assertTrue(a.board.y.all { it==0f });assertTrue(a.board.state.all { it==0 });checkInventory(a)
    }
    @Test fun replayResetsOnlyTheSelectedRoundAndOtherMapsRetainTheirProgress() {
        val a=TornadoArena();a.enter();finish(a);a.enter(1);a.choose(TornadoArena.Kind.ICE);tick(a,600)
        val first=a.boards[0].collected;a.replay();assertEquals(0,a.board.collected);assertEquals(1,a.board.level);assertEquals(first,a.boards[0].collected);assertEquals(TornadoArena.CENTER_X,a.x,0f);assertEquals(TornadoArena.CENTER_Z,a.z,0f)
        a.enter(0);assertTrue(a.board.complete);assertEquals(TornadoArena.Kind.ICE,a.kind)
    }
    @Test fun pauseSettlesAirbornePropsAndColdRestoreNeverStartsAStorm() {
        val a=TornadoArena();a.enter();a.aim(a.board.x[0],a.board.z[0]);var saw=false
        repeat(800) { if(!saw) { a.update(.016f);saw=a.board.state.any { it==1 } } };assertTrue(saw)
        val restored=TornadoArena();restored.restore(a.snapshot());assertFalse(restored.open || restored.running);assertEquals(0f,restored.strength,0f)
        assertFalse(restored.board.state.any { it==1 });checkInventory(restored)
        a.pause();assertFalse(a.running);assertFalse(a.board.state.any { it==1 });assertEquals(0f,a.strength,0f)
        restored.enter();assertTrue(restored.running)
    }
    @Test fun allThreeRoundSavesKeepContainedInventoryKindMapAndBuddyChoice() {
        val a=TornadoArena();a.enter();a.toggleBuddy();finish(a);a.enter(2);a.choose(TornadoArena.Kind.SAND);tick(a,500);a.pause()
        val value=a.snapshot();val copy=TornadoArena();copy.restore(value);assertEquals(value,copy.snapshot());assertTrue(copy.buddy)
        assertEquals(2,copy.map);assertEquals(TornadoArena.Kind.SAND,copy.kind);assertEquals(48,copy.boards[0].collected);checkInventory(copy)
    }
    @Test fun duoHelperActuallyMovesAndAbsorbsPropsWithSharedGrowthAndCanBeTurnedOff() {
        val a=TornadoArena();a.enter(1);a.toggleBuddy();val x=a.buddyX;tick(a,1000)
        assertNotEquals(x,a.buddyX);assertTrue(a.board.state.indices.any { a.board.state[it]==2 && a.board.puller[it]==1 })
        assertTrue(a.board.collected>0);a.toggleBuddy();assertFalse(a.buddy);assertFalse(a.board.state.indices.any { a.board.state[it]==1 && a.board.puller[it]==1 })
        checkInventory(a);finish(a);assertTrue(a.board.complete)
    }
    @Test fun fullGameSaveMigratesAlpha19AndKeepsTheConstructionWorldUntouched() {
        val g=GameState();g.source-=4;g.load=4;g.tipping=true;g.bedAngle=-42f;val before=g.snapshot().substringAfter('|').split(';').take(12).joinToString(";")
        g.stopMovement();g.wind.arena.enter(2);g.wind.arena.choose(TornadoArena.Kind.ICE);repeat(1500) { g.update(.016f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) }
        assertEquals(before,g.snapshot().substringAfter('|').split(';').take(12).joinToString(";"));assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        g.stopMovement();val saved=g.snapshot();val copy=GameState();copy.restore(saved);assertEquals(saved,copy.snapshot());assertFalse(copy.wind.arena.open)
        val old=GameState();old.restore("14|"+before);assertEquals(4,old.load);assertTrue(old.tipping);assertEquals(Terrain.INITIAL_MASS,old.totalMaterial);assertFalse(old.wind.arena.open)
    }
    @Test fun malformedRoundSavesAreRejectedWithoutCreatingASecondInventory() {
        val a=TornadoArena();val valid=a.snapshot()
        val groups=valid.split('#').toMutableList();val cells=groups[1].split(':').toMutableList();val fields=cells[0].split(',').toMutableList()
        fields[2]="NaN";cells[0]=fields.joinToString(",");groups[1]=cells.joinToString(":")
        for(bad in listOf(valid.replaceFirst("0,0,0#","9,0,0#"),groups.joinToString("#"),valid.replaceFirst("#0,0,","#2,0,"))) {
            try { a.restore(bad);fail("accepted malformed save") } catch(_:IllegalArgumentException) { }
        }
        val g=GameState();val parts=g.snapshot().substringAfter('|').split(';').toMutableList();parts[12]="broken";g.restore(parts.joinToString(";",prefix="15|"));assertEquals(GameState().snapshot(),g.snapshot())
    }
    @Test fun randomTouchModeAndPauseSequencesStayBoundedAndNeverChangeWorldMaterial() {
        val g=GameState();val a=g.wind.arena;a.enter();val random=java.util.Random(881)
        repeat(4000) { i->
            if(i%90==0)a.aim(random.nextFloat()*200-50,random.nextFloat()*100-20)
            if(i%200==0)a.choose(TornadoArena.Kind.entries[random.nextInt(3)])
            if(i%500==0)a.enter(random.nextInt(3))
            if(i%700==0)a.toggleBuddy()
            if(i%800==0)a.release()
            g.update(if(i%99==0)Float.NaN else .04f);checkInventory(a);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
        }
        val x=a.targetX;a.aim(Float.NaN,Float.POSITIVE_INFINITY);assertEquals(x,a.targetX,0f)
    }
    @Test fun glSnapshotOwnsAllArenaArraysAndNeverReadsLiveBoards() {
        val g=GameState();g.wind.arena.enter();tick(g.wind.arena)
        val f=SceneFrame.capture(g,0,1f);val positions=f.arena.px.copyOf();val states=f.arena.state.copyOf()
        g.wind.arena.replay();assertArrayEquals(positions,f.arena.px,0f);assertArrayEquals(states,f.arena.state);assertTrue(f.arena.open)
    }
    @Test fun threeAudioKindsAndAbsorptionHaveAudibleGentleBoundedSignals() {
        val clips=TornadoArena.Kind.entries.map { kind->
            val s=MachineSounds();s.wind=1f;s.windKind=kind.ordinal;s.absorbSerial++
            DoubleArray(48000) { s.next(1.0/48000) }.also { pcm->assertTrue(abs(pcm[0])<.003);assertTrue(pcm.all { it.isFinite() && abs(it)<.35 });assertTrue(sqrt(pcm.sumOf { it*it }/pcm.size)>.01) }
        }
        assertFalse(clips[0].contentEquals(clips[1]));assertFalse(clips[0].contentEquals(clips[2]))
    }
}
