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
    private val audio=GameAudio(context)
    private val hudRenderer=GameRenderer()
    private val sceneRenderer=SceneRenderer3D(com.dromadi.tinysim.scene.AssetTextureUploader(context.assets))
    private val input=GameInput(this)
    private val surface=GLSurfaceView(context)
    private val hud=object: View(context) {
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas);drawHud(canvas) }
    }
    var parents=false
    var holdProgress=0f
    private var camera=settings.camera
    var orbitMode=false
    /** Parent setting: show zoom/camera/minimap/stop buttons on top of the child HUD. */
    var advanced=settings.advanced
    // Idle help: a pointing hand after a few quiet seconds, then one gentle automatic step.
    private var lastTouch=SystemClock.uptimeMillis()
    private var assisted=false
    private var hint:Hint?=null
    private var bridgeFramed=false
    /** Scripted device tests turn idle help off so the assist never races their touches. */
    var helpEnabled=true
    private var yaw=settings.yaw
    private var pitch=settings.pitch
    private var zoom=settings.zoom
    private var panX=settings.panX
    private var panZ=settings.panZ
    private data class CameraPose(val camera:Int,val yaw:Float,val pitch:Float,val zoom:Float,val x:Float,val z:Float)
    private var worldCamera:CameraPose?=null
    private var lastArenaReward=0
    private var lastArenaAbsorb=0
    private var following=false
    private fun enterArena(index:Int=game.wind.arena.map) {
        if(worldCamera==null)worldCamera=CameraPose(camera,yaw,pitch,zoom,panX,panZ)
        game.stopMovement();game.wind.arena.enter(index);following=false;orbitMode=false
        camera=0;yaw=-12f;pitch=10f;zoom=.82f;panX=TornadoArena.CENTER_X;panZ=TornadoArena.CENTER_Z
    }
    private fun leaveArena() {
        game.wind.arena.leave()
        worldCamera?.let { camera=it.camera;yaw=it.yaw;pitch=it.pitch;zoom=it.zoom;panX=it.x;panZ=it.z }
        worldCamera=null;saveCamera()
    }
    fun cameraSpec()=WorldCamera.spec(camera,yaw,pitch,zoom,panX,panZ)
    private fun groundAt(x:Float,y:Float)=if(game.wind.arena.open)cameraSpec().ground(x,y) else cameraSpec().ground(x,y,game::surface)
    fun followSelected() { following=true }
    fun pan(fromX:Float,fromY:Float,toX:Float,toY:Float) {
        val a=groundAt(fromX,fromY) ?: return;val b=groundAt(toX,toY) ?: return
        following=false;panX=(panX+a.x-b.x).coerceIn(-8f,PAN_MAX_X);panZ=(panZ+a.z-b.z).coerceIn(-3f,21f)
    }
    fun jumpMap(x:Float,y:Float) {
        following=false
        panX=(WorldMap.MIN_X+(x-32f)/192f*(WorldMap.MAX_X-WorldMap.MIN_X)+.3f).coerceIn(-8f,PAN_MAX_X)
        panZ=(WorldMap.MIN_Z+(y-314f)/82f*(WorldMap.MAX_Z-WorldMap.MIN_Z)).coerceIn(-3f,21f)
    }
    fun selectUnit(vehicle:GameState.Vehicle,focus:Boolean=true) { if(game.wind.arena.open)leaveArena();game.wind.halt(); if(game.pipes.active && vehicle in listOf(GameState.Vehicle.MIXER,GameState.Vehicle.WRECKER))game.leavePipes();game.selected=vehicle;following=focus && !game.pipes.active;game.greet(vehicle) }
    fun vehicleAt(x:Float,y:Float):GameState.Vehicle? {
        val camera=cameraSpec()
        val candidates=GameState.Vehicle.entries.map { v->game.vehicleBody(v).let { Triple(v,it.point.x,it.point.z) } }
        return candidates.mapNotNull { (unit,wx,wz)->
            val p=runCatching { camera.project(com.dromadi.tinysim.scene.Vec3(wx,1.1f,wz)) }.getOrNull() ?: return@mapNotNull null
            val distance=kotlin.math.hypot(x-p.first,y-p.second)
            if(distance<68f)unit to distance else null
        }.minByOrNull { it.second }?.first
    }
    fun windAt(x:Float,y:Float) { val p=groundAt(x,y) ?: return;if(game.wind.arena.open)game.wind.arena.aim(p.x,p.z) else game.wind.aim(p.x,p.z) }
    fun driveAt(x:Float,y:Float) {
        val point=groundAt(x,y) ?: return
        if(point.x !in WorldMap.MIN_X..WorldMap.MAX_X || point.z !in WorldMap.MIN_Z..WorldMap.MAX_Z)return
        following=false;game.driveTo(game.selected,WorldMap.gameX(point.x),point.z)
    }
    fun sceneTap(x:Float,y:Float) {
        // A dumped pile lies close to the truck: prioritize the intended digging surface
        // over the forgiving vehicle hit radius when the excavator is selected.
        // With an empty bucket the excavator digs wherever the child touches, driving there if needed.
        val keeper=cameraSpec().project(com.dromadi.tinysim.scene.Vec3(BridgeKeeper.X,1.4f,BridgeKeeper.Z))
        if(kotlin.math.hypot(x-keeper.first,y-keeper.second)<48f) { game.guardTap();return }
        if(game.selected==GameState.Vehicle.MIXER || game.selected==GameState.Vehicle.WRECKER) {
            val wx=if(game.selected==GameState.Vehicle.MIXER)WorkFleet.PAD_X else WorkFleet.TOWER_X
            val mark=cameraSpec().project(com.dromadi.tinysim.scene.Vec3(wx,if(game.selected==GameState.Vehicle.MIXER).3f else 1.4f,WorkFleet.YARD_Z))
            if(kotlin.math.hypot(x-mark.first,y-mark.second)<65f) { game.action();return }
        }
        if(game.pipes.active) {
            val id=pipeMarkers().minByOrNull { kotlin.math.hypot(x-it.second.first,y-it.second.second) }
            if(id!=null && kotlin.math.hypot(x-id.second.first,y-id.second.second)<40f) { pipeTouch(id.first);return }
        }
        val ground=groundAt(x,y)
        if(!game.pipes.active && game.selected==GameState.Vehicle.EXCAVATOR && game.bucket==0 && ground!=null && game.digAt(ground.x,ground.z))return
        // Hit-test the visible block centre, not its projection on the ground behind it.
        if(game.selected==GameState.Vehicle.CRANE && game.blocks.carried<0 && game.craneCargo==0) {
            val id=(0 until BlockYard.COUNT).filter { game.blocks.level[it]<0 }.map { i->
                val p=cameraSpec().project(com.dromadi.tinysim.scene.Vec3(game.blocks.x[i],game.surface(game.blocks.x[i],game.blocks.z[i])+BlockYard.HEIGHT/2,game.blocks.z[i]))
                i to kotlin.math.hypot(x-p.first,y-p.second)
            }.filter { it.second<52f }.minByOrNull { it.second }?.first
            if(id!=null && game.blockTap(id))return
        }
        if(game.selected==GameState.Vehicle.CRANE && game.blocks.carried>=0) {
            val targetHeight=game.surface(BlockYard.TARGET_X,BlockYard.TARGET_Z)+game.blocks.placed*BlockYard.HEIGHT+.4f
            val target=cameraSpec().project(com.dromadi.tinysim.scene.Vec3(BlockYard.TARGET_X,targetHeight,BlockYard.TARGET_Z))
            if(kotlin.math.hypot(x-target.first,y-target.second)<60f) { game.craneTap(BlockYard.TARGET_X,BlockYard.TARGET_Z);return }
        }
        val unit=vehicleAt(x,y)
        // The crane works wherever the child touches: grab, drop (also into the truck) or drive closer.
        if(game.selected==GameState.Vehicle.CRANE && unit!=GameState.Vehicle.CRANE) {
            if(unit==GameState.Vehicle.TRUCK && game.craneCargo>0) { game.craneTap(WorldMap.worldX(game.truckX),game.truckZ);return }
            if(unit==null && ground!=null) { game.craneTap(ground.x,ground.z);return }
        }
        if(unit!=null) {
            if(game.selected==GameState.Vehicle.EXCAVATOR && game.bucket>0 && unit==GameState.Vehicle.TRUCK)game.action()
            else if(unit==game.selected && unit!=GameState.Vehicle.EXCAVATOR) {
                game.tapSelectedVehicle()
            } else selectUnit(unit)
            return
        }
        val point=groundAt(x,y) ?: return
        driveAt(x,y);followSelected()
    }

    private fun pipeMarkers()=GameRenderer.pipeTargets(game,cameraSpec())
    private fun pipeTouch(id:Int) {
        val unit=if(id<6)GameState.Vehicle.EXCAVATOR else if(id<9)GameState.Vehicle.CRANE else GameState.Vehicle.DOZER
        game.selected=unit;following=false
        if(!game.pipes.tap(game,id) && game.pipes.carried>=0 && !game.craneBusy && game.craneGoal==null)game.installPipe(game.pipes.carried)
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
    fun saveCamera() {
        val saved=worldCamera
        settings.camera=saved?.camera ?: camera;settings.yaw=saved?.yaw ?: yaw;settings.pitch=saved?.pitch ?: pitch
        settings.zoom=saved?.zoom ?: zoom;settings.panX=saved?.x ?: panX;settings.panZ=saved?.z ?: panZ
    }
    private var running=false
    private var previous=0L
    private var lastTransfer=0
    private var lastGreet=0
    private var lastArmX=Float.NaN;private var lastArmY=Float.NaN
    private val motion=Array(GameState.Vehicle.entries.size) { VehicleMotion() }
    private var lastReward=0
    private var scale=1f
    private var offsetX=0f
    private var offsetY=0f
    init {
        settings.restore(game)
        isFocusable=true
        contentDescription="TinySim 3D. Permainan konstruksi: excavator, truk, bulldozer, crane dan susun balok."
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
        running=true;previous=0;lastTouch=SystemClock.uptimeMillis();assisted=false;audio.resume();surface.onResume();hud.invalidate()
    }
    fun pause() {
        input.cancel();game.stopMovement()
        if(running) { running=false;surface.onPause();audio.pause() }
        saveCamera();settings.save(game)
    }
    fun closePanel(): Boolean {
        if(parents) { parents=false;input.cancel();hud.invalidate();return true }
        if(game.wind.arena.open) { leaveArena();input.cancel();hud.invalidate();return true }
        return false
    }
    fun worldX(x: Float)=(x-offsetX)/scale
    fun worldY(y: Float)=(y-offsetY)/scale
    fun dragArm(x: Float,y: Float) {
        val origin=WorldMap.worldX(game.excavatorX)
        val point=cameraSpec().armPlane(x,y,origin,game.excavatorZ,game.excavatorHeading) ?: return
        val a=game.excavatorHeading*kotlin.math.PI.toFloat()/180f
        val local=(point.x-origin)*kotlin.math.cos(a)-(point.z-game.excavatorZ)*kotlin.math.sin(a)
        game.stopMovement();game.moveArm(200f+local*60f,WorldCamera.gameY(point.y-game.excavatorHeight))
    }
    private fun drawHud(canvas: Canvas) {
        scale=minOf(width/1200f,height/675f).coerceAtLeast(.001f)
        offsetX=(width-1200*scale)/2;offsetY=(height-675*scale)/2
        val now=SystemClock.uptimeMillis()
        input.tick(now)
        if(running && !parents) {
            val dt=if(previous==0L)0f else (now-previous)/1000f
            game.update(dt)
            if(!game.wind.arena.open && !game.wind.active && game.selected==GameState.Vehicle.EXCAVATOR) { if(game.canAutoScoop)game.scoop();game.pour() }
            if(!game.wind.arena.open)game.nextMission()
            if(game.rewardSerial!=lastReward) { audio.play(settings.sound,true);lastReward=game.rewardSerial }
            if(game.transferSerial!=lastTransfer) { when(game.transferKind) { 6->audio.machines.blockSerial++;7->audio.machines.keeperSerial++;8->audio.machines.impactSerial++;else->audio.machines.pourSerial++ } }
            lastTransfer=game.transferSerial
            machineSounds(dt)
        }
        if(following && !parents) {
            val targetX=game.vehicleBody(game.selected).point.x+if(game.selected==GameState.Vehicle.EXCAVATOR)3.3f else .3f
            val targetZ=game.vehicleBody(game.selected).point.z
            val dt=if(previous==0L)0f else ((now-previous)/1000f).coerceIn(0f,.04f)
            val ease=1f-kotlin.math.exp(-4f*dt)
            panX+=(targetX.coerceIn(-8f,PAN_MAX_X)-panX)*ease;panZ+=(targetZ.coerceIn(-3f,21f)-panZ)*ease
        }
        if(running && !parents)houseAndHelp(now)
        val arena=game.wind.arena
        audio.machines.wind=if(parents)0f else if(arena.open)arena.strength*(.65f+.1f*arena.board.level) else game.wind.strength
        audio.machines.windKind=if(arena.open)arena.kind.ordinal else 0
        if(arena.open) {
            audio.machines.engine.fill(0f);audio.machines.hydraulic=0f
            if(arena.absorbSerial!=lastArenaAbsorb) { lastArenaAbsorb=arena.absorbSerial;audio.machines.absorbSerial++ }
            if(arena.rewardSerial!=lastArenaReward) { lastArenaReward=arena.rewardSerial;audio.play(settings.sound,true) }
        }
        audio.update(settings.sound,if(panX<10f)0 else if(panX<28f)1 else 2)
        previous=now
        sceneRenderer.submit(SceneFrame.capture(game,camera,now/1000f,yaw,pitch,zoom,panX,panZ))
        surface.requestRender()
        canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale)
        val mark=if(game.wind.arena.open && helpEnabled && now-lastTouch>HINT_AFTER_MS)game.wind.arena.hintTarget()?.let {
            cameraSpec().project(com.dromadi.tinysim.scene.Vec3(it.first,.7f,it.second))
        }?.takeIf { it.first in 40f..1160f && it.second in 110f..520f } else hintPoint()
        hudRenderer.draw(canvas,game,settings.sound,parents,holdProgress,now/1000f,camera,advanced,orbitMode,zoom,panX,panZ,mark?.first ?: Float.NaN,mark?.second ?: Float.NaN,targets=pipeMarkers())
        canvas.restore()
        if(running)hud.postInvalidateOnAnimation()
    }
    fun hit(x: Float,y: Float): String? {
        if(x !in 0f..1200f || y !in 0f..675f)return null
        if(parents) {
            if(x !in 350f..850f)return null
            val row=((y-GameRenderer.PARENT_TOP)/GameRenderer.PARENT_STEP).toInt()
            if(y<GameRenderer.PARENT_TOP || (y-GameRenderer.PARENT_TOP)%GameRenderer.PARENT_STEP>60f)return null
            return listOf("sound","cameraPreset","advanced","mode","reset","close").getOrNull(row)
        }
        if(x in 1100f..1176f && y in 20f..84f)return "parents"
        if(game.wind.arena.open) {
            if(x in 350f..450f && y in 20f..90f)return "arenaExit"
            if(x in 470f..570f && y in 20f..90f)return "arenaReplay"
            if(x in 590f..690f && y in 20f..90f)return "arenaBuddy"
            if(y in 556f..660f) {
                if(x in 20f..360f && (x-20f)%116f<=108f)return "arenaKind${((x-20f)/116f).toInt()}"
                if(x in 400f..740f && (x-400f)%116f<=108f)return "arenaMap${((x-400f)/116f).toInt()}"
                if(x in 840f..1002f)return "arenaRelease"
                if(x in 1018f..1180f)return "arenaToggle"
            }
            return if(y in 100f..535f)"wind" else null
        }
        if(x in 350f..450f && y in 20f..90f)return "windToggle"
        if(x in 470f..570f && y in 20f..90f)return "arena"
        if(advanced) {
            if(x in 1000f..1070f && y in 20f..84f)return "sound"
            if(x in 1098f..1178f && y in 106f..182f)return "camera"
            if(x in 1098f..1178f && y in 194f..270f)return "orbitToggle"
            if(x in 20f..100f && y in 110f..186f)return "zoomIn"
            if(x in 20f..100f && y in 198f..274f)return "zoomOut"
            if(x in 20f..236f && y in 298f..420f)return "map"
            if(x in 1098f..1178f && y in 282f..358f)return "focus"
            if(x in 712f..812f && y in 557f..655f)return "stop"
        }
        if((game.selected==GameState.Vehicle.MIXER || game.selected==GameState.Vehicle.WRECKER) && x in 24f..324f && y in 20f..90f)return "workyard"
        if((game.pipes.active || game.selected==GameState.Vehicle.EXCAVATOR || game.selected==GameState.Vehicle.DOZER) && x in 24f..324f && y in 20f..90f)return "pipes"
        if(game.selected==GameState.Vehicle.CRANE && !game.bridgeReady && game.craneCargo==0 && x in 24f..324f && y in 20f..90f)return "blocks"
        if(y in 556f..660f) {
            if(x in 840f..1180f)return "action"
            if(x in 20f..708f) {
                val n=((x-20f)/116f).toInt()
                if(n in 0..5 && (x-20f)%116f<=108f)return listOf("excavator","truck","dozer","crane","mixer","wrecker")[n]
            }
            return null
        }
        if(y in 100f..535f) {
            if(game.wind.active)return "wind"
            if(game.pipes.active && pipeMarkers().any { kotlin.math.hypot(x-it.second.first,y-it.second.second)<40f })return "scene"
            if(orbitMode && advanced)return "orbit"
            if(game.selected==GameState.Vehicle.EXCAVATOR) {
                val pin=runCatching { cameraSpec().project(com.dromadi.tinysim.scene.Vec3(game.bucketPosition.first,WorldCamera.y(game.armY)+game.excavatorHeight,game.bucketPosition.second)) }.getOrNull()
                if(pin!=null && kotlin.math.hypot(x-pin.first,y-pin.second)<54f)return "arm"
            }
            return "scene"
        }
        return null
    }
    /** Engines follow each vehicle's real speed; the selected one idles; arm motion drives hydraulics. */
    private fun machineSounds(dt:Float) {
        val travel=floatArrayOf(game.excavatorTravel,game.truckTravel,game.dozerTravel,game.craneTravel,game.fleet.mixer.travel,game.fleet.wrecker.travel)
        for(v in motion.indices) {
            motion[v].update(travel[v],dt)
            audio.machines.engine[v]=when {
                motion[v].moving -> .35f+.65f*motion[v].effort
                game.selected.ordinal==v -> .18f
                else -> 0f
            }
        }
        val arm=if(lastArmX.isNaN() || dt<=0f)0f else kotlin.math.hypot(game.armX-lastArmX,game.armY-lastArmY)/dt
        lastArmX=game.armX;lastArmY=game.armY
        // The crane's slewing and hoisting also run on hydraulics.
        audio.machines.hydraulic=maxOf((arm/240f).coerceIn(0f,1f),if(game.craneBusy || game.bridgeClock>0f || game.fleet.busy).7f else 0f)
        if(game.greetSerial!=lastGreet) { lastGreet=game.greetSerial;audio.machines.hornVehicle=game.greetVehicle.ordinal;audio.machines.hornSerial++ }
    }
    /** House stages assemble by themselves once the foundation and materials are ready. */
    private fun houseAndHelp(now:Long) {
        if(game.wind.arena.open || game.wind.active || game.wind.strength>0f) { hint=null;return }
        if(game.houseReady && game.houseFill>=8 && game.houseStage<3 && game.buildClock==0f && game.buildHouse()) {
            following=false;panX=36.3f;panZ=7f
        }
        // The crane builds the bridge stage by stage once the yard holds all eight units.
        // The child's crane builds the bridge; frame the river while a stage goes up.
        if(game.bridgeClock>0f && !bridgeFramed) { bridgeFramed=true;following=false;panX=50f;panZ=5f }
        if(game.bridgeClock==0f)bridgeFramed=false
        val idle=now-lastTouch
        val quiet=game.celebrating<=0f && !game.driving && game.buildClock==0f && game.bridgeClock==0f
        hint=if(helpEnabled && quiet && idle>HINT_AFTER_MS)Guide.next(game) else null
        if(quiet && idle>ASSIST_AFTER_MS && !assisted) { assisted=true;hint?.let(::perform) }
    }
    /** Does exactly what the pointing hand suggests, as if the child had touched it. */
    private fun perform(next:Hint) {
        when(next) {
            is Hint.Card -> selectUnit(next.vehicle)
            Hint.Action -> game.action()
            Hint.Blocks -> activate("blocks")
            is Hint.Pipe -> pipeTouch(next.id)
            is Hint.Ground -> {
                if(game.selected==GameState.Vehicle.EXCAVATOR && game.bucket==0 && game.digAt(next.x,next.z))return
                game.driveTo(game.selected,WorldMap.gameX(next.x),next.z);followSelected()
            }
        }
        audio.play(settings.sound)
    }
    private fun hintPoint():Pair<Float,Float>? = when(val next=hint) {
        null -> null
        is Hint.Card -> GameRenderer.cardCenter(next.vehicle) to 590f
        Hint.Action -> 1010f to 590f
        Hint.Blocks -> 170f to 55f
        is Hint.Pipe -> pipeMarkers().firstOrNull { it.first==next.id }?.second ?: (1010f to 590f)
        is Hint.Ground -> runCatching { cameraSpec().project(com.dromadi.tinysim.scene.Vec3(next.x,game.surface(next.x,next.z),next.z)) }.getOrNull()
            ?.takeIf { it.first in 40f..1160f && it.second in 110f..520f }
    }
    fun activate(action: String) {
        when(action) {
            "arena" -> enterArena()
            "arenaExit" -> leaveArena()
            "arenaReplay" -> { game.wind.arena.replay();enterArena() }
            "arenaBuddy" -> game.wind.arena.toggleBuddy()
            "arenaRelease" -> game.wind.arena.release()
            "arenaToggle" -> game.wind.arena.toggle()
            "arenaMap0","arenaMap1","arenaMap2" -> enterArena(action.last().digitToInt())
            "arenaKind0","arenaKind1","arenaKind2" -> game.wind.arena.choose(TornadoArena.Kind.entries[action.last().digitToInt()])
            "windToggle" -> { game.wind.toggle(game);following=false;orbitMode=false;if(game.wind.active) { camera=0;yaw=-18f;pitch=10f;zoom=1.05f;panX=game.wind.x;panZ=game.wind.z };saveCamera() }
            "sound" -> settings.sound=!settings.sound
            "camera" -> { camera=(camera+1)%3;settings.camera=camera;yaw=0f;pitch=0f;zoom=1f;panX=0f;panZ=0f;following=false;saveCamera();input.cancel() }
            "orbitToggle" -> { orbitMode=!orbitMode;input.cancel() }
            "zoomIn" -> { orbit(0f,0f,1.12f);saveCamera() }
            "zoomOut" -> { orbit(0f,0f,1f/1.12f);saveCamera() }
            "focus" -> followSelected()
            "stop" -> game.stopMovement()
            "cameraPreset" -> { camera=(camera+1)%3;settings.camera=camera;yaw=0f;pitch=0f;zoom=1f;following=false;saveCamera() }
            "advanced" -> { advanced=!advanced;settings.advanced=advanced;if(!advanced)orbitMode=false }
            "excavator" -> selectUnit(GameState.Vehicle.EXCAVATOR)
            "truck" -> selectUnit(GameState.Vehicle.TRUCK)
            "dozer" -> selectUnit(GameState.Vehicle.DOZER)
            "crane" -> selectUnit(GameState.Vehicle.CRANE)
            "mixer" -> selectUnit(GameState.Vehicle.MIXER)
            "wrecker" -> selectUnit(GameState.Vehicle.WRECKER)
            "workyard" -> { game.wind.halt(); if(game.pipes.active)game.leavePipes();following=false;panX=if(game.selected==GameState.Vehicle.MIXER)20f else 33f;panZ=17f;camera=0;yaw=-12f;pitch=8f;zoom=1.1f;saveCamera() }
            "pipes" -> {
                game.wind.halt()
                if(game.pipes.active)game.leavePipes() else game.startPipes()
                camera=0;settings.camera=0;yaw=-12f;pitch=8f;zoom=1.2f;panX=13f;panZ=5.8f;following=false;orbitMode=false;saveCamera()
            }
            "blocks" -> { game.stopMovement();game.blockAction();following=false;panX=27f;panZ=3f;zoom=1.05f }
            "action" -> if(game.wind.active)game.wind.toggle(game) else game.action()
            "mode" -> game.toggleMode()
            "reset" -> AlertDialog.Builder(context).setTitle("Mulai dunia baru?")
                .setMessage("Pasir dan kendaraan kembali ke posisi awal.")
                .setPositiveButton("Mulai ulang") { _,_ -> game.reset();worldCamera=null;panX=0f;panZ=0f;following=false;saveCamera();settings.save(game);parents=false }
                .setNegativeButton("Batal",null).show()
            "close" -> parents=false
        }
        // Vehicle cards honk instead of beeping.
        if(action !in listOf("sound","excavator","truck","dozer","crane","mixer","wrecker"))audio.play(settings.sound)
    }
    override fun onInterceptTouchEvent(event: MotionEvent)=true
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) { lastTouch=SystemClock.uptimeMillis();assisted=false;hint=null }
        val handled=input.handle(event)
        if(event.actionMasked==MotionEvent.ACTION_UP)performClick()
        return handled
    }
    override fun performClick(): Boolean { super.performClick();return true }
    private companion object { const val HINT_AFTER_MS=5000L;const val ASSIST_AFTER_MS=14000L;const val PAN_MAX_X=68f }
    override fun onDetachedFromWindow() { pause();super.onDetachedFromWindow() }
}
