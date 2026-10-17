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
