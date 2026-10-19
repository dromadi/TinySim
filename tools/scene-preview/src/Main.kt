import android.opengl.GLES20
import android.graphics.Canvas
import com.dromadi.tinysim.GameRenderer
import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.WorldMap
import com.dromadi.tinysim.scene.*
import java.io.File

fun main(args: Array<String>) {
    val out=StringBuilder("{")
    // Real CC0 material maps from the app assets; uploads are recorded by path for the browser.
    val assets=File(System.getProperty("user.dir"),"../../app/src/main/assets").canonicalFile
    val r=SceneRenderer3D(object:TextureUploader {
        override fun readText(path:String)=File(assets,path).takeIf { it.isFile }?.readText()
        override fun upload(path:String):Boolean { val file=File(assets,path);if(!file.isFile)return false;GLES20.texImageAsset(file.path);return true }
    })
    r.onSurfaceCreated(null,null);r.onSurfaceChanged(null,1200,675)
    out.append("\"init\":[").append(GLES20.ops).append("],\"frames\":{");GLES20.ops.setLength(0)
    val scenes=listOf<Triple<String,Int,(GameState)->Unit>>(
        Triple("detail-excavator",0) { g->prepareVehicleDetail(g,GameState.Vehicle.EXCAVATOR) },
        Triple("detail-truck",0) { g->prepareVehicleDetail(g,GameState.Vehicle.TRUCK) },
        Triple("detail-dozer",0) { g->prepareVehicleDetail(g,GameState.Vehicle.DOZER) },
        Triple("detail-crane",0) { g->prepareVehicleDetail(g,GameState.Vehicle.CRANE) },
        Triple("detail-mixer",0) { g->prepareVehicleDetail(g,GameState.Vehicle.MIXER) },
        Triple("detail-wrecker",0) { g->prepareVehicleDetail(g,GameState.Vehicle.WRECKER) },
        Triple("fleet-excavator",0) { g->prepareFleetWash(g,GameState.Vehicle.EXCAVATOR) },
        Triple("fleet-truck",0) { g->prepareFleetWash(g,GameState.Vehicle.TRUCK) },
        Triple("fleet-dozer",0) { g->prepareFleetWash(g,GameState.Vehicle.DOZER) },
        Triple("fleet-crane",0) { g->prepareFleetWash(g,GameState.Vehicle.CRANE) },
        Triple("fleet-mixer",0) { g->prepareFleetWash(g,GameState.Vehicle.MIXER) },
        Triple("fleet-wrecker",0) { g->prepareFleetWash(g,GameState.Vehicle.WRECKER) },
        Triple("wash-dirty",0) { g->prepareWash(g) },
        Triple("wash-spray",0) { g->prepareWash(g);val p=g.wash.targets(g)[0];g.wash.spray(g,p.first,p.second);repeat(8){g.update(.04f)} },
        Triple("wash-clean",0) { g->prepareWash(g);for(p in g.wash.targets(g)) { g.wash.spray(g,p.first,p.second);repeat(30){g.update(.04f)} };g.wash.stop();repeat(90){g.update(.016f)};check(g.wash.clean) },
        Triple("village-welcome",0) { g->prepareDistrict(g,4);repeat(180){g.update(.016f)};check(g.village.welcomed==15) },
        Triple("village-garden",0) { g->prepareDistrict(g,4);repeat(4){check(g.village.plant(g,it))};repeat(180){g.update(.016f)};check(g.village.planted==15) },
        Triple("village-shelter",0) { g->prepareDistrict(g,4);check(g.village.plant(g,0));g.wind.toggle(g);repeat(80){g.update(.016f)};check((0..3).none { g.village.outside(g,it) }) },
        Triple("district-storm",0) { g->g.recovery.enter(g);g.wind.toggle(g);repeat(160){g.update(.04f)};check(g.recovery.state.any { it==1 }) },
        Triple("district-push",0) { g->prepareDistrict(g,0);g.selected=GameState.Vehicle.DOZER;val old=g.recovery.x.copyOf();g.action();repeat(3000){if(g.recovery.x.contentEquals(old))g.update(.04f)};repeat(25){g.update(.04f)} },
        Triple("district-tip",0) { g->prepareDistrict(g,1);g.selected=GameState.Vehicle.TRUCK;g.action();repeat(6000){if(g.bedAngle> -32f)g.update(.04f)};check(g.recovery.cargo>0 && g.tipping) },
        Triple("district-pour",0) { g->prepareDistrict(g,2);g.selected=GameState.Vehicle.MIXER;g.action();repeat(6000){if(g.recovery.pads.sum()<1)g.update(.04f)};check(g.recovery.pouring==0) },
        Triple("district-lift",0) { g->prepareDistrict(g,3);g.selected=GameState.Vehicle.CRANE;g.action();previewIdle(g);check(g.recovery.held==0) },
        Triple("district-restored",0) { g->prepareDistrict(g,4);repeat(220){g.update(.016f)};check(g.recovery.cycles==1) },
        Triple("arena-wind",0) { g->g.wind.arena.enter();repeat(90){g.update(.016f)};check(g.wind.arena.board.level==1) },
        Triple("arena-sand",0) { g->prepareArena(g,1,com.dromadi.tinysim.TornadoArena.Kind.SAND,4);repeat(20){g.update(.016f)};check(g.wind.arena.board.level==4) },
        Triple("arena-ice",0) { g->prepareArena(g,2,com.dromadi.tinysim.TornadoArena.Kind.ICE,2);val a=g.wind.arena;a.aim(a.board.x[32],a.board.z[32]);repeat(230){g.update(.016f)};check(a.kind==com.dromadi.tinysim.TornadoArena.Kind.ICE) },
        Triple("arena-release",0) { g->prepareArena(g,1,com.dromadi.tinysim.TornadoArena.Kind.SAND,4);repeat(400){g.update(.016f)};check(g.wind.arena.release());repeat(15){g.update(.016f)};check(g.wind.arena.board.state.any { it==3 }) },
        Triple("arena-duo",0) { g->g.wind.arena.enter(1);g.wind.arena.toggleBuddy();g.wind.arena.aim(69f,14.5f);repeat(600){g.update(.016f)};check(g.wind.arena.board.puller.any { it==1 }) },
        Triple("wind-lift",0) { g->g.wind.toggle(g);repeat(180){g.update(.016f)};check(g.wind.lift.any { it>.5f } && g.wind.moved>0) },
        Triple("wind-travel",0) { g->g.wind.toggle(g);repeat(180){g.update(.016f)};g.wind.aim(29f,1f);repeat(200){g.update(.016f)};check(g.blocks.x.any { it>26f }) },
        Triple("wind-settled",0) { g->g.wind.toggle(g);repeat(180){g.update(.016f)};g.wind.aim(28f,1f);repeat(200){g.update(.016f)};g.wind.toggle(g);repeat(200){g.update(.016f)};g.selected=GameState.Vehicle.CRANE;check(g.wind.lift.all { it==0f }) },
        Triple("wind-bridge",0) { g->previewBridge(g);g.wind.toggle(g);g.wind.aim(47f,4.5f);repeat(500){g.update(.016f)};check(g.wind.nearBridge && g.keeper.lift==0f) },
        Triple("mixer-pour",0) { g->g.selected=GameState.Vehicle.MIXER;g.action();repeat(20000){if(g.fleet.pad<3)g.update(.016f)};check(g.fleet.pouring && g.fleet.pad==3) },
        Triple("wreck-impact",0) { g->g.selected=GameState.Vehicle.WRECKER;g.action();repeat(20000){if(g.fleet.hits==0)g.update(.016f)};repeat(45){g.update(.016f)};check(g.fleet.hits==1) },
        Triple("yard-finished",0) { g->g.selected=GameState.Vehicle.MIXER;g.action();previewIdle(g);g.selected=GameState.Vehicle.WRECKER;repeat(3){g.action();previewIdle(g)};repeat(180){g.update(.016f)};check(g.fleet.pad==8 && g.fleet.hits==3) },
        Triple("keeper-open",0) { g->previewBridge(g);g.guardTap();repeat(90){g.update(.016f)};check(g.keeper.open) },
        Triple("pipe-trench",0) { g->preparePipes(g,0) },
        Triple("pipe-lift",0) { g->preparePipes(g,1) },
        Triple("pipe-laid",0) { g->preparePipes(g,2) },
        Triple("pipe-complete",0) { g->preparePipes(g,3) },
        Triple("start-cam1",0) { },
        Triple("start-cam2",1) { },
        Triple("start-cam3",2) { },
        Triple("orbit-close",0) { },
        Triple("orbit-wide",2) { },
        Triple("map-overview",1) { },
        Triple("construction-yard",0) { it.truckX=WorldMap.gameX(18f);it.truckZ=6f;it.truckHeading=315f;it.dozerX=WorldMap.gameX(22f);it.dozerZ=8f;it.source=28;it.load=4;it.sand[WorldMap.cell(16f,6f)]=4;it.selected=GameState.Vehicle.TRUCK;it.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(23f),9f) },
        Triple("building-site",2) { it.truckX=WorldMap.gameX(38f);it.truckZ=7f;it.dozerX=WorldMap.gameX(35f);it.dozerZ=7f;it.dozerHeading=270f;it.source=28;it.sand[WorldMap.cell(34f,8f)]=4;it.sand[WorldMap.cell(35f,8f)]=4;it.selected=GameState.Vehicle.DOZER },
        Triple("mobile-dig",0) { it.excavatorX=WorldMap.gameX(33.5f);it.excavatorZ=8.5f;it.source=30;it.sand[WorldMap.cell(36.5f,8.5f)]=6;it.digAt(36.5f,8.5f);repeat(200){_->it.update(.016f)};it.scoop();it.truckX=WorldMap.gameX(40.5f);it.truckZ=8.5f },
        Triple("rotated-excavator",2) { it.excavatorX=WorldMap.gameX(18f);it.excavatorZ=5f;it.excavatorHeading=270f;it.armX=440f;it.armY=320f;it.bucket=2;it.source=34;it.truckX=WorldMap.gameX(18f);it.truckZ=10f },
        Triple("road-project",0) { it.source=30;it.road[0]=1;it.road[1]=1;it.sand[WorldMap.cell(20.5f,8.5f)]=4;it.dozerX=WorldMap.gameX(18.8f);it.dozerZ=8.5f;it.selected=GameState.Vehicle.DOZER;it.excavatorX=WorldMap.gameX(14f);it.excavatorZ=5f;it.truckX=WorldMap.gameX(24f);it.truckZ=10f },
        Triple("road-complete",0) { it.source=32;it.road.fill(1);it.dozerX=WorldMap.gameX(24f);it.dozerZ=8f;it.selected=GameState.Vehicle.DOZER;it.excavatorX=WorldMap.gameX(14f);it.excavatorZ=5f;it.dug[WorldMap.cell(22f,5f)]=2;it.celebrating=2f },
        Triple("terrain-pit",0) { g->g.excavatorX=WorldMap.gameX(30.5f);g.excavatorZ=6.5f;for(x in listOf(33.5f,34.5f,35.5f,36.5f)){g.earth[WorldMap.cell(x,6.5f)]=0;g.sand[WorldMap.cell(31.5f,9.5f)]+=4};g.armX=440f;g.armY=510f },
        Triple("terrain-mound",0) { g->g.sand[WorldMap.cell(34.5f,7.5f)]=8;g.source=28;g.dozerX=WorldMap.gameX(31.5f);g.dozerZ=7.5f;g.selected=GameState.Vehicle.DOZER },
        Triple("house-materials",0) { g->for(x in listOf(33.5f,34.5f,35.5f,36.5f))g.earth[WorldMap.cell(x,6.5f)]=2;g.load=8;g.excavatorX=WorldMap.gameX(30f);g.excavatorZ=6f;g.truckX=WorldMap.gameX(37.3f);g.truckZ=7.5f;g.truckHeading=0f;g.tipping=true;repeat(240){g.update(.016f)} },
        Triple("house-walls",0) { g->for(x in listOf(33.5f,34.5f,35.5f,36.5f))g.earth[WorldMap.cell(x,6.5f)]=2;g.load=8;g.truckX=WorldMap.gameX(37.3f);g.truckZ=7.5f;g.tipping=true;repeat(240){g.update(.016f)};repeat(2){check(g.buildHouse());repeat(400){g.update(.016f)}} },
        Triple("house-complete",0) { g->for(x in listOf(33.5f,34.5f,35.5f,36.5f))g.earth[WorldMap.cell(x,6.5f)]=2;g.load=8;g.truckX=WorldMap.gameX(37.3f);g.truckZ=7.5f;g.tipping=true;repeat(240){g.update(.016f)};repeat(3){check(g.buildHouse());repeat(400){g.update(.016f)}};g.excavatorX=WorldMap.gameX(29f);g.excavatorZ=5f;g.truckX=WorldMap.gameX(41f);g.truckZ=11f },
        Triple("loaded",0) { it.source=28;it.load=6;it.bucket=2;it.armX=520f;it.armY=330f },
        Triple("dump",0) { it.source=29;it.load=0;it.truckX=760f;it.tipping=true;it.bedAngle=-42f;it.sand[WorldMap.cell(1.2f,0f)]=4;it.sand[WorldMap.cell(2.2f,0f)]=3;it.selected=GameState.Vehicle.TRUCK },
        Triple("bridge-crane",0) { g-> g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.load=8;g.source-=8;g.selected=GameState.Vehicle.TRUCK;g.action();repeat(400){g.update(.016f)};g.action();repeat(200){g.update(.016f)}
            check(g.buildBridge());repeat(2000){ if(g.driving)g.update(.016f) };repeat(260){g.update(.016f)};check(g.buildBridge());repeat(2000){ if(g.driving)g.update(.016f) };repeat(95){g.update(.016f)} },
        Triple("bridge-open",0) { g-> g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.load=8;g.source-=8;g.selected=GameState.Vehicle.TRUCK;g.action();repeat(400){g.update(.016f)};g.action();repeat(200){g.update(.016f)}
            while(g.bridgeStage<3){ check(g.buildBridge());repeat(3000){ if(g.driving)g.update(.016f) };repeat(260){g.update(.016f)} }
            g.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(60f),4.5f);repeat(240){g.update(.016f)} },
        Triple("crane-grab",0) { g-> g.sand[WorldMap.cell(42.5f,6.5f)]=4;g.source-=4;g.truckX=WorldMap.gameX(41f);g.truckZ=10.5f;g.truckHeading=0f;g.selected=GameState.Vehicle.CRANE
            check(g.craneTap(42.5f,6.5f));repeat(2000){ if(g.craneCargo==0)g.update(.016f) };repeat(25){g.update(.016f)} },
        Triple("block-lift",0) { g-> g.selected=GameState.Vehicle.CRANE;check(g.blockAction());repeat(12000) { if(g.blocks.carried<0)g.update(.016f) };repeat(30) { g.update(.016f) } },
        Triple("block-tower",0) { g-> g.selected=GameState.Vehicle.CRANE;repeat(3) { check(g.blockAction());repeat(12000) { if(g.driving)g.update(.016f) };check(g.blockAction());repeat(12000) { if(g.driving)g.update(.016f) } };check(g.blocks.complete);repeat(220) { g.update(.016f) } },
        Triple("vehicle-life",0) { it.selected=GameState.Vehicle.TRUCK;it.driveTo(GameState.Vehicle.TRUCK,WorldMap.gameX(8f),2.2f) },
        Triple("dozer",2) { it.source=27;it.sand[WorldMap.cell(3f,2f)]=2;it.sand[WorldMap.cell(4f,2f)]=2;it.sand[WorldMap.cell(5f,2f)]=1;it.sand[WorldMap.cell(6f,2f)]=4;it.dozerX=900f;it.selected=GameState.Vehicle.DOZER }
    )
    val hud=StringBuilder()
    val chosen=System.getenv("TINYSIM_PREVIEW_SCENES")?.split(',')
    scenes.filter { chosen==null || it.first in chosen }.forEachIndexed { i,(name,cam,setup) ->
        val g=GameState();setup(g);check(g.totalMaterial==com.dromadi.tinysim.Terrain.INITIAL_MASS) { "Invalid inventory in preview $name" }
        if(name=="vehicle-life") {
            // Warm-up frames: the truck drives (dust, smoke, engine bob) and the excavator driver is greeted.
            var t=1.2f+i
            repeat(36) { k-> if(k==2)g.greet(GameState.Vehicle.EXCAVATOR);g.update(.016f)
                r.submit(SceneFrame.capture(g,cam,t,-40f,2f,1.8f,-6.2f,.6f));r.onDrawFrame(null);GLES20.ops.setLength(0);t+=.016f }
        }
        val frame=when(name) {
            "village-welcome","village-garden","village-shelter"->SceneFrame.capture(g,cam,1.2f+i,-12f,12f,1.15f,16f,31f)
            "district-storm"->SceneFrame.capture(g,cam,1.2f+i,-12f,12f,.95f,15f,29f)
            "district-push"->SceneFrame.capture(g,cam,1.2f+i,-18f,12f,1.2f,WorldMap.worldX(g.dozerX)+2f,g.dozerZ)
            "district-tip"->SceneFrame.capture(g,cam,1.2f+i,-20f,14f,1.1f,39f,29f)
            "district-pour"->SceneFrame.capture(g,cam,1.2f+i,-24f,12f,1.35f,12f,26f)
            "district-lift"->SceneFrame.capture(g,cam,1.2f+i,-18f,12f,1.15f,37f,26f)
            "district-restored"->SceneFrame.capture(g,cam,1.2f+i,-12f,12f,.7f,23f,32f)
            "arena-wind","arena-sand","arena-ice","arena-duo","arena-release"->SceneFrame.capture(g,cam,1.2f+i,-12f,10f,.82f,64.5f,17f)
            "wind-lift","wind-travel","wind-settled"->SceneFrame.capture(g,cam,1.2f+i,-18f,10f,1.05f,26f,1f)
            "wind-bridge"->SceneFrame.capture(g,cam,1.2f+i,-18f,10f,.95f,48f,3f)
            "mixer-pour"->SceneFrame.capture(g,cam,1.2f+i,-23f,7f,1.35f,20f,18f)
            "wreck-impact"->SceneFrame.capture(g,cam,1.2f+i,-22f,5f,1.2f,31f,18f)
            "yard-finished"->SceneFrame.capture(g,cam,1.2f+i,-22f,12f,.88f,26f,17f)
            "keeper-open"->SceneFrame.capture(g,cam,1.2f+i,-18f,10f,1.1f,49f,2f)
            "pipe-trench","pipe-lift","pipe-laid","pipe-complete"->SceneFrame.capture(g,cam,1.2f+i,-12f,8f,1.2f,13f,5.8f)
            "orbit-close"->SceneFrame.capture(g,cam,1.2f+i,18f,8f,1.35f)
            "orbit-wide"->SceneFrame.capture(g,cam,1.2f+i,-18f,14f,.8f)
            "map-overview"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,.48f,19f,4f)
            "construction-yard"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,.95f,18f,5f)
            "mobile-dig"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,36f,8f)
            "rotated-excavator"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,18f,7f)
            "road-project","road-complete"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,1.2f,20f,8f)
            "terrain-pit","terrain-mound","house-materials","house-walls","house-complete"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,1.05f,36f,7f)
            "bridge-crane"->SceneFrame.capture(g,cam,1.2f+i,-18f,6f,1.1f,50f,5f)
            "block-lift","block-tower"->SceneFrame.capture(g,cam,1.2f+i,-25f,8f,1.2f,27f,3f)
            "crane-grab"->SceneFrame.capture(g,cam,1.2f+i,-30f,5f,1.3f,44f,7.5f)
            "bridge-open"->SceneFrame.capture(g,cam,1.2f+i,12f,4f,1.05f,53f,5f)
            "vehicle-life"->SceneFrame.capture(g,cam,1.2f+i+36*.016f,-40f,2f,1.8f,-6.2f,.6f)
            "building-site"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1f,36f,6f)
            else->SceneFrame.capture(g,cam,1.2f+i)
        }
        // Replay opens each fixture in a fresh WebGL context. Runtime terrain updates are
        // incremental; without a full fixture upload later shots would inherit flat init soil.
        // Keep this JVM-only reset here: the Android renderer remains incremental.
        val terrainField=SceneRenderer3D::class.java.getDeclaredField("terrain").apply { isAccessible=true }
        (terrainField.get(r) as TerrainMesh).apply { contextLost();update(g.earth,g.sand);upload() }
        r.submit(frame)
        r.onDrawFrame(null)
        val canvas=Canvas();GameRenderer().draw(canvas,g,true,false,0f,frame.seconds,cam,advanced=false,orbitMode=false,zoom=frame.zoom,panX=frame.panX,panZ=frame.panZ,hintX=if(name=="start-cam1")1010f else Float.NaN,hintY=590f,targets=GameRenderer.pipeTargets(g,WorldCamera.spec(cam,frame.yaw,frame.pitch,frame.zoom,frame.panX,frame.panZ)),gardens=GameRenderer.gardenTargets(g,WorldCamera.spec(cam,frame.yaw,frame.pitch,frame.zoom,frame.panX,frame.panZ)))
        if(i>0)hud.append(',');hud.append('"').append(name).append("\":[").append(canvas.ops).append(']')
        if(i>0)out.append(',');out.append('"').append(name).append("\":[").append(GLES20.ops).append(']');GLES20.ops.setLength(0)
    }
    out.append("},\"hud\":{").append(hud).append("}}")
    File(args[0]).writeText(out.toString())
}

