package com.dromadi.tinysim

import android.view.MotionEvent
import kotlin.math.hypot

/** Explicit camera mode or two scene fingers; gestures never also move the bucket. */
class GameInput(private val view: GameView) {
    private var pointer=-1
    private var held: String?=null
    private var start=0L
    private var lastX=0f;private var lastY=0f;private var spread=0f
    private var twoFinger=false
    private var waitForRelease=false
    fun cancel() {
        pointer=-1;held=null;twoFinger=false;waitForRelease=false
        view.game.direction=0;view.holdProgress=0f
    }
    fun tick(now:Long) {
        if(held=="parents") {
            view.holdProgress=((now-start)/3000f).coerceIn(0f,1f)
            if(view.holdProgress>=1f) { view.parents=true;cancel() }
        }
    }
    private fun scenePoint(event:MotionEvent,i:Int)=view.worldY(event.getY(i)) in 100f..535f
    fun handle(event:MotionEvent):Boolean {
        val x=view.worldX(event.x);val y=view.worldY(event.y)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel();pointer=event.getPointerId(0);start=event.eventTime
                held=view.hit(x,y);lastX=x;lastY=y
                when(held) {
                    "left" -> view.game.direction=-1
                    "right" -> view.game.direction=1
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if(!view.parents && event.pointerCount==2 && scenePoint(event,0) && scenePoint(event,1) && held in listOf("arm","orbit",null)) {
                    view.game.direction=0;held=null;twoFinger=true;waitForRelease=true
                    lastX=(view.worldX(event.getX(0))+view.worldX(event.getX(1)))/2
                    lastY=(view.worldY(event.getY(0))+view.worldY(event.getY(1)))/2
                    spread=hypot(view.worldX(event.getX(0))-view.worldX(event.getX(1)),view.worldY(event.getY(0))-view.worldY(event.getY(1)))
                } else { cancel();waitForRelease=true }
            }
            MotionEvent.ACTION_MOVE -> {
                if(twoFinger && event.pointerCount==2) {
                    val ax=view.worldX(event.getX(0));val bx=view.worldX(event.getX(1))
                    val ay=view.worldY(event.getY(0));val by=view.worldY(event.getY(1))
                    val cx=(ax+bx)/2;val cy=(ay+by)/2;val distance=hypot(ax-bx,ay-by)
                    view.orbit(cx-lastX,cy-lastY,if(spread>20f)distance/spread else 1f)
                    lastX=cx;lastY=cy;spread=distance
                } else if(!waitForRelease) {
                    val i=event.findPointerIndex(pointer)
                    if(i<0) { cancel();return true }
                    val px=view.worldX(event.getX(i));val py=view.worldY(event.getY(i))
                    when(held) {
                        "arm" -> view.dragArm(px,py)
                        "orbit" -> { view.orbit(px-lastX,py-lastY);lastX=px;lastY=py }
                        else -> if(view.hit(px,py)!=held)cancel()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                view.saveCamera();cancel();waitForRelease=true
            }
            MotionEvent.ACTION_UP -> {
                val action=held
                if(!waitForRelease) {
                    if(action=="arm")view.dragArm(x,y)
                    if(action!=null && action==view.hit(x,y) && action !in listOf("parents","arm","orbit"))view.activate(action)
                }
                view.saveCamera();cancel()
            }
            MotionEvent.ACTION_CANCEL -> { view.saveCamera();cancel() }
        }
        return true
    }
}
