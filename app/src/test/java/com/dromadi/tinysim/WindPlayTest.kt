package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import com.dromadi.tinysim.scene.*

class WindPlayTest {
    private fun tick(g:GameState,n:Int=200) { repeat(n) { g.update(.016f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) } }
    @Test fun gardenUsesExistingSandOnlyOnceAndMovingWindChangesRealPiles() {
        val g=GameState();g.wind.toggle(g);assertEquals(30,g.source);assertEquals(6,g.sand[WorldMap.cell(24.5f,-.5f)])
        val before=g.sand.copyOf();tick(g);assertTrue(g.wind.moved>0);assertFalse(before.contentEquals(g.sand))
        g.wind.aim(28f,0f);tick(g);assertTrue(g.sand.indices.any { WorldMap.cellX(it)>25f && g.sand[it]>0 })
        g.wind.toggle(g);tick(g);assertEquals(0f,g.wind.strength,0f);g.wind.toggle(g);assertEquals(30,g.source)
    }
    @Test fun blocksLiftTravelSettleAndRemainAvailableForTheCrane() {
        val g=GameState();g.wind.toggle(g);tick(g);assertTrue(g.wind.lift.any { it>0.5f })
        g.wind.aim(29f,1f);tick(g);assertTrue(g.blocks.x.any { it>26f })
        g.wind.toggle(g);tick(g);assertTrue(g.wind.lift.all { it==0f });assertTrue(g.blocks.x.indices.all { BlockYard.validGround(g.blocks.x[it],g.blocks.z[it]) })
        g.selected=GameState.Vehicle.CRANE;assertTrue(g.blockTap(0));repeat(20000) { if(g.driving)g.update(.016f) }
        assertEquals(0,g.blocks.carried);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun carriedAndInstalledBlocksAndAllVehicleCargoAreProtected() {
        val g=GameState();g.blocks.pick(0);g.blocks.put(BlockYard.TARGET_X,BlockYard.TARGET_Z);g.blocks.pick(1)
        g.load=3;g.bucket=2;g.source-=5;val earth=g.earth.copyOf()
        g.wind.toggle(g);g.wind.aim(28f,6f);tick(g,600)
        assertEquals(0,g.blocks.level[0]);assertEquals(28f,g.blocks.x[0],0f);assertEquals(1,g.blocks.carried)
        assertEquals(0f,g.wind.lift[0],0f);assertEquals(0f,g.wind.lift[1],0f)
        assertEquals(3,g.load);assertEquals(2,g.bucket);assertArrayEquals(earth,g.earth)
    }
    @Test fun pauseAndColdRestoreKeepEveryMovedUnitAndPropWithoutRestartingWeather() {
        val g=GameState();g.wind.toggle(g);g.wind.aim(27f,2f);tick(g,400)
        val live=g.snapshot();val copy=GameState();copy.restore(live)
        assertEquals(live,copy.snapshot());assertFalse(copy.wind.active);assertEquals(0f,copy.wind.strength,0f)
        assertTrue(copy.wind.lift.all { it==0f });assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        g.stopMovement();assertFalse(g.wind.active);assertEquals(0f,g.wind.strength,0f)
        val save=g.snapshot();tick(g);assertEquals(save,g.snapshot());copy.wind.toggle(copy);assertEquals(g.source,copy.source)
    }
    @Test fun alpha18SaveMigratesWithoutChangingSixVehiclesOrMaterial() {
        val g=GameState();g.load=4;g.source-=4;g.blocks.pick(2)
        val old=g.snapshot().substringAfter('|').split(';').take(11).mapIndexed { i,s->if(i in listOf(1,3,4))s.split(',').take(WorldMap.COLUMNS*32).joinToString(",") else s }.joinToString(";",prefix="13|")
        val copy=GameState();copy.restore(old)
        assertEquals(g.sand.toList(),copy.sand.toList());assertEquals(4,copy.load);assertEquals(2,copy.blocks.carried)
        assertFalse(copy.wind.prepared);assertEquals(Terrain.INITIAL_MASS,copy.totalMaterial)
        for(v in GameState.Vehicle.entries)assertEquals(g.vehicleBody(v),copy.vehicleBody(v))
    }
    @Test fun invalidWindSavesCannotEraseOrInventSandAndResetRestoresTheGarden() {
        val g=GameState();g.wind.toggle(g);tick(g);g.reset();assertEquals(GameState().snapshot(),g.snapshot())
        for(s in listOf("1,NaN,0","1,52,0","2,24,0","1,24,99")) {
            val parts=g.snapshot().substringAfter('|').split(';').toMutableList();parts[11]=s
            g.restore(parts.joinToString(";",prefix="16|"));assertEquals(GameState().snapshot(),g.snapshot())
        }
    }
    @Test fun arbitraryDragAndBadTimestepsRemainFiniteConservativeAndOnDryLand() {
        val g=GameState();g.wind.toggle(g);g.wind.aim(Float.NaN,Float.POSITIVE_INFINITY);assertEquals(24f,g.wind.targetX,0f)
        val random=java.util.Random(93)
        repeat(3000) { i->if(i%70==0)g.wind.aim(random.nextFloat()*200-60,random.nextFloat()*80-30)
            g.update(if(i%31==0)Float.NaN else if(i%33==0)100f else .04f)
            assertEquals(Terrain.INITIAL_MASS,g.totalMaterial);assertTrue(g.wind.x in -8f..47f && g.wind.z in WorldMap.DRIVE_MIN_Z..WorldMap.DRIVE_MAX_Z)
            assertTrue(g.blocks.x.indices.all { BlockYard.validGround(g.blocks.x[it],g.blocks.z[it]) })
        }
    }
    @Test fun windAtBridgeClosesWaitingGateButNeverLowersItOntoAnOccupiedDeck() {
        val k=BridgeKeeper();k.request(true);repeat(100){k.update(.02f,true,false,true)};assertTrue(k.open)
        repeat(100){k.update(.02f,true,false,true,true)};assertEquals(0f,k.lift,0f);assertTrue(k.wave>0f)
        repeat(100){k.update(.02f,true,true,true,true)};assertTrue(k.open)
        repeat(100){k.update(.02f,true,false,true,false)};assertTrue(k.open)
        val g=GameState();g.wind.toggle(g);g.wind.aim(47f,4.5f);tick(g,500);assertTrue(g.wind.nearBridge)
        g.wind.toggle(g);tick(g);assertFalse(g.wind.nearBridge)
    }
    @Test fun aRealTruckWaitsOutsideTheClosedWindGateThenCrossesAfterWeatherStops() {
        val g=GameState();g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.load=8;g.source-=8;g.selected=GameState.Vehicle.TRUCK
        g.action();tick(g,400);g.action();tick(g)
        repeat(3) { assertTrue(g.buildBridge());repeat(20000) { if(g.driving)g.update(.016f) };tick(g,300) };assertTrue(g.bridgeOpen)
        g.wind.toggle(g);g.wind.aim(47f,4.5f);tick(g,500);assertTrue(g.wind.nearBridge)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(64f),18f)
        repeat(600) { g.update(.016f);assertTrue(WorldMap.worldX(g.truckX)<WorldMap.WEST_BANK_X);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial) }
        assertNotNull(g.truckGoal);g.wind.toggle(g)
        repeat(20000) { if(g.driving) { g.update(.016f);if(WorldMap.worldX(g.truckX) in 49f..55f)assertTrue(g.keeper.open) } }
        assertEquals(64f,WorldMap.worldX(g.truckX),.1f);assertEquals(18f,g.truckZ,.1f);assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    @Test fun cloudModeDragCannotDriveVehicleAndTwoFingersStillPanWithoutFinalWindTap() {
        val events=mutableListOf<Gesture>();val c=TouchController({_,_->"wind"},{_,_->GameState.Vehicle.TRUCK},{false},{false},events::add)
        fun event(p:TouchPhase,vararg points:TouchPoint)=c.handle(SceneTouch(p,0,points.toList()))
        val a=TouchPoint(1,500f,330f);val b=TouchPoint(2,700f,330f)
        event(TouchPhase.DOWN,a);event(TouchPhase.MOVE,a.copy(x=540f));assertTrue(events.any { it.kind==GestureKind.WIND })
        assertFalse(events.any { it.kind in listOf(GestureKind.DRIVE,GestureKind.SELECT,GestureKind.PAN) })
        events.clear();event(TouchPhase.ADD,a,b);event(TouchPhase.MOVE,a.copy(x=480f),b.copy(x=740f));event(TouchPhase.REMOVE,a,b);event(TouchPhase.UP,a)
        assertTrue(events.any { it.kind==GestureKind.PAN });assertTrue(events.any { it.kind==GestureKind.ORBIT });assertFalse(events.any { it.kind==GestureKind.WIND })
    }
    @Test fun snapshotOwnsLiftArrayAndFunnelHasFiniteNondegenerateGeometry() {
        val g=GameState();g.wind.toggle(g);tick(g);val f=SceneFrame.capture(g,0,1f);val original=f.wind.lift.copyOf();g.wind.halt()
        assertArrayEquals(original,f.wind.lift,0f);assertTrue(f.wind.lift.any { it>0 })
        for(mesh in listOf(Geometry.windFunnel(),Geometry.windFunnel(true))) {
            assertTrue(mesh.triangleCount>1000);val data=mesh.vertexData();assertTrue(data.all { it.isFinite() })
            for(i in data.indices step 18) { val n=sqrt(data[i+3]*data[i+3]+data[i+4]*data[i+4]+data[i+5]*data[i+5]);assertEquals(1f,n,.002f) }
        }
    }
    @Test fun windIsAudibleRampsGentlyAndFadesToSilence() {
        val s=MachineSounds();s.wind=1f;val step=1.0/48000;val pcm=DoubleArray(48000*3) { s.next(step) }
        assertTrue(abs(pcm[0])<.001);assertTrue(sqrt(pcm.takeLast(48000).sumOf { it*it }/48000)>.02)
        assertTrue(pcm.all { it.isFinite() && abs(it)<.3 })
        s.wind=0f;val fade=DoubleArray(48000*5){s.next(step)}
        assertTrue(sqrt(fade.takeLast(4800).sumOf { it*it }/4800)<.0001)
        val mixer=SoundscapeMixer();mixer.machines.wind=1f;val out=ShortArray(4800);repeat(5){mixer.render(out)};assertTrue(out.all { it.toInt()==0 })
    }
}
