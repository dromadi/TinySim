package com.dromadi.tinysim

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout

/** Real device input smoke test, with no test-library/runtime dependency in the game. */
class SmokeInstrumentation:Instrumentation() {
    private lateinit var view:GameView
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    private fun ui(block:()->Unit)=runOnMainSync(block)
    private fun event(action:Int,points:List<Pair<Float,Float>>) {
        val time=SystemClock.uptimeMillis()
        val properties=points.indices.map { i->MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords=points.map { p->MotionEvent.PointerCoords().apply { x=p.first*view.width/1200f;y=p.second*view.height/675f;pressure=1f;size=.1f } }.toTypedArray()
        val e=MotionEvent.obtain(time,time,action,points.size,properties,coords,0,0,1f,1f,0,0,0,0)
        view.dispatchTouchEvent(e);e.recycle()
    }
    private fun tap(x:Float,y:Float)=ui { event(MotionEvent.ACTION_DOWN,listOf(x to y));event(MotionEvent.ACTION_UP,listOf(x to y)) }
    private fun waitUntil(description:String,predicate:()->Boolean) {
        val end=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<end) {
            var done=false;ui { done=predicate() };if(done)return
            SystemClock.sleep(100)
        }
        error("Timed out: $description")
    }
    override fun onStart() {
        val result=Bundle()
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            ui {
                view=(activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as GameView)
                view.game.reset();view.game.selected=GameState.Vehicle.EXCAVATOR
            }
            val originalTarget=view.game.targetX to view.game.targetY
            val originalZoom=view.settings.zoom
            // A two-finger pinch must zoom without sending the arm to either finger.
            ui {
                event(MotionEvent.ACTION_DOWN,listOf(480f to 330f))
                event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(480f to 330f,720f to 330f))
                event(MotionEvent.ACTION_MOVE,listOf(440f to 320f,760f to 340f))
                event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(440f to 320f,760f to 340f))
                event(MotionEvent.ACTION_UP,listOf(440f to 320f))
                check(view.game.targetX==originalTarget.first && view.game.targetY==originalTarget.second) { "Pinch also moved bucket" }
                check(view.settings.zoom>originalZoom) { "Pinch did not zoom" }
            }
            tap(1138f,232f)
            val previousYaw=view.settings.yaw
            ui {
                event(MotionEvent.ACTION_DOWN,listOf(500f to 330f))
                event(MotionEvent.ACTION_MOVE,listOf(720f to 360f))
                event(MotionEvent.ACTION_UP,listOf(720f to 360f))
                check(view.settings.yaw!=previousYaw) { "Orbit did not rotate" }
                check(view.game.targetX==originalTarget.first) { "Orbit moved bucket" }
            }
            tap(1138f,232f);tap(1138f,140f) // Exit orbit and reset to a camera preset.
            tap(60f,140f);tap(60f,234f)
            // Touch the same large action a child uses; no direct inventory edits.
            repeat(2) {
                tap(1020f,605f);waitUntil("scoop") { view.game.bucket==2 }
                tap(1020f,605f);waitUntil("pour") { view.game.bucket==0 }
            }
            ui { check(view.game.load==4 && view.game.source==32 && view.game.totalSand==36) }
            tap(218f,605f)
            tap(1020f,605f);waitUntil("dump") { view.game.load==0 }
            ui { check(view.game.sand.sum()==4 && view.game.delivered==4 && view.game.totalSand==36) }
            tap(353f,605f)
            ui { event(MotionEvent.ACTION_DOWN,listOf(562f to 605f)) }
            waitUntil("dozer travels left") { view.game.dozerX<550f }
            ui { event(MotionEvent.ACTION_UP,listOf(562f to 605f)) }
            ui { event(MotionEvent.ACTION_DOWN,listOf(707f to 605f)) }
            waitUntil("dozer spreads sand") { view.game.flattened>0 }
            ui { event(MotionEvent.ACTION_UP,listOf(707f to 605f));check(view.game.totalSand==36) }
            // A cancelled hold and a short tap may not open the parent area.
            tap(1138f,52f);ui { check(!view.parents) }
            ui { event(MotionEvent.ACTION_DOWN,listOf(1138f to 52f));event(MotionEvent.ACTION_CANCEL,listOf(1138f to 52f));check(!view.parents) }
            result.putString("stream","PASS: pinch/orbit isolation, zoom, scoop -> load 4 -> dump 4 -> dozer, conserved 36, parent tap/cancel.\n")
            result.putString("result","PASS")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {
            result.putString("stream",t.stackTraceToString());result.putString("result","FAIL")
            finish(Activity.RESULT_CANCELED,result)
        }
    }
}
