package com.dromadi.tinysim

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.dromadi.tinysim.scene.SceneFrame
import com.dromadi.tinysim.scene.SceneRenderer3D
import com.dromadi.tinysim.scene.WorldCamera

/** Simulation and input stay on the UI thread; an owned snapshot crosses to the GL thread. */
class GameView(context: Context): FrameLayout(context) {
    val game=GameState()
    val settings=SettingsStore(context)
    private val audio=GameAudio()
    private val hudRenderer=GameRenderer()
    private val sceneRenderer=SceneRenderer3D()
    private val input=GameInput(this)
    private val surface=GLSurfaceView(context)
    private val hud=object: View(context) {
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas);drawHud(canvas) }
    }
    var parents=false
    var holdProgress=0f
    private var camera=settings.camera
    var orbitMode=false
    private var yaw=settings.yaw
    private var pitch=settings.pitch
    private var zoom=settings.zoom
    private var panX=settings.panX
    private var panZ=settings.panZ
    private var following=false
    fun cameraSpec()=WorldCamera.spec(camera,yaw,pitch,zoom,panX,panZ)
    fun followSelected() { following=true }
    fun pan(fromX:Float,fromY:Float,toX:Float,toY:Float) {
        val camera=cameraSpec();val a=camera.ground(fromX,fromY) ?: return;val b=camera.ground(toX,toY) ?: return
        following=false;panX=(panX+a.x-b.x).coerceIn(-8f,44f);panZ=(panZ+a.z-b.z).coerceIn(-3f,11f)
    }
    fun jumpMap(x:Float,y:Float) {
        following=false
        panX=(WorldMap.MIN_X+(x-32f)/192f*(WorldMap.MAX_X-WorldMap.MIN_X)+.3f).coerceIn(-8f,44f)
        panZ=(WorldMap.MIN_Z+(y-314f)/82f*(WorldMap.MAX_Z-WorldMap.MIN_Z)).coerceIn(-3f,11f)
    }
    fun selectUnit(vehicle:GameState.Vehicle,focus:Boolean=true) { game.selected=vehicle;following=focus }
    fun vehicleAt(x:Float,y:Float):GameState.Vehicle? {
        val camera=cameraSpec()
        val candidates=listOf(Triple(GameState.Vehicle.EXCAVATOR,WorldMap.worldX(game.excavatorX),game.excavatorZ),Triple(GameState.Vehicle.TRUCK,WorldMap.worldX(game.truckX),game.truckZ),Triple(GameState.Vehicle.DOZER,WorldMap.worldX(game.dozerX),game.dozerZ))
        return candidates.mapNotNull { (unit,wx,wz)->
            val p=runCatching { camera.project(com.dromadi.tinysim.scene.Vec3(wx,1.1f,wz)) }.getOrNull() ?: return@mapNotNull null
            val distance=kotlin.math.hypot(x-p.first,y-p.second)
            if(distance<68f)unit to distance else null
        }.minByOrNull { it.second }?.first
    }
    fun driveAt(x:Float,y:Float) {
        val point=cameraSpec().ground(x,y) ?: return
        if(point.x !in WorldMap.MIN_X..WorldMap.MAX_X || point.z !in WorldMap.MIN_Z..WorldMap.MAX_Z)return
        following=false;game.driveTo(game.selected,WorldMap.gameX(point.x),point.z)
    }
    fun sceneTap(x:Float,y:Float) {
        // A dumped pile lies close to the truck: prioritize the intended digging surface
        // over the forgiving vehicle hit radius when the excavator is selected.
        val ground=cameraSpec().ground(x,y)
        if(game.selected==GameState.Vehicle.EXCAVATOR && game.bucket==0 && ground!=null && game.digAt(ground.x,ground.z))return
        val unit=vehicleAt(x,y)
        if(unit!=null) {
            if(game.selected==GameState.Vehicle.EXCAVATOR && game.bucket>0 && unit==GameState.Vehicle.TRUCK)game.action()
            else if(unit==game.selected && unit!=GameState.Vehicle.EXCAVATOR) {
                game.stopMovement();game.action()
            } else selectUnit(unit)
            return
        }
        val point=cameraSpec().ground(x,y) ?: return
        if(game.selected==GameState.Vehicle.EXCAVATOR && game.digAt(point.x,point.z))return
        driveAt(x,y);followSelected()
    }

    fun orbit(dx:Float,dy:Float,ratio:Float=1f) {
        following=false
        val base=WorldCamera.spec(camera)
        val d=base.eye-base.target
        val baseYaw=kotlin.math.atan2(d.x,d.z)*180f/kotlin.math.PI.toFloat()
        val basePitch=kotlin.math.asin(d.y/kotlin.math.sqrt(d.dot(d)))*180f/kotlin.math.PI.toFloat()
        yaw=(yaw+dx*.15f).coerceIn(-55f-baseYaw,55f-baseYaw)
        pitch=(pitch+dy*.1f).coerceIn(22f-basePitch,55f-basePitch)
        if(ratio.isFinite())zoom=(zoom*ratio).coerceIn(.45f,1.8f)
    }
    fun saveCamera() { settings.yaw=yaw;settings.pitch=pitch;settings.zoom=zoom;settings.panX=panX;settings.panZ=panZ }
    private var running=false
    private var previous=0L
    private var lastTransfer=0
    private var lastReward=0
    private var scale=1f
    private var offsetX=0f
    private var offsetY=0f
    init {
        settings.restore(game)
        isFocusable=true
        contentDescription="TinySim 3D. Permainan konstruksi: excavator, truk, bulldozer."
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(com.dromadi.tinysim.scene.MultisampleConfigChooser())
        surface.preserveEGLContextOnPause=true
        surface.setRenderer(sceneRenderer)
        surface.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
        surface.isFocusable=false
        addView(surface,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        addView(hud,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
    }
    fun resume() {
        if(running)return
        running=true;previous=0;audio.resume();surface.onResume();hud.invalidate()
    }
    fun pause() {
        input.cancel();game.stopMovement()
        if(running) { running=false;surface.onPause();audio.pause() }
        saveCamera();settings.save(game)
    }
    fun closePanel(): Boolean {
        if(parents) { parents=false;input.cancel();hud.invalidate();return true }
        return false
    }
    fun worldX(x: Float)=(x-offsetX)/scale
    fun worldY(y: Float)=(y-offsetY)/scale
    fun dragArm(x: Float,y: Float) {
        val origin=WorldMap.worldX(game.excavatorX)
        val point=cameraSpec().armPlane(x,y,origin,game.excavatorZ,game.excavatorHeading) ?: return
        val a=game.excavatorHeading*kotlin.math.PI.toFloat()/180f
        val local=(point.x-origin)*kotlin.math.cos(a)-(point.z-game.excavatorZ)*kotlin.math.sin(a)
        game.stopMovement();game.moveArm(200f+local*60f,WorldCamera.gameY(point.y))
    }
    private fun drawHud(canvas: Canvas) {
        scale=minOf(width/1200f,height/675f).coerceAtLeast(.001f)
        offsetX=(width-1200*scale)/2;offsetY=(height-675*scale)/2
        val now=SystemClock.uptimeMillis()
        input.tick(now)
        if(running && !parents) {
            val dt=if(previous==0L)0f else (now-previous)/1000f
            game.update(dt)
            if(game.selected==GameState.Vehicle.EXCAVATOR) { game.scoop();game.pour() }
            game.nextMission()
            if(game.rewardSerial!=lastReward) { audio.play(settings.sound,true);lastReward=game.rewardSerial }
            else if(game.transferSerial!=lastTransfer)audio.play(settings.sound)
            lastTransfer=game.transferSerial
        }
        if(following && !parents) {
            val targetX=when(game.selected) { GameState.Vehicle.EXCAVATOR -> WorldMap.worldX(game.excavatorX)+3.3f;GameState.Vehicle.TRUCK->WorldMap.worldX(game.truckX)+.3f;GameState.Vehicle.DOZER->WorldMap.worldX(game.dozerX)+.3f }
            val targetZ=when(game.selected) { GameState.Vehicle.EXCAVATOR->game.excavatorZ;GameState.Vehicle.TRUCK->game.truckZ;GameState.Vehicle.DOZER->game.dozerZ }
            val dt=if(previous==0L)0f else ((now-previous)/1000f).coerceIn(0f,.04f)
            val ease=1f-kotlin.math.exp(-4f*dt)
            panX+=(targetX.coerceIn(-8f,44f)-panX)*ease;panZ+=(targetZ.coerceIn(-3f,11f)-panZ)*ease
        }
        previous=now
        sceneRenderer.submit(SceneFrame.capture(game,camera,now/1000f,yaw,pitch,zoom,panX,panZ))
        surface.requestRender()
        canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale)
        hudRenderer.draw(canvas,game,settings.sound,parents,holdProgress,now/1000f,camera,orbitMode,zoom,panX,panZ)
        canvas.restore()
        if(running)hud.postInvalidateOnAnimation()
    }
    fun hit(x: Float,y: Float): String? {
        if(x !in 0f..1200f || y !in 0f..675f)return null
        if(parents)return when {
            y in 170f..245f && x in 350f..850f -> "sound"
            y in 260f..335f && x in 350f..850f -> "reset"
            y in 350f..425f && x in 350f..850f -> "mode"
            y in 440f..515f && x in 350f..850f -> "close"
            else -> null
        }
        if(y in 20f..84f)return when {
            x in 1000f..1070f -> "sound"
            x in 1100f..1176f -> "parents"
            x in 24f..225f -> "mode"
            else -> null
        }
        if(x in 1098f..1178f && y in 106f..182f)return "camera"
        if(x in 1098f..1178f && y in 194f..270f)return "orbitToggle"
        if(x in 20f..100f && y in 110f..186f)return "zoomIn"
        if(x in 20f..100f && y in 198f..274f)return "zoomOut"
        if(x in 20f..236f && y in 298f..420f)return "map"
        if(x in 1098f..1178f && y in 282f..358f)return "focus"
        if(y in 557f..655f)return when {
            x in 20f..145f -> "excavator"
            x in 155f..280f -> "truck"
            x in 290f..415f -> "dozer"
            x in 720f..810f -> "stop"
            x in 850f..1175f -> "action"
            else -> null
        }
        if(y in 100f..535f) {
            if(orbitMode)return "orbit"
            if(game.selected==GameState.Vehicle.EXCAVATOR) {
                val pin=runCatching { cameraSpec().project(com.dromadi.tinysim.scene.Vec3(game.bucketPosition.first,WorldCamera.y(game.armY),game.bucketPosition.second)) }.getOrNull()
                if(pin!=null && kotlin.math.hypot(x-pin.first,y-pin.second)<54f)return "arm"
            }
            return "scene"
        }
        return null
    }
    fun activate(action: String) {
        when(action) {
            "sound" -> settings.sound=!settings.sound
            "camera" -> { camera=(camera+1)%3;settings.camera=camera;yaw=0f;pitch=0f;zoom=1f;panX=0f;panZ=0f;following=false;saveCamera();input.cancel() }
            "orbitToggle" -> { orbitMode=!orbitMode;input.cancel() }
            "zoomIn" -> { orbit(0f,0f,1.12f);saveCamera() }
            "zoomOut" -> { orbit(0f,0f,1f/1.12f);saveCamera() }
            "focus" -> followSelected()
            "stop" -> game.stopMovement()
            "excavator" -> selectUnit(GameState.Vehicle.EXCAVATOR)
            "truck" -> selectUnit(GameState.Vehicle.TRUCK)
            "dozer" -> selectUnit(GameState.Vehicle.DOZER)
            "action" -> game.action()
            "mode" -> game.toggleMode()
            "reset" -> AlertDialog.Builder(context).setTitle("Mulai dunia baru?")
                .setMessage("Pasir dan kendaraan kembali ke posisi awal.")
                .setPositiveButton("Mulai ulang") { _,_ -> game.reset();panX=0f;panZ=0f;following=false;saveCamera();settings.save(game);parents=false }
                .setNegativeButton("Batal",null).show()
            "close" -> parents=false
        }
        if(action !in listOf("sound"))audio.play(settings.sound)
    }
    override fun onInterceptTouchEvent(event: MotionEvent)=true
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled=input.handle(event)
        if(event.actionMasked==MotionEvent.ACTION_UP)performClick()
        return handled
    }
    override fun performClick(): Boolean { super.performClick();return true }
    override fun onDetachedFromWindow() { pause();super.onDetachedFromWindow() }
}