fun preparePipes(g:GameState,stage:Int) {
    fun idle() { repeat(15000) { if(!g.driving)return;g.update(.016f) };error("pipe task stuck") }
    g.startPipes();g.selected=GameState.Vehicle.EXCAVATOR
    for(i in 0..5) { check(g.pipes.tap(g,i));idle();check(g.pipes.dug(g,i)) }
    if(stage>0) {
        g.selected=GameState.Vehicle.CRANE
        check(g.pipes.tap(g,6));idle();check(g.pipes.carried==0)
        if(stage>1) {
            check(g.pipes.tap(g,6));idle()
            for(i in 1..2) { check(g.pipes.tap(g,i+6));idle();check(g.pipes.tap(g,i+6));idle() }
        }
    }
    if(stage>2) {
        g.selected=GameState.Vehicle.DOZER
        for(i in 9..11) { check(g.pipes.tap(g,i));idle() };check(g.pipes.complete)
    }
    // Clear the cut by driving the excavator away, rather than hiding it or editing terrain.
    g.driveTo(GameState.Vehicle.EXCAVATOR,WorldMap.gameX(6f),10.5f);idle()
    repeat(180) { g.update(.016f) }
}

fun previewIdle(g:GameState) { repeat(20000) { if(!g.driving)return;g.update(.016f) };error("activity stuck") }
fun previewBridge(g:GameState) {
    g.truckX=WorldMap.gameX(48f);g.truckZ=4.5f;g.load=8;g.source-=8;g.selected=GameState.Vehicle.TRUCK;g.action();repeat(400){g.update(.016f)};g.action();repeat(200){g.update(.016f)}
    repeat(3){check(g.buildBridge());previewIdle(g);repeat(300){g.update(.016f)}};check(g.bridgeOpen)
}

