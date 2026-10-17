import android.opengl.GLES20
import com.dromadi.tinysim.GameState
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
        Triple("loaded",0) { it.source=28;it.load=6;it.bucket=2;it.armX=520f;it.armY=330f },
        Triple("dump",0) { it.source=28;it.load=0;it.truckX=760f;it.tipping=true;it.bedAngle=42f;it.sand[8]=4;it.sand[9]=3;it.selected=GameState.Vehicle.TRUCK },
        Triple("dozer",2) { it.source=20;it.sand[10]=2;it.sand[11]=2;it.sand[12]=1;it.sand[16]=4;it.dozerX=900f;it.selected=GameState.Vehicle.DOZER }
    )
    scenes.forEachIndexed { i,(name,cam,setup) ->
        val g=GameState();setup(g)
        r.submit(SceneFrame.capture(g,cam,1.2f+i))
        r.onDrawFrame(null)
        if(i>0)out.append(',');out.append('"').append(name).append("\":[").append(GLES20.ops).append(']');GLES20.ops.setLength(0)
    }
    out.append("}}")
    File(args[0]).writeText(out.toString())
}
