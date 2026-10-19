package com.dromadi.tinysim

import kotlin.math.hypot

enum class TouchPhase { DOWN, ADD, MOVE, REMOVE, UP, CANCEL }
data class TouchPoint(val id:Int,val x:Float,val y:Float)
data class SceneTouch(val phase:TouchPhase,val time:Long,val points:List<TouchPoint>)
enum class GestureKind { WASH, WASH_END, WIND, ARM, DRIVE, SELECT, PAN, ORBIT, SCENE_TAP, MAP, FOLLOW, BUTTON, SAVE, STOP, PARENT_OPEN, HOLD }
data class Gesture(val kind:GestureKind,val x:Float=0f,val y:Float=0f,val fromX:Float=0f,val fromY:Float=0f,val ratio:Float=1f,val unit:GameState.Vehicle?=null,val button:String?=null)

/** Pure state machine used by Android input; coordinates already account for viewport letterboxing. */
class TouchController(
    private val hit:(Float,Float)->String?,
    private val vehicleAt:(Float,Float)->GameState.Vehicle?,
    private val orbitMode:()->Boolean,
    private val parents:()->Boolean,
    private val emit:(Gesture)->Unit
) {
    private var pointer=-1
    private var held:String?=null
    private var start=0L
    private var downX=0f;private var downY=0f
    private var lastX=0f;private var lastY=0f;private var spread=0f
    private var candidate:GameState.Vehicle?=null
    private var twoFinger=false;private var waitForRelease=false
    fun cancel() { if(held=="wash")emit(Gesture(GestureKind.WASH_END));pointer=-1;held=null;twoFinger=false;waitForRelease=false;candidate=null;emit(Gesture(GestureKind.HOLD)) }
    fun tick(now:Long) {
        if(held=="wash")emit(Gesture(GestureKind.WASH,lastX,lastY))
        if(held=="parents") {
            val progress=((now-start)/3000f).coerceIn(0f,1f)
            emit(Gesture(GestureKind.HOLD,progress))
            if(progress>=1f) { emit(Gesture(GestureKind.STOP));emit(Gesture(GestureKind.PARENT_OPEN));cancel() }
        }
    }
    fun handle(event:SceneTouch) {
        val first=event.points.firstOrNull()
        val x=first?.x ?: 0f;val y=first?.y ?: 0f
        when(event.phase) {
            TouchPhase.DOWN -> {
                cancel();pointer=first?.id ?: -1;start=event.time
                held=hit(x,y);downX=x;downY=y;lastX=x;lastY=y
                if(held=="scene")candidate=vehicleAt(x,y)
                if(held=="wash")emit(Gesture(GestureKind.WASH,x,y))
            }
            TouchPhase.ADD -> {
                if(!parents() && event.points.size==2 && event.points.all { it.y in 100f..535f } && held in listOf("wind","arm","scene","pan","drive","orbit")) {
                    held=null;twoFinger=true;waitForRelease=true
                    val a=event.points[0];val b=event.points[1]
                    lastX=(a.x+b.x)/2;lastY=(a.y+b.y)/2;spread=hypot(a.x-b.x,a.y-b.y)
                } else { cancel();waitForRelease=true }
            }
            TouchPhase.MOVE -> {
                if(twoFinger && event.points.size==2) {
                    val a=event.points[0];val b=event.points[1]
                    val cx=(a.x+b.x)/2;val cy=(a.y+b.y)/2;val distance=hypot(a.x-b.x,a.y-b.y)
                    if(orbitMode())emit(Gesture(GestureKind.ORBIT,cx-lastX,cy-lastY))
                    else emit(Gesture(GestureKind.PAN,cx,cy,lastX,lastY))
                    emit(Gesture(GestureKind.ORBIT,ratio=if(spread>20f)distance/spread else 1f))
                    lastX=cx;lastY=cy;spread=distance
                } else if(!waitForRelease) {
                    val point=event.points.firstOrNull { it.id==pointer } ?: run { cancel();return }
                    val px=point.x;val py=point.y
                    if(held=="scene" && hypot(px-downX,py-downY)>14f) {
                        val unit=candidate
                        if(unit!=null) { emit(Gesture(GestureKind.SELECT,unit=unit));held="drive" }
                        else held="pan"
                    }
                    when(held) {
                        "wash" -> emit(Gesture(GestureKind.WASH,px,py))
                        "wind" -> emit(Gesture(GestureKind.WIND,px,py))
                        "arm" -> emit(Gesture(GestureKind.ARM,px,py))
                        "drive" -> emit(Gesture(GestureKind.DRIVE,px,py))
                        "pan" -> emit(Gesture(GestureKind.PAN,px,py,lastX,lastY))
                        "orbit" -> emit(Gesture(GestureKind.ORBIT,px-lastX,py-lastY))
                        "map" -> emit(Gesture(GestureKind.MAP,px,py))
                        "scene" -> Unit
                        else -> if(hit(px,py)!=held)cancel()
                    }
                    lastX=px;lastY=py
                }
            }
            TouchPhase.REMOVE -> { emit(Gesture(GestureKind.SAVE));cancel();waitForRelease=true }
            TouchPhase.UP -> {
                if(!waitForRelease) {
                    when(val action=held) {
                        "wash" -> Unit
                        "wind" -> emit(Gesture(GestureKind.WIND,x,y))
                        "arm" -> emit(Gesture(GestureKind.ARM,x,y))
                        "drive" -> { emit(Gesture(GestureKind.DRIVE,x,y));emit(Gesture(GestureKind.FOLLOW)) }
                        "scene" -> emit(Gesture(GestureKind.SCENE_TAP,x,y))
                        "map" -> emit(Gesture(GestureKind.MAP,x,y))
                        "pan","orbit","parents",null -> Unit
                        else -> if(action==hit(x,y))emit(Gesture(GestureKind.BUTTON,button=action))
                    }
                }
                emit(Gesture(GestureKind.SAVE));cancel()
            }
            TouchPhase.CANCEL -> { if(held=="drive")emit(Gesture(GestureKind.STOP));emit(Gesture(GestureKind.SAVE));cancel() }
        }
    }
}