fun prepareArena(g:GameState,map:Int,kind:com.dromadi.tinysim.TornadoArena.Kind,level:Int) {
    val a=g.wind.arena;a.enter(map);a.choose(kind)
    repeat(30000) { i->
        if(a.board.level>=level)return
        if(i%40==0)a.hintTarget()?.let { a.aim(it.first,it.second) }
        g.update(.016f)
    }
    error("arena growth stuck")
}

/** Uses actual tornado, routes and vehicle jobs; no completed-state fixture shortcuts. */
fun prepareDistrict(g:GameState,stage:Int) {
    g.recovery.enter(g);g.wind.toggle(g)
    for(h in 0..3) { g.wind.aim(com.dromadi.tinysim.RecoverySite.houseX(h),com.dromadi.tinysim.RecoverySite.houseZ(h));repeat(180){g.update(.04f)} }
    g.wind.toggle(g);previewIdle(g);check(g.recovery.state.count { it==2 }==16)
    fun act(v:GameState.Vehicle) { g.selected=v;g.action();previewIdle(g) }
    if(stage>=1)repeat(16){act(GameState.Vehicle.DOZER)}
    if(stage>=2)repeat(2){act(GameState.Vehicle.TRUCK)}
    if(stage>=3)repeat(4){act(GameState.Vehicle.MIXER)}
    if(stage>=4)repeat(16){act(GameState.Vehicle.CRANE);act(GameState.Vehicle.CRANE)}
}

