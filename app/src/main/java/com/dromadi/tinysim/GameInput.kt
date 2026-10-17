package com.dromadi.tinysim
import android.view.MotionEvent
/** One owner pointer prevents extra fingers from jumping the arm or moving vehicles. */
class GameInput(private val view: GameView) {
    private var pointer = -1
    private var held: String? = null
    private var start = 0L
    fun cancel() { pointer=-1; held=null; view.game.direction=0; view.holdProgress=0f }
    fun tick(now: Long) {
        if(held=="parents") {
            view.holdProgress=((now-start)/3000f).coerceIn(0f,1f)
            if(view.holdProgress>=1f) { view.parents=true; cancel() }
        }
    }
    fun handle(event: MotionEvent): Boolean {
        val x=view.worldX(event.x); val y=view.worldY(event.y)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer=event.getPointerId(0); start=event.eventTime
                held=view.hit(x,y)
                when(held) {
                    "left" -> view.game.direction=-1
                    "right" -> view.game.direction=1
                    "arm" -> view.dragArm(x,y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i=event.findPointerIndex(pointer)
                if(i<0) { cancel(); return true }
                val px=view.worldX(event.getX(i)); val py=view.worldY(event.getY(i))
                if(held=="arm") view.dragArm(px,py)
                else if(view.hit(px,py)!=held) cancel()
            }
            MotionEvent.ACTION_POINTER_UP -> if(event.getPointerId(event.actionIndex)==pointer) cancel()
            MotionEvent.ACTION_UP -> {
                val action=held
                if(action=="arm")view.dragArm(x,y)
                if(action!=null && action==view.hit(x,y) && action!="parents" && action!="arm") view.activate(action)
                cancel()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }
}
