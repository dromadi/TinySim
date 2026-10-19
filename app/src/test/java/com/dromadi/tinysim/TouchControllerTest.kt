package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test

class TouchControllerTest {
    private class Harness(var zone:String="scene",var unit:GameState.Vehicle?=null) {
        val events=mutableListOf<Gesture>()
        val controller=TouchController({x,_->if(x<0f)null else zone},{_,_->unit},{false},{false},events::add)
        fun event(phase:TouchPhase,vararg points:TouchPoint,time:Long=0L)=controller.handle(SceneTouch(phase,time,points.toList()))
        fun point(x:Float,y:Float=330f,id:Int=7)=TouchPoint(id,x,y)
        fun has(kind:GestureKind)=events.any { it.kind==kind }
    }
    @Test fun heldWashSpraysUntilReleaseWithoutDrivingPanningOrArmMovement() {
        val h=Harness("wash",GameState.Vehicle.EXCAVATOR)
        h.event(TouchPhase.DOWN,h.point(500f));h.controller.tick(50);h.event(TouchPhase.MOVE,h.point(550f));h.event(TouchPhase.UP,h.point(550f))
        assertTrue(h.has(GestureKind.WASH));assertTrue(h.has(GestureKind.WASH_END))
        assertFalse(h.has(GestureKind.DRIVE));assertFalse(h.has(GestureKind.ARM));assertFalse(h.has(GestureKind.PAN))
        val count=h.events.count { it.kind==GestureKind.WASH };h.controller.tick(100)
        assertEquals(count,h.events.count { it.kind==GestureKind.WASH })
    }
    @Test fun secondFingerAndCancellationStopTheHose() {
        val h=Harness("wash");h.event(TouchPhase.DOWN,h.point(500f))
        h.event(TouchPhase.ADD,h.point(500f),h.point(600f,id=8));assertTrue(h.has(GestureKind.WASH_END));assertFalse(h.has(GestureKind.PAN))
        h.events.clear();h.event(TouchPhase.DOWN,h.point(500f));h.event(TouchPhase.CANCEL,h.point(500f));assertTrue(h.has(GestureKind.WASH_END))
    }
    @Test fun singleGroundTapDoesNotPanOrDrag() {
        val h=Harness();h.event(TouchPhase.DOWN,h.point(500f));h.event(TouchPhase.UP,h.point(504f))
        assertTrue(h.has(GestureKind.SCENE_TAP));assertFalse(h.has(GestureKind.PAN));assertFalse(h.has(GestureKind.DRIVE))
    }
    @Test fun draggingAVehicleSelectsItAndSetsDestinationWithoutPanning() {
        val h=Harness(unit=GameState.Vehicle.TRUCK)
        h.event(TouchPhase.DOWN,h.point(500f));h.event(TouchPhase.MOVE,h.point(560f));h.event(TouchPhase.UP,h.point(620f))
        assertEquals(GameState.Vehicle.TRUCK,h.events.first { it.kind==GestureKind.SELECT }.unit)
        assertEquals(620f,h.events.last { it.kind==GestureKind.DRIVE }.x,0f)
        assertTrue(h.has(GestureKind.FOLLOW));assertFalse(h.has(GestureKind.PAN));assertFalse(h.has(GestureKind.SCENE_TAP))
    }
    @Test fun excavatorBodyDragDrivesWhileBucketDragStillManipulatesArm() {
        val h=Harness(unit=GameState.Vehicle.EXCAVATOR)
        h.event(TouchPhase.DOWN,h.point(400f));h.event(TouchPhase.MOVE,h.point(600f));h.event(TouchPhase.UP,h.point(650f))
        assertTrue(h.has(GestureKind.DRIVE));assertFalse(h.has(GestureKind.PAN));assertFalse(h.has(GestureKind.ARM))
        assertEquals(GameState.Vehicle.EXCAVATOR,h.events.first { it.kind==GestureKind.SELECT }.unit)
    }
    @Test fun emptyGroundDragPansAndCannotAlsoSetADestination() {
        val h=Harness();h.event(TouchPhase.DOWN,h.point(500f));h.event(TouchPhase.MOVE,h.point(550f));h.event(TouchPhase.UP,h.point(580f))
        assertTrue(h.has(GestureKind.PAN));assertFalse(h.has(GestureKind.DRIVE));assertFalse(h.has(GestureKind.SCENE_TAP))
    }
    @Test fun bucketDragIncludesFinalFingerPositionOnlyAsArmInput() {
        val h=Harness("arm");h.event(TouchPhase.DOWN,h.point(500f));h.event(TouchPhase.MOVE,h.point(600f));h.event(TouchPhase.UP,h.point(650f))
        assertEquals(650f,h.events.last { it.kind==GestureKind.ARM }.x,0f)
        assertFalse(h.has(GestureKind.SCENE_TAP));assertFalse(h.has(GestureKind.PAN))
    }
    @Test fun pinchCannotAlsoMoveTheBucketOrActivateGround() {
        val h=Harness("arm")
        h.event(TouchPhase.DOWN,h.point(480f,id=1))
        h.event(TouchPhase.ADD,h.point(480f,id=1),h.point(720f,id=2))
        h.event(TouchPhase.MOVE,h.point(440f,id=1),h.point(760f,id=2))
        h.event(TouchPhase.REMOVE,h.point(440f,id=1),h.point(760f,id=2))
        h.event(TouchPhase.UP,h.point(440f,id=1))
        assertTrue(h.has(GestureKind.PAN));assertEquals(320f/240f,h.events.last { it.kind==GestureKind.ORBIT }.ratio,.001f)
        assertFalse(h.has(GestureKind.ARM));assertFalse(h.has(GestureKind.DRIVE));assertFalse(h.has(GestureKind.SCENE_TAP))
    }
    @Test fun thirdFingerCancelsAndTheRemainingFingerCannotTap() {
        val h=Harness();h.event(TouchPhase.DOWN,h.point(400f,id=1))
        h.event(TouchPhase.ADD,h.point(400f,id=1),h.point(600f,id=2))
        h.event(TouchPhase.ADD,h.point(400f,id=1),h.point(600f,id=2),h.point(800f,id=3))
        h.event(TouchPhase.UP,h.point(400f,id=1));assertFalse(h.has(GestureKind.SCENE_TAP))
    }
    @Test fun cancellingAVehicleDragStopsTheRoute() {
        val h=Harness(unit=GameState.Vehicle.DOZER);h.event(TouchPhase.DOWN,h.point(400f));h.event(TouchPhase.MOVE,h.point(600f))
        h.event(TouchPhase.CANCEL,h.point(600f));assertTrue(h.has(GestureKind.STOP));assertFalse(h.has(GestureKind.FOLLOW))
    }
    @Test fun minimapDragCannotMoveVehiclesOrTheBucket() {
        val h=Harness("map");h.event(TouchPhase.DOWN,h.point(80f));h.event(TouchPhase.MOVE,h.point(160f));h.event(TouchPhase.UP,h.point(180f))
        assertEquals(180f,h.events.last { it.kind==GestureKind.MAP }.x,0f)
        assertFalse(h.has(GestureKind.DRIVE));assertFalse(h.has(GestureKind.ARM));assertFalse(h.has(GestureKind.PAN))
    }
    @Test fun parentGateRequiresAnUninterruptedThreeSecondHold() {
        val h=Harness("parents");h.event(TouchPhase.DOWN,h.point(1138f),time=100L);h.controller.tick(3099L)
        assertFalse(h.has(GestureKind.PARENT_OPEN));h.controller.tick(3100L)
        assertTrue(h.has(GestureKind.PARENT_OPEN));assertTrue(h.has(GestureKind.STOP))
        val canceled=Harness("parents");canceled.event(TouchPhase.DOWN,canceled.point(1138f));canceled.event(TouchPhase.CANCEL,canceled.point(1138f));canceled.controller.tick(5000L)
        assertFalse(canceled.has(GestureKind.PARENT_OPEN))
    }
    @Test fun lostOwnerAndButtonExitNeverActivateOnRelease() {
        val h=Harness("sound");h.event(TouchPhase.DOWN,h.point(1000f,id=1));h.event(TouchPhase.MOVE,h.point(1000f,id=2));h.event(TouchPhase.UP,h.point(1000f,id=2))
        assertFalse(h.has(GestureKind.BUTTON))
        val exit=Harness("sound");exit.event(TouchPhase.DOWN,exit.point(1000f));exit.event(TouchPhase.MOVE,exit.point(-5f));exit.event(TouchPhase.UP,exit.point(1000f))
        assertFalse(exit.has(GestureKind.BUTTON))
    }
}
