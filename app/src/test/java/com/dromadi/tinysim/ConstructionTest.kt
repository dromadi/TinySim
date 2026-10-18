package com.dromadi.tinysim

import com.dromadi.tinysim.scene.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class ConstructionTest {
    private fun tick(g:GameState,frames:Int=220) { repeat(frames) { g.update(.016f) } }
    private fun arrive(g:GameState) { repeat(5000) { if(!g.driving)return;g.update(.016f) };fail("Route did not finish") }
    @Test fun excavatorTravelsToFarSiteThenScoopsDumpedSandAndLoadsNearbyTruck() {
        val g=GameState();g.source=32;val cell=WorldMap.cell(36.5f,8.5f);g.sand[cell]=4
        assertTrue(g.digAt(36.5f,8.5f));assertNotNull(g.excavatorGoal)
        arrive(g);tick(g);assertTrue(g.scoop());assertEquals(2,g.bucket);assertEquals(2,g.sand[cell]);assertTrue(g.dug[cell]>0)
        assertEquals(36.5f,g.bucketPosition.first,.03f);assertEquals(8.5f,g.bucketPosition.second,.03f)
        g.action();arrive(g);tick(g);assertTrue(g.pour());assertEquals(2,g.load);assertEquals(GameState.START_SAND,g.totalSand)
        g.digAt(36.5f,8.5f);tick(g);assertTrue(g.scoop());assertEquals(0,g.sand[cell]);assertEquals(GameState.START_SAND,g.totalSand)
    }
    @Test fun bucketTracksTheMobileRotatedRigAndTruckZMatters() {
        for(heading in listOf(0f,90f,180f,270f,315f)) {
            val g=GameState();g.excavatorX=WorldMap.gameX(20f);g.excavatorZ=5f;g.excavatorHeading=heading
            val end=g.armWorld(365f);val cell=WorldMap.cell(end.first,end.second)
            g.source=34;g.sand[cell]=2;g.moveArm(365f,440f);tick(g)
            assertTrue("heading $heading",g.scoop());assertEquals(2,g.bucket)
            g.truckX=WorldMap.gameX(end.first);g.truckZ=end.second+4f;g.moveArm(365f,300f);tick(g)
            assertFalse(g.pour());g.truckZ=end.second;assertTrue(g.pour());assertEquals(GameState.START_SAND,g.totalSand)
        }
    }
    @Test fun roadRequiresSandAndLoweredBladeAndConservesInstalledMaterial() {
        val g=GameState();g.dozerX=WorldMap.gameX(13f);g.dozerZ=8.5f
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(25f),8.5f);arrive(g);assertEquals(0,g.roadProgress)
        for(section in 0..3)g.sand[WorldMap.cell(16.5f+section*2f,8.5f)]=1
        g.source=32;g.dozerX=WorldMap.gameX(13f);g.bladeDown=false
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(25f),8.5f);arrive(g);assertEquals(0,g.roadProgress)
        g.dozerX=WorldMap.gameX(13f);g.bladeDown=true;g.dozerHeading=0f
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(25f),8.5f);arrive(g)
        assertEquals(4,g.roadProgress);assertEquals(0,g.westSand());assertEquals(4,g.road.sum());assertEquals(GameState.START_SAND,g.totalSand)
        assertEquals(1,g.rewardSerial)
        val copy=GameState();copy.restore(g.snapshot());assertEquals(g.snapshot(),copy.snapshot())
    }
    @Test fun aRealTruckDeliveryBuildsTheWholeRoadWithOneBladePass() {
        val g=GameState()
        repeat(2) {
            g.moveArm(365f,440f);tick(g);assertTrue(g.scoop())
            g.moveArm(620f,300f);tick(g);assertTrue(g.pour())
        }
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(18.3f),8.5f);arrive(g)
        g.selected=GameState.Vehicle.TRUCK;g.action();tick(g);assertEquals(4,g.westSand())
        g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(40f),-2f);arrive(g)
        g.dozerX=WorldMap.gameX(13f);g.dozerZ=8.5f;g.dozerHeading=0f
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(25f),8.5f);arrive(g)
        assertEquals(4,g.roadProgress);assertEquals(GameState.START_SAND,g.totalSand)
    }
    @Test fun alpha5SaveMigratesAndMalformedRoadOrMarksResetSafely() {
        val g=GameState();g.load=4;g.source=32
        val parts=g.snapshot().substringAfter("|").split(';')
        // A real alpha.5 save holds the 62-column map without the east-bank quarry.
        val old="5|"+parts[0].split(',').take(20).joinToString(",")+";"+IntArray(WorldMap.LEGACY_COLUMNS*WorldMap.LEGACY_ROWS).joinToString(",")
        val copy=GameState();copy.restore(old);assertEquals(4,copy.load);assertEquals(200f,copy.excavatorX,0f);assertEquals(GameState.START_SAND,copy.totalSand)
        copy.restore(g.snapshot().replace(";0,0,0,0;",";2,0,0,0;"));assertEquals(0,copy.load)
        copy.restore(g.snapshot().substringAfter('|').split(';').toMutableList().also { it[3]="0" }.joinToString(";",prefix="14|"));assertEquals(0,copy.load)
    }
    @Test fun stopAndSaveCancelExcavatorRoutesAndPreservePoseAndDigMarks() {
        val g=GameState();g.driveTo(GameState.Vehicle.EXCAVATOR,3000f,10f);tick(g,50);assertTrue(g.excavatorTravel>0)
        g.stopMovement();val x=g.excavatorX;val z=g.excavatorZ;tick(g);assertEquals(x,g.excavatorX,0f);assertEquals(z,g.excavatorZ,0f)
        g.dug[20]=2;val frame=SceneFrame.capture(g,0,1f);g.dug[20]=3;g.road[0]=1
        assertEquals(2,frame.dug[20]);assertEquals(0,frame.road[0])
        g.road[0]=0;val copy=GameState();copy.restore(g.snapshot());assertEquals(g.snapshot(),copy.snapshot());assertFalse(copy.driving)
        g.driveTo(GameState.Vehicle.EXCAVATOR,Float.NaN,5f);assertFalse(g.driving)
    }
    @Test fun armPlanePickingMatchesTheRotatedTranslatedRig() {
        for(heading in listOf(0f,45f,90f,180f,270f)) {
            val g=GameState();g.excavatorX=WorldMap.gameX(35f);g.excavatorZ=8f;g.excavatorHeading=heading
            val end=g.bucketPosition;val camera=WorldCamera.spec(0,0f,0f,1f,35f,8f)
            val original=Vec3(end.first,WorldCamera.y(g.armY),end.second);val p=camera.project(original)
            val picked=camera.armPlane(p.first,p.second,35f,8f,heading)!!
            assertEquals(original.x,picked.x,.003f);assertEquals(original.y,picked.y,.003f);assertEquals(original.z,picked.z,.003f)
        }
    }
    @Test fun loadAssistParksInsideMapEvenWhenExcavatorFacesOutAtBoundary() {
        for(x in listOf(0f,47f))for(z in listOf(-2.8f,11.5f))for(angle in listOf(0f,90f,180f,270f)) {
            val g=GameState();g.excavatorX=WorldMap.gameX(x);g.excavatorZ=z;g.excavatorHeading=angle
            g.bucket=2;g.source=34;g.action();arrive(g);tick(g)
            if(g.bucket>0)assertTrue("pose $x $z $angle",g.pour());assertEquals(2,g.load);assertEquals(GameState.START_SAND,g.totalSand)
        }
    }
    @Test fun roadMissionIsOptionalAndAlreadyBuiltRoadCanCompleteIt() {
        val g=GameState();g.source=32;g.road.fill(1);g.missionMode=true;g.mission=3
        val copy=GameState();copy.restore(g.snapshot());assertEquals(4,copy.missionProgress);assertEquals(1,copy.rewardSerial)
        tick(copy);copy.nextMission();assertEquals(4,copy.mission);assertEquals(GameState.START_SAND,copy.totalSand)
    }
}
