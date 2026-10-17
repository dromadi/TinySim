package com.dromadi.tinysim

import com.dromadi.tinysim.scene.*
import org.junit.Assert.*
import org.junit.Test

class WorldCameraTest {
    @Test fun projectedArmPointsRoundTripThroughEveryCamera() {
        for(camera in WorldCamera.presets)for(x in listOf(285f,365f,620f,825f))for(y in listOf(220f,340f,440f,466f)) {
            val point=Vec3(WorldCamera.x(x),WorldCamera.y(y),0f)
            val screen=camera.project(point)
            val picked=camera.armPlane(screen.first,screen.second)!!
            assertEquals(x,WorldCamera.gameX(picked.x),.003f)
            assertEquals(y,WorldCamera.gameY(picked.y),.003f)
        }
    }
    @Test fun importantWorldPointsStayBetweenHeaderAndControls() {
        for(camera in WorldCamera.presets)for(x in listOf(-7f,-3.8f,0f,8.5f))for(y in listOf(0f,3.4f)) {
            val p=camera.project(Vec3(x,y,0f))
            assertTrue("x=${p.first}",p.first in 20f..1180f)
            assertTrue("y=${p.second}",p.second in 100f..535f)
        }
    }
    @Test fun orbitZoomPickingIsAccurateAtEveryLimit() {
        for(index in 0..2)for(yaw in listOf(-110f,0f,110f))for(pitch in listOf(-40f,0f,40f))for(zoom in listOf(.8f,1f,1.45f)) {
            val camera=WorldCamera.spec(index,yaw,pitch,zoom)
            for(x in listOf(285f,365f,620f,825f))for(y in listOf(220f,440f,466f)) {
                val world=Vec3(WorldCamera.x(x),WorldCamera.y(y),0f)
                val screen=camera.project(world)
                val picked=camera.armPlane(screen.first,screen.second)!!
                assertEquals(x,WorldCamera.gameX(picked.x),.01f)
                assertEquals(y,WorldCamera.gameY(picked.y),.01f)
            }
        }
    }
    @Test fun invalidCameraValuesRecoverAndKeepFrontFacing() {
        val camera=WorldCamera.spec(99,Float.NaN,Float.POSITIVE_INFINITY,Float.NaN)
        assertTrue(camera.eye.x.isFinite());assertTrue(camera.eye.y.isFinite());assertTrue(camera.eye.z>0f)
        assertNotNull(camera.armPlane(600f,337.5f))
    }
    @Test fun groundPickingRoundTripsAcrossExpandedMapAndCameraPan() {
        for(index in 0..2)for(panX in listOf(0f,19f,40f))for(panZ in listOf(0f,8f))for(zoom in listOf(.45f,1f,1.8f)) {
            val camera=WorldCamera.spec(index,12f,8f,zoom,panX,panZ)
            for(dx in listOf(-5f,0f,5f))for(dz in listOf(-2f,0f,3f)) {
                val point=Vec3(panX+dx,.07f,panZ+dz)
                val screen=camera.project(point);val picked=camera.ground(screen.first,screen.second)!!
                assertEquals(point.x,picked.x,.001f);assertEquals(point.z,picked.z,.001f)
            }
        }
    }
    @Test fun glSnapshotOwnsSandArray() {
        val game=GameState();game.sand[0]=2
        val frame=SceneFrame.capture(game,2,1f)
        game.sand[0]=7
        assertEquals(2,frame.sand[0]);assertEquals(2,frame.camera)
    }
    @Test fun legacySaveUpgradesWithoutLosingSand() {
        val old="620.0,1000.0,365.0,340.0,0,4,32,0,0,0,0,0;"+IntArray(24).joinToString(",")
        val game=GameState();game.restore(old)
        assertEquals(4,game.load);assertEquals(36,game.totalSand)
        game.bladeDown=false;game.tipping=true;game.bedAngle=-32f;game.dozerFacing=-1
        val copy=GameState();copy.restore(game.snapshot())
        assertEquals(game.snapshot(),copy.snapshot())
        assertFalse(copy.bladeDown);assertTrue(copy.tipping);assertEquals(-32f,copy.bedAngle,0f)
    }
}
