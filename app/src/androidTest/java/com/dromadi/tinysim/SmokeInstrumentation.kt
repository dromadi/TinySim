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
            // Minimap moves the camera to the second zone, then a real ground touch sets a route.
            tap(128f,355f)
            fun destination(wx:Float,wz:Float) {
                var point=0f to 0f
                ui { point=view.cameraSpec().project(com.dromadi.tinysim.scene.Vec3(wx,.07f,wz)) }
                tap(point.first,point.second)
            }
            destination(18f,6f)
            waitUntil("truck reaches second zone") { view.game.truckGoal==null && kotlin.math.abs(WorldMap.worldX(view.game.truckX)-18f)<.1f }
            ui { check(kotlin.math.abs(view.game.truckZ-6f)<.1f) }
            tap(1020f,605f);waitUntil("dump") { view.game.load==0 }
            ui { check(view.game.sand.sum()==4 && view.game.delivered==4 && view.game.totalSand==36) }
            var pileX=0f;var pileZ=0f
            ui { val cell=view.game.sand.indexOfFirst { it>0 };pileX=WorldMap.cellX(cell);pileZ=WorldMap.cellZ(cell) }
            tap(353f,605f);tap(128f,355f)
            destination(pileX-4f,pileZ)
            waitUntil("dozer lines up behind the pile") { view.game.dozerGoal==null && kotlin.math.abs(WorldMap.worldX(view.game.dozerX)-(pileX-4f))<.1f }
            tap(128f,355f);destination(pileX+6f,pileZ)
            waitUntil("dozer spreads sand in second zone") { view.game.flattened>0 }
            tap(765f,605f)
            ui { check(!view.game.driving && view.game.totalSand==36) }
            // Select the mobile excavator and tap real sand left by the truck/dozer.
            ui { val cell=view.game.sand.indexOfFirst { it>0 };pileX=WorldMap.cellX(cell);pileZ=WorldMap.cellZ(cell) }
            tap(81f,605f);tap(128f,355f);destination(pileX,pileZ)
            waitUntil("mobile excavator reaches and re-scoops deposit") { view.game.excavatorGoal==null && view.game.bucket>0 }
            tap(1020f,605f);waitUntil("truck collects re-scooped sand") { view.game.bucket==0 && view.game.load>0 }
            ui {
                check(view.game.dug.any { it>0 } && view.game.totalSand==36)
                val saved=view.game.snapshot();view.pause();view.resume()
                check(!view.game.driving && view.game.snapshot()==saved) { "Pause/resume changed inventory or pose" }
            }
            // A cancelled hold and a short tap may not open the parent area.
            tap(1138f,52f);ui { check(!view.parents) }
            ui { event(MotionEvent.ACTION_DOWN,listOf(1138f to 52f));event(MotionEvent.ACTION_CANCEL,listOf(1138f to 52f));check(!view.parents) }
            result.putString("stream","PASS: pinch/orbit isolation, zoom, tap destination, minimap, scoop -> load 4 -> dump 4 -> dozer in expanded map, mobile excavator re-scoop -> reload, pause/resume, conserved 36, parent tap/cancel.\n")
            result.putString("result","PASS")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {
            result.putString("stream",t.stackTraceToString());result.putString("result","FAIL")
            finish(Activity.RESULT_CANCELED,result)
        }
    }
}
