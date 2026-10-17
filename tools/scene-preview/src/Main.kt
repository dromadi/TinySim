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
            "orbit-close"->SceneFrame.capture(g,cam,1.2f+i,18f,8f,1.35f)
            "orbit-wide"->SceneFrame.capture(g,cam,1.2f+i,-18f,14f,.8f)
            "map-overview"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,.48f,19f,4f)
            "construction-yard"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,.95f,18f,5f)
            "mobile-dig"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,36f,8f)
            "rotated-excavator"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,18f,7f)
            "road-project","road-complete"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,1.2f,20f,8f)
            "terrain-pit","terrain-mound","house-materials","house-walls","house-complete"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,1.05f,36f,7f)
            "bridge-crane"->SceneFrame.capture(g,cam,1.2f+i,-18f,6f,1.1f,50f,5f)
            "crane-grab"->SceneFrame.capture(g,cam,1.2f+i,-30f,5f,1.3f,44f,7.5f)
            "bridge-open"->SceneFrame.capture(g,cam,1.2f+i,12f,4f,1.05f,53f,5f)
            "vehicle-life"->SceneFrame.capture(g,cam,1.2f+i+36*.016f,-40f,2f,1.8f,-6.2f,.6f)
            "building-site"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1f,36f,6f)
            else->SceneFrame.capture(g,cam,1.2f+i)
        }
        r.submit(frame)
        r.onDrawFrame(null)
        val canvas=Canvas();GameRenderer().draw(canvas,g,true,false,0f,frame.seconds,cam,advanced=false,orbitMode=false,zoom=frame.zoom,panX=frame.panX,panZ=frame.panZ,hintX=if(name=="start-cam1")1010f else Float.NaN,hintY=590f)
        if(i>0)hud.append(',');hud.append('"').append(name).append("\":[").append(canvas.ops).append(']')
        if(i>0)out.append(',');out.append('"').append(name).append("\":[").append(GLES20.ops).append(']');GLES20.ops.setLength(0)
    }
    out.append("},\"hud\":{").append(hud).append("}}")
    File(args[0]).writeText(out.toString())
}
