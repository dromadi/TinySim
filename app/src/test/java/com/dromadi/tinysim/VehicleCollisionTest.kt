package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VehicleCollisionTest {
    private fun safe(g:GameState) {
        val all=GameState.Vehicle.entries.map(g::vehicleBody)
        for(a in all)for(b in all)if(a.unit.ordinal<b.unit.ordinal)
            assertTrue("${a.unit} vs ${b.unit}: ${a.point} ${b.point}",a.point.distance(b.point)>=a.radius+b.radius+VehicleNavigation.GAP-.001f)
        assertEquals(Terrain.INITIAL_MASS,g.totalMaterial)
    }
    private fun run(g:GameState,frames:Int=3000,dt:Float=.016f) { repeat(frames){g.update(dt);safe(g)} }
    private fun layout()=GameState().apply {
        excavatorX=WorldMap.gameX(2f);excavatorZ=9f
        truckX=WorldMap.gameX(12f);truckZ=4f
        dozerX=WorldMap.gameX(24f);dozerZ=4f
    }
    @Test fun parkedVehicleIsAvoidedAndTheOriginalDestinationIsReached() {
        val g=layout();g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(35f),4f)
        run(g);assertFalse(g.driving);assertEquals(35f,WorldMap.worldX(g.truckX),.001f);assertEquals(4f,g.truckZ,.001f)
        assertEquals(24f,WorldMap.worldX(g.dozerX),0f);assertEquals(4f,g.dozerZ,0f)
    }
    @Test fun headOnCrossingNeverInterpenetratesEvenWhileTurning() {
        val g=layout();g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(34f),4f)
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(5f),4f)
        run(g,5000);assertFalse(g.driving);assertEquals(34f,WorldMap.worldX(g.truckX),.001f);assertEquals(5f,WorldMap.worldX(g.dozerX),.001f)
    }
    @Test fun occupiedDestinationStopsAtSafeDistanceAndCanBackAway() {
        val g=layout();g.driveTo(GameState.Vehicle.TRUCK,g.dozerX,g.dozerZ)
        run(g);assertFalse(g.driving);safe(g)
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(8f),-2f);run(g)
        assertEquals(8f,WorldMap.worldX(g.truckX),.001f);assertEquals(-2f,g.truckZ,.001f)
    }
    @Test fun allThreeApproachingOnePointStaySeparatedAndStopWithoutBouncing() {
        val g=layout();GameState.Vehicle.entries.forEach { g.driveTo(it,WorldMap.gameX(20f),6f) }
        run(g,6000);assertFalse(g.driving)
        val poses=GameState.Vehicle.entries.map(g::vehicleBody);run(g,200)
        assertEquals(poses,GameState.Vehicle.entries.map(g::vehicleBody))
    }
    @Test fun largeDeltaAndRepeatedTouchRetargetingCannotTunnelThrough() {
        val g=layout();val random=Random(8364)
        repeat(1500) { frame->
            if(frame%25==0)for(unit in GameState.Vehicle.entries)g.driveTo(unit,WorldMap.gameX(random.nextFloat()*42f),-2f+random.nextFloat()*13f)
            g.update(if(frame%3==0)5f else .04f);safe(g)
        }
        g.stopMovement();val poses=GameState.Vehicle.entries.map(g::vehicleBody);run(g,100);assertEquals(poses,GameState.Vehicle.entries.map(g::vehicleBody))
    }
    @Test fun overlappingLegacyPosesAreSeparatedWithoutResettingInventory() {
        val g=layout();g.source=30;g.load=6;g.excavatorX=WorldMap.gameX(20f);g.excavatorZ=5f;g.truckX=g.excavatorX;g.dozerX=g.excavatorX;g.truckZ=5f;g.dozerZ=5f
        val restored=GameState();restored.restore(g.snapshot());safe(restored);assertEquals(6,restored.load);assertEquals(30,restored.source)
        assertFalse(restored.driving);val copy=GameState();copy.restore(restored.snapshot());assertEquals(restored.snapshot(),copy.snapshot())
    }
    @Test fun overlappingSavesAndOccupiedGoalsAtMapCornersStayBounded() {
        for(x in listOf(0f,47f))for(z in listOf(-2.8f,11.5f)) {
            val old=GameState();old.excavatorX=WorldMap.gameX(x);old.truckX=old.excavatorX;old.dozerX=old.excavatorX
            old.excavatorZ=z;old.truckZ=z;old.dozerZ=z
            val g=GameState();g.restore(old.snapshot());safe(g)
            for(unit in GameState.Vehicle.entries)g.driveTo(unit,WorldMap.gameX(x),z)
            run(g,1500);assertFalse(g.driving)
            for(unit in GameState.Vehicle.entries)assertTrue(VehicleNavigation.inside(g.vehicleBody(unit).point,unit))
        }
    }
    @Test fun aSweptSegmentDetectsAnObstacleEvenWhenBothEndsAreFree() {
        val unit=GameState.Vehicle.TRUCK;val obstacle=VehicleNavigation.Body(GameState.Vehicle.DOZER,VehicleNavigation.Point(20f,5f))
        val start=VehicleNavigation.Point(10f,5f);val end=VehicleNavigation.Point(30f,5f)
        assertTrue(VehicleNavigation.free(start,unit,listOf(obstacle)));assertTrue(VehicleNavigation.free(end,unit,listOf(obstacle)))
        assertFalse(VehicleNavigation.clear(start,end,unit,listOf(obstacle)))
        val plan=VehicleNavigation.plan(start,end,unit,listOf(obstacle));assertTrue(plan.size>1)
        var previous=start;for(p in plan){assertTrue(VehicleNavigation.clear(previous,p,unit,listOf(obstacle)));previous=p}
    }
    @Test fun turningAndBladeStateDoNotShrinkTheSafetyEnvelope() {
        val g=layout();g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(12f),4f);run(g)
        for(angle in 0..359 step 7) { g.dozerHeading=angle.toFloat();g.bladeDown=!g.bladeDown;g.truckHeading=(angle+90f)%360f;g.update(.04f);safe(g) }
    }
}
