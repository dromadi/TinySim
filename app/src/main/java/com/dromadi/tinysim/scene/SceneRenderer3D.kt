package com.dromadi.tinysim.scene

import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.dromadi.tinysim.GameState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Real depth-tested 3D meshes, lit in world space. All graphics are generated on-device. */
class SceneRenderer3D: GLSurfaceView.Renderer {
    @Volatile private var frame=SceneFrame.capture(GameState(),0,0f)
    private val cube=Geometry.cube()
    private val cylinder=Geometry.cylinder()
    private val cone=Geometry.cone()
    private val sphere=Geometry.sphere()
    private var program=0
    private var position=0;private var normal=0
    private var uMvp=0;private var uModel=0;private var uNormal=0;private var uColor=0
    private val view=FloatArray(16);private val projection=FloatArray(16);private val vp=FloatArray(16)
    private val mvp=FloatArray(16);private val normals=FloatArray(9)
    private val stack=Array(16){FloatArray(16)};private var depth=0
    private val model get()=stack[depth]
    private var viewportX=0;private var viewportY=0;private var viewportW=1200;private var viewportH=675
    private var transferSeen=0;private var streamStart=-10f;private var streamX=0f;private var streamY=0f
    private var drawCalls=0
    private val sky=rgba(191,236,238)
    private val white=rgba(255,253,237)
    private val yellow=rgba(250,190,63)
    private val orange=rgba(235,139,55)
    private val teal=rgba(40,158,157)
    private val tealDark=rgba(25,120,126)
    private val ink=rgba(39,63,72)
    private val rubber=rgba(48,64,67)
    private val metal=rgba(156,182,184)
    private val glass=rgba(132,218,231)
    private val sandColor=rgba(250,216,139)
    private val soil=rgba(217,183,122)
    private val soilSide=rgba(183,146,93)
    private val grass=rgba(145,192,151)
    private val leaf=rgba(79,162,130)
    private val leafLight=rgba(112,184,136)
    private val shadow=rgba(34,63,62,.16f)
    fun submit(snapshot: SceneFrame) { frame=snapshot }
    override fun onSurfaceCreated(unused:GL10?,config:EGLConfig?) {
        program=link(VERTEX,FRAGMENT)
        position=GL.glGetAttribLocation(program,"aPosition");normal=GL.glGetAttribLocation(program,"aNormal")
        uMvp=GL.glGetUniformLocation(program,"uMvp");uModel=GL.glGetUniformLocation(program,"uModel")
        uNormal=GL.glGetUniformLocation(program,"uNormal");uColor=GL.glGetUniformLocation(program,"uColor")
        for(mesh in arrayOf(cube,cylinder,cone,sphere))mesh.upload()
        GL.glEnable(GL.GL_DEPTH_TEST);GL.glDepthFunc(GL.GL_LEQUAL)
        GL.glEnable(GL.GL_CULL_FACE);GL.glCullFace(GL.GL_BACK)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
        transferSeen=frame.transferSerial;streamStart=-10f
        Log.i("TinySim3D","EGL scene ready: ${GL.glGetString(GL.GL_RENDERER)}, procedural meshes uploaded")
    }
    override fun onSurfaceChanged(unused:GL10?,width:Int,height:Int) {
        val scale=min(width/1200f,height/675f)
        viewportW=(1200*scale).toInt().coerceAtLeast(1);viewportH=(675*scale).toInt().coerceAtLeast(1)
        viewportX=(width-viewportW)/2;viewportY=(height-viewportH)/2
    }
    override fun onDrawFrame(unused:GL10?) {
        val f=frame
        GL.glDisable(GL.GL_SCISSOR_TEST);GL.glClearColor(.12f,.24f,.28f,1f)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        GL.glViewport(viewportX,viewportY,viewportW,viewportH)
        GL.glEnable(GL.GL_SCISSOR_TEST);GL.glScissor(viewportX,viewportY,viewportW,viewportH)
        GL.glClearColor(sky[0],sky[1],sky[2],1f);GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        val camera=WorldCamera.spec(f.camera)
        Matrix.setLookAtM(view,0,camera.eye.x,camera.eye.y,camera.eye.z,camera.target.x,camera.target.y,camera.target.z,0f,1f,0f)
        Matrix.perspectiveM(projection,0,camera.fov,WorldCamera.ASPECT,1f,120f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        GL.glUseProgram(program);GL.glEnableVertexAttribArray(position);GL.glEnableVertexAttribArray(normal)
        depth=0;Matrix.setIdentityM(model,0);drawCalls=0
        environment()
        // Soft ground-contact shadows are simple geometry, not a shadow-map pass.
        GL.glDepthMask(false)
        disk(-6.6f,.1f,0f,3.9f,2.7f,shadow)
        disk(WorldCamera.x(f.truckX),.1f,0f,4.6f,2.7f,shadow)
        disk(WorldCamera.x(f.dozerX),.1f,2.25f,4.2f,2.7f,shadow)
        GL.glDepthMask(true)
        sand(f)
        excavator(f)
        truck(f)
        bulldozer(f)
        if(f.selected==GameState.Vehicle.EXCAVATOR)guide(f)
        particles(f)
        GL.glDisableVertexAttribArray(position);GL.glDisableVertexAttribArray(normal)
        GL.glDisable(GL.GL_SCISSOR_TEST)
        val error=GL.glGetError()
        if(error!=GL.GL_NO_ERROR)Log.e("TinySim3D","OpenGL error: $error")
    }
    private fun environment() {
        box(0f,-.76f,0f,140f,.12f,140f,grass)
        blob(-17f,1f,-22f,16f,7f,10f,leafLight)
        blob(0f,1f,-26f,19f,6f,12f,grass)
        blob(20f,1f,-25f,18f,8f,14f,leafLight)
        // Raised sandbox island with a visible terracotta rim.
        box(0f,-.4f,0f,22.8f,.8f,9.1f,soilSide)
        box(0f,-.03f,0f,22.5f,.14f,8.8f,soil)
        box(3.4f,.05f,0f,15.2f,.025f,3.1f,rgba(226,197,145))
        box(3.4f,.05f,2.6f,15.2f,.025f,1.65f,rgba(232,201,144))
        for(i in 0..17) {
            val x=-10.6f+i*1.25f
            box(x,.075f,-4.22f,1.08f,.07f,.22f,white)
            box(x,.075f,4.22f,1.08f,.07f,.22f,white)
        }
        for(i in 0..9) {
            box(-1.2f+i*1.2f,.083f,1.6f,.55f,.025f,.07f,white)
            box(-1.2f+i*1.2f,.083f,-1.6f,.55f,.025f,.07f,white)
        }
        // Small garden behind the work site keeps foreground controls clear.
        tree(-10f,-5.6f,1.0f);tree(-7f,-6f,1.2f);tree(0f,-6.7f,.95f);tree(6f,-6.1f,1.1f);tree(10.8f,-5.5f,.9f)
        for(i in 0..7) {
            val x=-10.5f+i*3f
            box(x,.55f,-3.85f,.12f,1.05f,.12f,white)
        }
        box(0f,.35f,-3.85f,21f,.09f,.09f,white);box(0f,.8f,-3.85f,21f,.09f,.09f,white)
        for(x in floatArrayOf(-1.9f,9.9f))for(z in floatArrayOf(-2.35f,3.75f))trafficCone(x,z)
        for(i in 0..24) {
            val x=-10.5f+((i*37)%210)/10f
            val z=if(i%2==0)2.8f+(i%3)*.25f else -2.8f-(i%3)*.25f
            blob(x,.12f,z,.15f+(i%3)*.04f,.13f,.2f,soilSide)
        }
        cloud(-8f,7.6f,-11f);cloud(1f,8.2f,-13f)
        blob(9f,8.0f,-13f,1.8f,1.8f,1.8f,rgba(255,221,123))
    }
    private fun tree(x:Float,z:Float,s:Float) {
        push();translate(x,0f,z);scale(s,s,s)
        box(0f,.65f,0f,.23f,1.35f,.23f,soilSide)
        blob(0f,1.95f,0f,1.4f,1.8f,1.35f,leaf)
        blob(-.4f,1.65f,.13f,1.05f,1.1f,1.05f,leafLight)
        pop()
    }
    private fun cloud(x:Float,y:Float,z:Float) {
        blob(x,y,z,2.5f,.7f,1.0f,white);blob(x-.5f,y+.3f,z,1.25f,1.1f,1f,white);blob(x+.55f,y+.2f,z,.95f,.8f,.9f,white)
    }
    private fun trafficCone(x:Float,z:Float) {
        box(x,.09f,z,.6f,.12f,.6f,white)
        shape(cone,x,.15f,z,.48f,.75f,.48f,orange)
        disk(x,.47f,z,.28f,.28f,white,.14f)
    }
    private fun sand(f:SceneFrame) {
        disk(WorldCamera.x(365f),.09f,0f,3.35f,2.8f,soilSide)
        if(f.source>0)shape(cone,WorldCamera.x(365f),.1f,0f,3f,.2f+f.source/36f,2.45f,sandColor)
        for(i in f.sand.indices)if(f.sand[i]>0) {
            shape(cone,WorldCamera.x(495f+i*30),.09f,.7f,.75f,.13f+f.sand[i]*.14f,1.0f,sandColor)
        }
    }
    private fun excavator(f:SceneFrame) {
        push();translate(-6.67f,0f,0f)
        tracks(2.75f)
        box(0f,.94f,0f,2.5f,.55f,1.65f,yellow)
        box(-.6f,1.75f,0f,1.2f,1.2f,1.4f,yellow)
        cabWindows(-.6f,1.88f,1.2f,1.4f)
        box(-.6f,2.42f,0f,1.45f,.16f,1.65f,yellow)
        box(.65f,1.29f,0f,.9f,.23f,1.6f,orange)
        pop()
        val px=WorldCamera.x(243f);val py=WorldCamera.y(412f)
        val jx=WorldCamera.x(310f);val jy=WorldCamera.y(285f)
        val bx=WorldCamera.x(f.armX);val by=WorldCamera.y(f.armY)
        beam(px,py,jx,jy,.38f,.5f,orange)
        beam(jx,jy,bx,by+.3f,.28f,.36f,yellow)
        beam(px-.14f,py+.27f,jx-.12f,jy-.31f,.08f,.14f,ink)
        beam(jx+.08f,jy-.12f,bx-.08f,by+.48f,.055f,.12f,metal)
        wheelDisc(px,py,0f,.23f,.63f,yellow)
        wheelDisc(jx,jy,0f,.21f,.6f,orange)
        wheelDisc(bx,by+.28f,0f,.12f,.54f,ink)
        push();translate(bx,by+.08f,0f);rotate(if(f.bucket>0)10f else -12f,0f,0f,1f)
        box(0f,-.18f,0f,.88f,.12f,.92f,ink)
        box(-.36f,.05f,0f,.13f,.5f,.92f,ink)
        box(0f,.0f,-.43f,.83f,.43f,.09f,ink);box(0f,.0f,.43f,.83f,.43f,.09f,ink)
        for(i in 0..3)box(.48f,-.18f,-.32f+i*.21f,.22f,.09f,.11f,metal)
        if(f.bucket>0)shape(cone,0f,-.1f,0f,.7f,.31f,.75f,sandColor)
        pop()
    }
    private fun truck(f:SceneFrame) {
        push();translate(WorldCamera.x(f.truckX),0f,0f)
        box(0f,.67f,0f,3.85f,.25f,1.55f,ink)
        for(x in floatArrayOf(-1.25f,1.24f))for(z in floatArrayOf(-.91f,.91f))wheel(x,.48f,z,f.truckX*.65f)
        box(1.13f,1.52f,0f,1.26f,1.38f,1.62f,orange)
        cabWindows(1.13f,1.77f,1.26f,1.62f)
        box(1.11f,2.26f,0f,1.45f,.15f,1.8f,orange)
        box(1.85f,.76f,0f,.18f,.22f,1.88f,white)
        for(z in floatArrayOf(-.57f,.57f))box(1.78f,1.15f,z,.07f,.2f,.25f,white)
        // Bed hinge at the rear; positive world-Z rotation lifts the front.
        push();translate(-1.66f,.91f,0f);rotate(-f.bedAngle,0f,0f,1f)
        box(1.05f,.07f,0f,2.7f,.14f,1.88f,teal)
        for(z in floatArrayOf(-.9f,.9f)) {
            box(1.05f,.48f,z,2.7f,.8f,.12f,teal)
            for(i in 0..4)box(-.02f+i*.53f,.48f,z*1.08f,.055f,.63f,.05f,tealDark)
        }
        box(2.35f,.48f,0f,.12f,.8f,1.88f,teal)
        push();translate(-.3f,.12f,0f);rotate(if(f.tipping)-65f else 0f,0f,0f,1f)
        box(0f,.36f,0f,.12f,.72f,1.88f,teal);pop()
        if(f.load>0)shape(cone,1.05f,.15f,0f,2.45f,.15f+f.load*.075f,1.65f,sandColor)
        pop();pop()
    }
    private fun bulldozer(f:SceneFrame) {
        push();translate(WorldCamera.x(f.dozerX),0f,2.25f)
        if(f.dozerFacing<0)rotate(180f,0f,1f,0f)
        tracks(2.7f)
        box(0f,.96f,0f,2.7f,.55f,1.65f,yellow)
        box(-.5f,1.77f,0f,1.2f,1.22f,1.4f,yellow)
        cabWindows(-.5f,1.89f,1.2f,1.4f)
        box(-.5f,2.44f,0f,1.5f,.14f,1.7f,ink)
        box(.86f,1.37f,0f,.63f,.38f,1.4f,orange)
        box(.7f,1.72f,-.49f,.13f,.55f,.13f,ink)
        push();translate(0f,if(f.bladeDown)0f else .55f,0f)
        for(z in floatArrayOf(-.57f,.57f))box(1.21f,.5f,z,1.05f,.14f,.15f,ink)
        box(1.83f,.53f,0f,.16f,.91f,2.15f,metal)
        box(1.95f,.15f,0f,.23f,.1f,2.25f,white)
        for(z in floatArrayOf(-1.02f,1.02f))box(1.79f,.5f,z,.35f,.84f,.1f,metal)
        pop();pop()
    }
    private fun cabWindows(x:Float,y:Float,width:Float,length:Float) {
        for(z in floatArrayOf(-length/2-.025f,length/2+.025f)) {
            box(x,y,z,width*.78f,.72f,.035f,glass)
            box(x-.2f,y+.2f,z*1.01f,width*.36f,.045f,.04f,white)
        }
        box(x+width/2+.025f,y,0f,.035f,.72f,length*.78f,glass)
    }
    private fun tracks(length:Float) {
        for(z in floatArrayOf(-.78f,.78f)) {
            box(0f,.41f,z,length-.55f,.7f,.53f,rubber)
            wheelDisc(-length/2+.34f,.41f,z,.35f,.53f,rubber)
            wheelDisc(length/2-.34f,.41f,z,.35f,.53f,rubber)
            for(i in 0..4)wheelDisc(-length/2+.37f+i*(length-.74f)/4,.4f,z+sign(z)*.3f,.22f,.08f,metal)
            for(i in 0..7)box(-length/2+.2f+i*(length-.4f)/7,.76f,z,.12f,.045f,.58f,ink)
        }
    }
    private fun wheel(x:Float,y:Float,z:Float,angle:Float) {
        wheelDisc(x,y,z,.47f,.32f,rubber)
        val outer=z+sign(z)*.18f
        wheelDisc(x,y,outer,.27f,.05f,metal)
        wheelDisc(x,y,outer+sign(z)*.035f,.1f,.05f,white)
        push();translate(x,y,outer+sign(z)*.04f);rotate(angle,0f,0f,1f)
        box(0f,0f,0f,.36f,.04f,.025f,white);pop()
    }
    private fun guide(f:SceneFrame) {
        val start=WorldCamera.x(365f);val end=WorldCamera.x(f.truckX)
        for(i in 0..12) {
            val t=i/12f
            val x=start+(end-start)*t
            val y=1.2f+(3.4f-1.2f)*t+sin(t*PI.toFloat())*1.35f
            blob(x,y,.05f,.055f,.055f,.055f,white)
        }
        // A bright wrist pin gives children a large, visible dragging target.
        blob(WorldCamera.x(f.armX),WorldCamera.y(f.armY)+.3f,.36f,.15f,.15f,.08f,white)
    }
    private fun particles(f:SceneFrame) {
        if(f.transferSerial!=transferSeen) {
            transferSeen=f.transferSerial;streamStart=f.seconds
            streamX=if(f.tipping)WorldCamera.x(f.truckX)-1.8f else WorldCamera.x(f.armX)
            streamY=if(f.tipping)2f else WorldCamera.y(f.armY)
        }
        val elapsed=f.seconds-streamStart
        if(elapsed !in 0f.. .6f)return
        for(i in 0..13) {
            val phase=(elapsed/.6f+i*.073f)%1f
            val ground=if(!f.tipping && f.bucket==0)1.2f else .1f
            val y=streamY+(ground-streamY)*phase*phase
            val x=streamX+sin(i*7f)*.16f
            blob(x,y,(if(f.tipping).7f else 0f)+cos(i*3f)*.3f,.09f,.09f,.09f,sandColor)
        }
    }
    private fun beam(ax:Float,ay:Float,bx:Float,by:Float,width:Float,thickness:Float,color:FloatArray) {
        val dx=bx-ax;val dy=by-ay
        push();translate((ax+bx)/2,(ay+by)/2,0f);rotate(atan2(dy,dx)*180f/PI.toFloat(),0f,0f,1f)
        box(0f,0f,0f,sqrt(dx*dx+dy*dy),width,thickness,color);pop()
    }
    private fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray)=shape(cube,x,y,z,w,h,d,color)
    private fun blob(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray)=shape(sphere,x,y,z,w,h,d,color)
    private fun disk(x:Float,y:Float,z:Float,w:Float,d:Float,color:FloatArray,h:Float=.015f)=shape(cylinder,x,y,z,w,h,d,color)
    private fun wheelDisc(x:Float,y:Float,z:Float,r:Float,thickness:Float,color:FloatArray) {
        push();translate(x,y,z);rotate(90f,1f,0f,0f);scale(r*2,thickness,r*2);draw(cylinder,color);pop()
    }
    private fun shape(mesh:Geometry,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray) {
        push();translate(x,y,z);scale(w,h,d);draw(mesh,color);pop()
    }
    private fun draw(mesh:Geometry,color:FloatArray) {
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        // Inverse-transpose normal matrix for orthogonal TRS columns, without per-object allocation.
        for(column in 0..2) {
            val k=column*4;val n=column*3
            val squared=(model[k]*model[k]+model[k+1]*model[k+1]+model[k+2]*model[k+2]).coerceAtLeast(1e-12f)
            for(row in 0..2)normals[n+row]=model[k+row]/squared
        }
        GL.glUniformMatrix4fv(uMvp,1,false,mvp,0);GL.glUniformMatrix4fv(uModel,1,false,model,0)
        GL.glUniformMatrix3fv(uNormal,1,false,normals,0);GL.glUniform4fv(uColor,1,color,0)
        mesh.draw(position,normal);drawCalls++
    }
    private fun push() { check(depth<stack.lastIndex);model.copyInto(stack[depth+1]);depth++ }
    private fun pop() { check(depth>0);depth-- }
    private fun translate(x:Float,y:Float,z:Float)=Matrix.translateM(model,0,x,y,z)
    private fun scale(x:Float,y:Float,z:Float)=Matrix.scaleM(model,0,x,y,z)
    private fun rotate(a:Float,x:Float,y:Float,z:Float)=Matrix.rotateM(model,0,a,x,y,z)
    companion object {
        private fun rgba(r:Int,g:Int,b:Int,a:Float=1f)=floatArrayOf(r/255f,g/255f,b/255f,a)
        private fun shader(type:Int,source:String):Int {
            val shader=GL.glCreateShader(type);GL.glShaderSource(shader,source);GL.glCompileShader(shader)
            val ok=IntArray(1);GL.glGetShaderiv(shader,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0){GL.glGetShaderInfoLog(shader)};return shader
        }
        private fun link(vertex:String,fragment:String):Int {
            val v=shader(GL.GL_VERTEX_SHADER,vertex);val f=shader(GL.GL_FRAGMENT_SHADER,fragment)
            val p=GL.glCreateProgram();GL.glAttachShader(p,v);GL.glAttachShader(p,f);GL.glLinkProgram(p)
            val ok=IntArray(1);GL.glGetProgramiv(p,GL.GL_LINK_STATUS,ok,0)
            check(ok[0]!=0){GL.glGetProgramInfoLog(p)};GL.glDeleteShader(v);GL.glDeleteShader(f);return p
        }
        private const val VERTEX="""
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat3 uNormal;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                vNormal=normalize(uNormal*aNormal);
                vHeight=(uModel*vec4(aPosition,1.0)).y;
                gl_Position=uMvp*vec4(aPosition,1.0);
            }
        """
        private const val FRAGMENT="""
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                float diffuse=max(dot(normalize(vNormal),normalize(vec3(-0.45,0.85,0.6))),0.0);
                float light=0.62+0.38*diffuse;
                vec3 color=uColor.rgb*light;
                gl_FragColor=vec4(color,uColor.a);
            }
        """
    }
}