fun prepareWash(g:GameState) {
    repeat(4){g.wash.soil()};g.wash.enter(g);previewIdle(g);g.update(.04f);check(g.wash.ready(g))
}

fun prepareFleetWash(g:GameState,v:GameState.Vehicle) {
    g.wash.soil(v,1f);g.wash.enter(g,v);previewIdle(g);g.update(.04f);check(g.wash.ready(g))
    g.wash.choose(com.dromadi.tinysim.WashBay.Tool.SOAP)
    for(p in g.wash.targets(g)) { g.wash.spray(g,p.first,p.second);repeat(25){g.update(.04f)} };g.wash.stop()
    if(v in listOf(GameState.Vehicle.MIXER,GameState.Vehicle.WRECKER)) {
        g.wash.choose(com.dromadi.tinysim.WashBay.Tool.WATER)
        for(p in g.wash.targets(g).take(if(v==GameState.Vehicle.MIXER)3 else 8)) { g.wash.spray(g,p.first,p.second);repeat(30){g.update(.04f)} }
        if(v==GameState.Vehicle.WRECKER) { g.wash.stop();repeat(90){g.update(.016f)};check(g.wash.clean) }
    }
}

/** Clean vehicles at the same real wash-bay pose make geometry changes directly comparable. */
fun prepareVehicleDetail(g:GameState,v:GameState.Vehicle) {
    g.wash.enter(g,v);previewIdle(g);g.update(.04f)
    check(g.wash.ready(g));check(g.totalMaterial==com.dromadi.tinysim.Terrain.INITIAL_MASS)
}
