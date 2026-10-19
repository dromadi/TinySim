package com.dromadi.tinysim

import android.view.MotionEvent

/** Android adapter; the exact gesture state machine is tested without a device. */
class GameInput(private val view:GameView) {
    private val controller=TouchController(view::hit,view::vehicleAt,{view.orbitMode},{view.parents}) { event->
        when(event.kind) {
            GestureKind.WASH -> view.game.wash.spray(view.game,event.x,event.y)
            GestureKind.WASH_END -> view.game.wash.stop()
            GestureKind.WIND -> view.windAt(event.x,event.y)
            GestureKind.ARM -> view.dragArm(event.x,event.y)
            GestureKind.DRIVE -> view.driveAt(event.x,event.y)
            GestureKind.SELECT -> event.unit?.let { view.selectUnit(it,false) }
            GestureKind.PAN -> view.pan(event.fromX,event.fromY,event.x,event.y)
            GestureKind.ORBIT -> view.orbit(event.x,event.y,event.ratio)
            GestureKind.SCENE_TAP -> view.sceneTap(event.x,event.y)
            GestureKind.MAP -> view.jumpMap(event.x,event.y)
            GestureKind.FOLLOW -> view.followSelected()
            GestureKind.BUTTON -> event.button?.let(view::activate)
            GestureKind.SAVE -> view.saveCamera()
            GestureKind.STOP -> view.game.stopMovement()
            GestureKind.PARENT_OPEN -> view.parents=true
            GestureKind.HOLD -> view.holdProgress=event.x
        }
    }
    fun cancel()=controller.cancel()
    fun tick(now:Long)=controller.tick(now)
    fun handle(event:MotionEvent):Boolean {
        val phase=when(event.actionMasked) {
            MotionEvent.ACTION_DOWN->TouchPhase.DOWN
            MotionEvent.ACTION_POINTER_DOWN->TouchPhase.ADD
            MotionEvent.ACTION_MOVE->TouchPhase.MOVE
            MotionEvent.ACTION_POINTER_UP->TouchPhase.REMOVE
            MotionEvent.ACTION_UP->TouchPhase.UP
            MotionEvent.ACTION_CANCEL->TouchPhase.CANCEL
            else->return true
        }
        val points=(0 until event.pointerCount).map { TouchPoint(event.getPointerId(it),view.worldX(event.getX(it)),view.worldY(event.getY(it))) }
        controller.handle(SceneTouch(phase,event.eventTime,points));return true
    }
}
