package com.dromadi.tinysim
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

class GameView(context: Context): View(context) {
    val game=GameState()
    val settings=SettingsStore(context)
    private val audio=GameAudio()
    private val renderer=GameRenderer()
    private val input=GameInput(this)
    var parents=false
    var holdProgress=0f
    private var running=false
    private var previous=0L
    private var lastTransfer=0
    private var lastReward=0
    private var scale=1f; private var offsetX=0f; private var offsetY=0f
    init { settings.restore(game); isFocusable=true; contentDescription="TinySim. Permainan konstruksi: excavator, truk, bulldozer." }
    fun resume() { audio.resume(); running=true; previous=0; invalidate() }
    fun pause() { running=false; input.cancel(); audio.pause(); settings.save(game) }
    fun closePanel(): Boolean { if(parents) { parents=false; input.cancel(); return true }; return false }
    fun worldX(x: Float)=(x-offsetX)/scale
    fun worldY(y: Float)=(y-offsetY)/scale
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now=SystemClock.uptimeMillis()
        input.tick(now)
        if(running && !parents) {
            val dt=if(previous==0L) 0f else (now-previous)/1000f
            game.update(dt)
            if(game.selected==GameState.Vehicle.EXCAVATOR) { game.scoop(); game.pour() }
            game.nextMission()
            if(game.rewardSerial!=lastReward) { audio.play(settings.sound,true); lastReward=game.rewardSerial }
            else if(game.transferSerial!=lastTransfer) audio.play(settings.sound)
            lastTransfer=game.transferSerial
        }
        previous=now
        scale=minOf(width/1200f,height/675f)
        offsetX=(width-1200*scale)/2; offsetY=(height-675*scale)/2
        canvas.drawColor(android.graphics.Color.rgb(31,62,71))
        canvas.save(); canvas.translate(offsetX,offsetY); canvas.scale(scale,scale)
        renderer.draw(canvas,game,settings.sound,parents,holdProgress,now/1000f)
        canvas.restore()
        if(running) postInvalidateOnAnimation()
    }
    fun hit(x: Float,y: Float): String? {
        if(x !in 0f..1200f || y !in 0f..675f) return null
        if(parents) return when { y in 170f..245f && x in 350f..850f -> "sound"
            y in 260f..335f && x in 350f..850f -> "reset"
            y in 350f..425f && x in 350f..850f -> "mode"
            y in 440f..515f && x in 350f..850f -> "close"; else -> null }
        if(y in 20f..84f) return when { x in 1000f..1070f -> "sound"; x in 1100f..1176f -> "parents"; x in 24f..225f -> "mode"; else -> null }
        if(y in 557f..655f) return when {
            x in 20f..145f -> "excavator"; x in 155f..280f -> "truck"; x in 290f..415f -> "dozer"
            x in 500f..625f -> "left"; x in 645f..770f -> "right"
            x in 850f..1175f -> "action"; else -> null }
        if(game.selected==GameState.Vehicle.EXCAVATOR && x in 260f..850f && y in 120f..535f) return "arm"
        return null
    }
    fun activate(action: String) {
        when(action) {
            "sound" -> settings.sound=!settings.sound
            "excavator" -> { game.selected=GameState.Vehicle.EXCAVATOR; game.direction=0 }
            "truck" -> { game.selected=GameState.Vehicle.TRUCK; game.direction=0 }
            "dozer" -> { game.selected=GameState.Vehicle.DOZER; game.direction=0 }
            "action" -> game.action()
            "mode" -> { game.toggleMode() }
            "reset" -> AlertDialog.Builder(context).setTitle("Mulai dunia baru?").setMessage("Pasir dan kendaraan kembali ke posisi awal.").setPositiveButton("Mulai ulang") { _,_ -> game.reset(); settings.save(game); parents=false }.setNegativeButton("Batal",null).show()
            "close" -> parents=false
        }
        if(action !in listOf("left","right","sound")) audio.play(settings.sound)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled=input.handle(event)
        if(event.actionMasked==MotionEvent.ACTION_UP) performClick()
        return handled
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
}
