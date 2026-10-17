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
        surface.setEGLConfigChooser(8,8,8,8,16,0)
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
        input.cancel()
        if(running) { running=false;surface.onPause();audio.pause() }
        settings.save(game)
    }
    fun closePanel(): Boolean {
        if(parents) { parents=false;input.cancel();hud.invalidate();return true }
        return false
    }
    fun worldX(x: Float)=(x-offsetX)/scale
    fun worldY(y: Float)=(y-offsetY)/scale
    fun dragArm(x: Float,y: Float) {
        val point=WorldCamera.spec(camera).armPlane(x,y) ?: return
        game.moveArm(WorldCamera.gameX(point.x),WorldCamera.gameY(point.y))
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
        previous=now
        sceneRenderer.submit(SceneFrame.capture(game,camera,now/1000f))
        surface.requestRender()
        canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale)
        hudRenderer.draw(canvas,game,settings.sound,parents,holdProgress,now/1000f,camera)
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
        if(y in 557f..655f)return when {
            x in 20f..145f -> "excavator"
            x in 155f..280f -> "truck"
            x in 290f..415f -> "dozer"
            x in 500f..625f -> "left"
            x in 645f..770f -> "right"
            x in 850f..1175f -> "action"
            else -> null
        }
        if(game.selected==GameState.Vehicle.EXCAVATOR && y in 100f..535f)return "arm"
        return null
    }
    fun activate(action: String) {
        when(action) {
            "sound" -> settings.sound=!settings.sound
            "camera" -> { camera=(camera+1)%3;settings.camera=camera;input.cancel() }
            "excavator" -> { game.selected=GameState.Vehicle.EXCAVATOR;game.direction=0 }
            "truck" -> { game.selected=GameState.Vehicle.TRUCK;game.direction=0 }
            "dozer" -> { game.selected=GameState.Vehicle.DOZER;game.direction=0 }
            "action" -> game.action()
            "mode" -> game.toggleMode()
            "reset" -> AlertDialog.Builder(context).setTitle("Mulai dunia baru?")
                .setMessage("Pasir dan kendaraan kembali ke posisi awal.")
                .setPositiveButton("Mulai ulang") { _,_ -> game.reset();settings.save(game);parents=false }
                .setNegativeButton("Batal",null).show()
            "close" -> parents=false
        }
        if(action !in listOf("left","right","sound"))audio.play(settings.sound)
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
