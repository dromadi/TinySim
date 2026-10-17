import android.opengl.GLES20
import android.graphics.Canvas
import com.dromadi.tinysim.GameRenderer
import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.WorldMap
import com.dromadi.tinysim.scene.*
import java.io.File

fun main(args: Array<String>) {
    val out=StringBuilder("{")
    val r=SceneRenderer3D()
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
        Triple("loaded",0) { it.source=28;it.load=6;it.bucket=2;it.armX=520f;it.armY=330f },
        Triple("dump",0) { it.source=29;it.load=0;it.truckX=760f;it.tipping=true;it.bedAngle=-42f;it.sand[WorldMap.cell(1.2f,0f)]=4;it.sand[WorldMap.cell(2.2f,0f)]=3;it.selected=GameState.Vehicle.TRUCK },
        Triple("dozer",2) { it.source=27;it.sand[WorldMap.cell(3f,2f)]=2;it.sand[WorldMap.cell(4f,2f)]=2;it.sand[WorldMap.cell(5f,2f)]=1;it.sand[WorldMap.cell(6f,2f)]=4;it.dozerX=900f;it.selected=GameState.Vehicle.DOZER }
    )
    val hud=StringBuilder()
    scenes.forEachIndexed { i,(name,cam,setup) ->
        val g=GameState();setup(g);check(g.totalSand==36) { "Invalid inventory in preview $name" }
        val frame=when(name) {
            "orbit-close"->SceneFrame.capture(g,cam,1.2f+i,18f,8f,1.35f)
            "orbit-wide"->SceneFrame.capture(g,cam,1.2f+i,-18f,14f,.8f)
            "map-overview"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,.48f,19f,4f)
            "construction-yard"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,.95f,18f,5f)
            "mobile-dig"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,36f,8f)
            "rotated-excavator"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1.1f,18f,7f)
            "road-project","road-complete"->SceneFrame.capture(g,cam,1.2f+i,0f,8f,1.2f,20f,8f)
            "building-site"->SceneFrame.capture(g,cam,1.2f+i,0f,4f,1f,36f,6f)
            else->SceneFrame.capture(g,cam,1.2f+i)
        }
        r.submit(frame)
        r.onDrawFrame(null)
        val canvas=Canvas();GameRenderer().draw(canvas,g,true,false,0f,frame.seconds,cam,false,frame.zoom,frame.panX,frame.panZ)
        if(i>0)hud.append(',');hud.append('"').append(name).append("\":[").append(canvas.ops).append(']')
        if(i>0)out.append(',');out.append('"').append(name).append("\":[").append(GLES20.ops).append(']');GLES20.ops.setLength(0)
    }
    out.append("},\"hud\":{").append(hud).append("}}")
    File(args[0]).writeText(out.toString())
}
