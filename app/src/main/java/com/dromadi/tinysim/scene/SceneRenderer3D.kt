package com.dromadi.tinysim.scene

import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.ArmRig
import com.dromadi.tinysim.WorldMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * Real depth-tested 3D meshes with a sun shadow map, linear-space lighting, procedural surface
 * detail, sky, fog and filmic tone mapping. All graphics are generated on-device.
 */
class SceneRenderer3D: GLSurfaceView.Renderer {
    @Volatile private var frame=SceneFrame.capture(GameState(),0,0f)
    private val cube=Geometry.cube()
    private val panelMesh=Geometry.bevelCube()
    private val cylinder=Geometry.cylinder()
    private val cone=Geometry.cone()
    private val sphere=Geometry.sphere()
    private val moundMesh=Geometry.mound()
    private val plane=Geometry.plane()
    private var program=0;private var depthProgram=0
    private var position=0;private var normal=0;private var depthPosition=0
    private var uMvp=0;private var uModel=0;private var uNormal=0;private var uColor=0
    private var uMaterial=0;private var uKind=0;private var uEye=0;private var uSun=0
    private var uLightVp=0;private var uShadowMap=0;private var uShadowOn=0;private var uShadowTexel=0;private var uViewport=0
    private var uDepthMvp=0
    private val view=FloatArray(16);private val projection=FloatArray(16);private val vp=FloatArray(16)
    private val lightView=FloatArray(16);private val lightProjection=FloatArray(16);private val lightVp=FloatArray(16)
    private val mvp=FloatArray(16);private val normals=FloatArray(9)
    private val stack=Array(20){FloatArray(16)};private var depth=0
    private val model get()=stack[depth]
    private var viewportX=0;private var viewportY=0;private var viewportW=1200;private var viewportH=675
    private var transferSeen=0;private var streamStart=-10f;private var streamX=0f;private var streamY=0f
    private var streamZ=0f;private var streamGround=0f
    private var lastDozerZ=Float.NaN
    private var lastDozerX=Float.NaN;private var dustUntil=-10f;private var dustX=0f
    private var drawCalls=0
    // Shadow map: depth is packed into RGBA8 because depth textures are optional on OpenGL ES 2.0.
    private var highPrecision=false
    private var shadowsEnabled=false;private var shadowSize=2048
    private var shadowFbo=0;private var shadowTexture=0;private var shadowDepth=0
    private var shadowPass=false;private var casting=true
    private val sun=Vec3(-.56f,.74f,.37f).normalized()

    // Base colours are authored in sRGB; the shader lights them in linear space.
    private val white=rgba(244,242,232)
    private val paintWhite=rgba(236,236,228)
    private val yellow=rgba(244,180,32)
    private val yellowDark=rgba(206,146,22)
    private val orange=rgba(226,112,38)
    private val teal=rgba(38,140,142)
    private val tealDark=rgba(24,104,110)
    private val ink=rgba(46,52,56)
    private val darkSteel=rgba(70,76,80)
    private val rubber=rgba(34,35,36)
    private val metal=rgba(168,172,174)
    private val chrome=rgba(214,218,220)
    private val glass=rgba(40,62,72)
    private val sandColor=rgba(226,190,130)
    private val sandWet=rgba(196,156,102)
    private val soil=rgba(178,142,96)
    private val soilSide=rgba(132,98,66)
    private val grass=rgba(72,98,50)
    private val hill=rgba(80,106,62)
    private val mountain=rgba(120,140,150)
    private val leaf=rgba(46,92,48)
    private val leafLight=rgba(78,122,56)
    private val pine=rgba(38,74,52)
    private val bark=rgba(96,70,50)
    private val stone=rgba(140,132,122)
    private val lamp=rgba(255,236,190)
    private val beacon=rgba(255,140,30)
    private val cloudColor=rgba(255,255,255)
    private val contact=rgba(18,22,16,.11f)
    private val dust=rgba(214,186,140,.16f)

    // Material: specular strength, shininess, detail frequency, detail amount, kind.
    private val PAINT=floatArrayOf(.55f,60f,5f,.05f,0f)
    private val MATTE=floatArrayOf(.08f,10f,3f,.08f,0f)
    private val METAL=floatArrayOf(.9f,90f,9f,.05f,6f)
    private val GLASS=floatArrayOf(1.2f,160f,0f,0f,5f)
    private val RUBBER=floatArrayOf(.12f,14f,16f,.12f,0f)
    private val GRASS=floatArrayOf(.04f,8f,1.1f,.32f,4f)
    private val SAND=floatArrayOf(.07f,12f,26f,.14f,7f)
    private val SOIL=floatArrayOf(.05f,8f,6f,.2f,7f)
    private val FOLIAGE=floatArrayOf(.12f,12f,2.6f,.28f,0f)
    private val WOOD=floatArrayOf(.06f,10f,7f,.18f,0f)
    private val CLOUD=floatArrayOf(0f,1f,0f,0f,8f)
    private val EMISSIVE=floatArrayOf(0f,1f,0f,0f,1f)
    private val SKY=floatArrayOf(0f,1f,0f,0f,2f)
    private val DECAL=floatArrayOf(0f,1f,0f,0f,3f)

    fun submit(snapshot: SceneFrame) { frame=snapshot }
    override fun onSurfaceCreated(unused:GL10?,config:EGLConfig?) {
        val precision=IntArray(1)
        GL.glGetShaderPrecisionFormat(GL.GL_FRAGMENT_SHADER,GL.GL_HIGH_FLOAT,IntArray(2),0,precision,0)
        highPrecision=precision[0]>=16
        fun compatible(source:String)=if(highPrecision)source else source.replace("#ifdef GL_FRAGMENT_PRECISION_HIGH","#if 0")
        program=link(compatible(VERTEX),compatible(FRAGMENT))
        position=GL.glGetAttribLocation(program,"aPosition");normal=GL.glGetAttribLocation(program,"aNormal")
        uMvp=GL.glGetUniformLocation(program,"uMvp");uModel=GL.glGetUniformLocation(program,"uModel")
        uNormal=GL.glGetUniformLocation(program,"uNormal");uColor=GL.glGetUniformLocation(program,"uColor")
        uMaterial=GL.glGetUniformLocation(program,"uMaterial");uKind=GL.glGetUniformLocation(program,"uKind")
        uEye=GL.glGetUniformLocation(program,"uEye");uSun=GL.glGetUniformLocation(program,"uSun")
        uLightVp=GL.glGetUniformLocation(program,"uLightVp");uShadowMap=GL.glGetUniformLocation(program,"uShadowMap")
        uShadowOn=GL.glGetUniformLocation(program,"uShadowOn");uShadowTexel=GL.glGetUniformLocation(program,"uShadowTexel")
        uViewport=GL.glGetUniformLocation(program,"uViewport")
        for(mesh in arrayOf(cube,panelMesh,cylinder,cone,sphere,moundMesh,plane))mesh.upload()
        GL.glEnable(GL.GL_DEPTH_TEST);GL.glDepthFunc(GL.GL_LEQUAL)
        GL.glEnable(GL.GL_CULL_FACE);GL.glCullFace(GL.GL_BACK)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
        createShadowMap()
        // The sun is fixed, so its orthographic frustum is fitted once around the work site.
        val c=Vec3(19f,0f,3f)
        Matrix.setLookAtM(lightView,0,c.x+sun.x*75f,c.y+sun.y*75f,c.z+sun.z*75f,c.x,c.y,c.z,0f,1f,0f)
        Matrix.orthoM(lightProjection,0,-42f,42f,-28f,28f,5f,170f)
        Matrix.multiplyMM(lightVp,0,lightProjection,0,lightView,0)
        transferSeen=frame.transferSerial;streamStart=-10f;lastDozerX=Float.NaN
        Log.i("TinySim3D","EGL scene ready: ${GL.glGetString(GL.GL_RENDERER)}, shadows=$shadowsEnabled ${shadowSize}px, procedural meshes uploaded")
    }
    private fun createShadowMap() {
        shadowsEnabled=false
        if(!highPrecision) { Log.w("TinySim3D","No highp fragment float; shadow map disabled");return }
        try { depthProgram=link(DEPTH_VERTEX,DEPTH_FRAGMENT) }
        catch(e:IllegalStateException) { Log.w("TinySim3D","Shadow shader unavailable; using unshadowed lighting: ${e.message}");return }
        depthPosition=GL.glGetAttribLocation(depthProgram,"aPosition");uDepthMvp=GL.glGetUniformLocation(depthProgram,"uMvp")
        val max=IntArray(1);GL.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE,max,0)
        shadowSize=if(max[0]>=4096)4096 else 1024
        val ids=IntArray(1)
        GL.glGenTextures(1,ids,0);shadowTexture=ids[0]
        GL.glBindTexture(GL.GL_TEXTURE_2D,shadowTexture)
        GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA,shadowSize,shadowSize,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
        GL.glGenRenderbuffers(1,ids,0);shadowDepth=ids[0]
        GL.glBindRenderbuffer(GL.GL_RENDERBUFFER,shadowDepth)
        GL.glRenderbufferStorage(GL.GL_RENDERBUFFER,GL.GL_DEPTH_COMPONENT16,shadowSize,shadowSize)
        GL.glGenFramebuffers(1,ids,0);shadowFbo=ids[0]
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,shadowFbo)
        GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,shadowTexture,0)
        GL.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER,GL.GL_DEPTH_ATTACHMENT,GL.GL_RENDERBUFFER,shadowDepth)
        shadowsEnabled=GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE
        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0)
        if(!shadowsEnabled)Log.w("TinySim3D","Shadow framebuffer incomplete; shadows disabled")
    }
    override fun onSurfaceChanged(unused:GL10?,width:Int,height:Int) {
        val scale=min(width/1200f,height/675f)
        viewportW=(1200*scale).toInt().coerceAtLeast(1);viewportH=(675*scale).toInt().coerceAtLeast(1)
        viewportX=(width-viewportW)/2;viewportY=(height-viewportH)/2
    }
    override fun onDrawFrame(unused:GL10?) {
        val f=frame
        updateEffects(f)
        drawCalls=0
        if(shadowsEnabled) {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,shadowFbo)
            GL.glDisable(GL.GL_SCISSOR_TEST);GL.glViewport(0,0,shadowSize,shadowSize)
            GL.glClearColor(1f,1f,1f,1f);GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
            GL.glDisable(GL.GL_BLEND);GL.glCullFace(GL.GL_FRONT)
            GL.glUseProgram(depthProgram);GL.glEnableVertexAttribArray(depthPosition)
            shadowPass=true;depth=0;Matrix.setIdentityM(model,0)
            scene(f)
            shadowPass=false
            GL.glDisableVertexAttribArray(depthPosition)
            GL.glCullFace(GL.GL_BACK);GL.glEnable(GL.GL_BLEND)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0)
        }
        GL.glDisable(GL.GL_SCISSOR_TEST);GL.glClearColor(.1f,.14f,.16f,1f)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        GL.glViewport(viewportX,viewportY,viewportW,viewportH)
        GL.glEnable(GL.GL_SCISSOR_TEST);GL.glScissor(viewportX,viewportY,viewportW,viewportH)
        val camera=WorldCamera.spec(f.camera,f.yaw,f.pitch,f.zoom,f.panX,f.panZ)
        Matrix.setLookAtM(view,0,camera.eye.x,camera.eye.y,camera.eye.z,camera.target.x,camera.target.y,camera.target.z,0f,1f,0f)
        Matrix.perspectiveM(projection,0,camera.fov,WorldCamera.ASPECT,.8f,260f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        GL.glUseProgram(program);GL.glEnableVertexAttribArray(position);GL.glEnableVertexAttribArray(normal)
        GL.glUniform3f(uEye,camera.eye.x,camera.eye.y,camera.eye.z);GL.glUniform3f(uSun,sun.x,sun.y,sun.z)
        GL.glUniformMatrix4fv(uLightVp,1,false,lightVp,0)
        GL.glUniform1f(uShadowOn,if(shadowsEnabled)1f else 0f);GL.glUniform2f(uShadowTexel,1f/shadowSize,1f/shadowSize)
        GL.glUniform4f(uViewport,viewportX.toFloat(),viewportY.toFloat(),viewportW.toFloat(),viewportH.toFloat())
        GL.glActiveTexture(GL.GL_TEXTURE0);GL.glBindTexture(GL.GL_TEXTURE_2D,if(shadowsEnabled)shadowTexture else 0);GL.glUniform1i(uShadowMap,0)
        depth=0;Matrix.setIdentityM(model,0)
        skyDome(camera.eye)
        scene(f)
        GL.glDisableVertexAttribArray(position);GL.glDisableVertexAttribArray(normal)
        GL.glDisable(GL.GL_SCISSOR_TEST)
        val error=GL.glGetError()
        if(error!=GL.GL_NO_ERROR)Log.e("TinySim3D","OpenGL error: $error")
    }
    /** Shared by the shadow and colour passes so every caster also appears on screen. */
    private fun scene(f:SceneFrame) {
        environment()
        destination(f)
        contactShadows(f)
        sand(f)
        excavator(f)
        truck(f)
        bulldozer(f)
        if(f.selected==GameState.Vehicle.EXCAVATOR)guide(f)
        particles(f)
        dustCloud(f)
    }
    private fun skyDome(eye:Vec3) {
        GL.glDisable(GL.GL_CULL_FACE);GL.glDepthMask(false)
        push();translate(eye.x,eye.y,eye.z);scale(400f,400f,400f);draw(sphere,cloudColor,SKY);pop()
        GL.glDepthMask(true);GL.glEnable(GL.GL_CULL_FACE)
    }
    private fun updateEffects(f:SceneFrame) {
        if(!lastDozerX.isNaN() && hypot((f.dozerX-lastDozerX)/60f,f.dozerZ-lastDozerZ)>.008f && f.bladeDown) { dustUntil=f.seconds+.7f;dustX=f.dozerX }
        lastDozerX=f.dozerX;lastDozerZ=f.dozerZ
    }
    private inline fun noCast(block:()->Unit) { val was=casting;casting=false;block();casting=was }
    private fun environment() {
        noCast {
            push();translate(0f,GRASS_Y,0f);scale(220f,1f,220f);draw(plane,grass,GRASS);pop()
            // Rolling hills and a hazy ridge give the diorama depth; fog blends them into the sky.
            blob(-24f,GRASS_Y,-30f,30f,9f,16f,hill,GRASS)
            blob(2f,GRASS_Y,-36f,36f,8f,14f,hill,GRASS)
            blob(28f,GRASS_Y,-31f,30f,10f,16f,hill,GRASS)
            blob(-44f,GRASS_Y,-70f,60f,30f,30f,mountain,MATTE)
            blob(10f,GRASS_Y,-80f,70f,38f,30f,mountain,MATTE)
            blob(56f,GRASS_Y,-72f,56f,26f,30f,mountain,MATTE)
            cloud(-14f,15f,-46f,1.4f);cloud(6f,17f,-58f,1.8f);cloud(26f,14f,-44f,1.2f)
        }
        // Raised sandbox island with an earthen rim.
        noCast { box(19f,-.4f,4f,62f,.8f,22f,soilSide,SOIL) }
        box(19f,-.03f,4f,61.8f,.14f,21.8f,soil,SOIL)
        noCast {
            box(21f,.05f,0f,55f,.025f,3.8f,rgba(196,166,118),SOIL)
            box(21f,.05f,8f,55f,.025f,3.8f,rgba(204,172,120),SOIL)
            for(i in 0..37) {
                box(-1.2f+i*1.2f,.07f,1.6f,.55f,.012f,.07f,paintWhite,MATTE)
                box(-1.2f+i*1.2f,.07f,-1.6f,.55f,.012f,.07f,paintWhite,MATTE)
            }
        }
        // Concrete kerb around the site; too low to cast a visible shadow.
        noCast {
            for(i in 0..48) {
                val x=-10.6f+i*1.25f
                box(x,.075f,-6.65f,1.08f,.09f,.22f,stone,MATTE)
                box(x,.075f,14.65f,1.08f,.09f,.22f,stone,MATTE)
            }
        }
        expandedSite()
        // Garden behind the work site keeps foreground controls clear.
        tree(-10f,-5.6f,1.0f);tree(-7f,-6f,1.2f);tree(0f,-6.7f,.95f);tree(6f,-6.1f,1.1f);tree(10.8f,-5.5f,.9f)
        // Background tree line sits outside the shadow frustum, so it skips the depth pass.
        noCast {
            for(i in 0..15) {
                val x=-30f+i*4.1f+((i*53)%17)/10f
                tree(x,-12f-(i%4)*2.3f,1.2f+(i%3)*.35f)
            }
        }
        for(z in floatArrayOf(-5f,-8.5f)) { tree(-15.5f,z,1.1f);tree(15.8f,z-1f,1.25f) }
        // Wooden site fence.
        for(i in 0..19) {
            val x=-10.5f+i*3f
            box(x,.55f,-6.5f,.14f,1.05f,.14f,bark,WOOD)
        }
        box(19f,.35f,-6.5f,59f,.09f,.06f,paintWhite,MATTE);box(19f,.8f,-6.5f,59f,.09f,.06f,orange,PAINT)
        for(x in floatArrayOf(-1.9f,9.9f))for(z in floatArrayOf(-2.35f,3.75f))trafficCone(x,z)
        noCast {
            for(i in 0..24) {
                val x=-10.5f+((i*37)%210)/10f
                val z=if(i%2==0)2.8f+(i%3)*.25f else -2.8f-(i%3)*.25f
                blob(x,.08f,z,.16f+(i%3)*.05f,.12f,.2f,if(i%3==0)stone else soilSide,MATTE)
            }
        }
    }
    private fun expandedSite() {
        // Continuous roads and low work pads remain driveable and accept real sand deposits.
        noCast {
            for(x in floatArrayOf(18f,36f)) {
                box(x,.065f,5f,3.8f,.025f,13f,rgba(204,172,120),SOIL)
                box(x,.078f,7f,11f,.025f,10f,if(x==18f)rgba(194,185,151) else rgba(210,187,146),SOIL)
                for(i in 0..7) {
                    box(x-4.5f+i*1.3f,.1f,12.3f,.6f,.04f,.12f,paintWhite,MATTE)
                    box(x-4.5f+i*1.3f,.1f,1.9f,.6f,.04f,.12f,paintWhite,MATTE)
                }
            }
            // Loading bay under the excavator's pouring point.
            for(z in floatArrayOf(-1.45f,1.45f))box(.35f,.095f,z,4.1f,.02f,.1f,teal,PAINT)
        }
        // Workshop is outside the driveable band, leaving the playing surface open.
        panel(21f,1f,-5.6f,5f,2f,2.1f,teal)
        panel(21f,2.08f,-5.6f,5.4f,.18f,2.45f,tealDark)
        box(20f,.85f,-4.53f,1.5f,1.7f,.05f,ink,MATTE)
        box(22.2f,1.15f,-4.51f,1.4f,.8f,.06f,glass,GLASS)
        for(i in 0..4)box(19f+i,2.22f,-5.6f,.06f,.14f,2.35f,metal,METAL)
        // Crane lattice and its fixed hanging hook: scenery, never an input obstacle.
        push();translate(13f,0f,-5.65f)
        panel(0f,.17f,0f,1.6f,.34f,1.6f,metal)
        for(x in floatArrayOf(-.35f,.35f))for(z in floatArrayOf(-.35f,.35f))box(x,3f,z,.13f,6f,.13f,yellow,PAINT)
        for(i in 0..8) {
            box(0f,.5f+i*.65f,.35f,.83f,.08f,.08f,yellowDark,PAINT)
            push();translate(0f,.8f+i*.65f,.35f);rotate(if(i%2==0)42f else -42f,0f,0f,1f)
            box(0f,0f,0f,1f,.07f,.08f,yellow,PAINT);pop()
        }
        box(2.2f,6f,0f,7f,.13f,.7f,yellow,PAINT)
        box(2.2f,6.6f,0f,7f,.11f,.7f,yellow,PAINT)
        for(i in 0..10) {
            push();translate(-.8f+i*.6f,6.3f,.35f);rotate(if(i%2==0)48f else -48f,0f,0f,1f)
            box(0f,0f,0f,.8f,.07f,.07f,yellowDark,PAINT);pop()
        }
        panel(-.7f,5.7f,0f,.7f,.6f,1f,metal)
        panel(.3f,5.55f,.1f,.75f,.8f,.8f,glass)
        box(4.8f,4.8f,0f,.035f,2.4f,.035f,ink,METAL)
        shape(cylinder,4.8f,3.6f,0f,.2f,.3f,.2f,yellow,PAINT)
        pop()
        // A low foundation and garden borders mark the third work area.
        for(z in floatArrayOf(3f,10f))for(i in 0..9)panel(31.5f+i,.16f,z,.85f,.22f,.32f,paintWhite)
        for(i in 0..5)for(k in 0..1)panel(28f+i*.45f,.25f+k*.24f,-5.5f,.4f,.21f,.6f,orange)
        for(i in 0..2) { push();translate(35f+i*.65f,.35f,-5.5f);rotate(90f,0f,0f,1f);shape(cylinder,0f,0f,0f,.6f,2.2f,.6f,metal,METAL);pop() }
        for(x in floatArrayOf(25f,31f,38f,45f,49f)) { tree(x,-6f,1f);tree(x,17f,1.4f) }
        for(x in floatArrayOf(11f,25f,30f,44f))for(z in floatArrayOf(2.5f,11.5f))trafficCone(x,z)
    }
    private fun destination(f:SceneFrame) {
        if(shadowPass)return
        noCast {
            GL.glDepthMask(false)
            val sx=when(f.selected) { GameState.Vehicle.EXCAVATOR->WorldMap.worldX(f.excavatorX);GameState.Vehicle.TRUCK->WorldMap.worldX(f.truckX);GameState.Vehicle.DOZER->WorldMap.worldX(f.dozerX) }
            val sz=when(f.selected) { GameState.Vehicle.EXCAVATOR->f.excavatorZ;GameState.Vehicle.TRUCK->f.truckZ;GameState.Vehicle.DOZER->f.dozerZ }
            disk(sx,.092f,sz,4.5f,3f,rgba(53,196,180,.16f),DECAL)
            val goal=when(f.selected) { GameState.Vehicle.TRUCK->f.truckGoal;GameState.Vehicle.DOZER->f.dozerGoal;else->f.excavatorGoal }
            if(goal!=null) {
                val gx=WorldMap.worldX(goal.x)
                disk(gx,.105f,goal.z,1.1f,1.1f,rgba(24,178,163,.85f),DECAL)
                disk(gx,.12f,goal.z,.28f,.28f,paintWhite,DECAL)
                for(i in 1..14) {
                    val t=i/15f
                    disk(sx+(gx-sx)*t,.11f,sz+(goal.z-sz)*t,.1f,.1f,rgba(255,248,220,.7f),DECAL)
                }
            }
            GL.glDepthMask(true)
        }
    }
    /** Trees vary by position: broadleaf crowns built from irregular clusters, or layered conifers. */
    private fun tree(x:Float,z:Float,s:Float) {
        val ground=if(x in WorldMap.MIN_X..WorldMap.MAX_X && z in WorldMap.MIN_Z..WorldMap.MAX_Z)0f else GRASS_Y
        val seed=abs((x*12.9898f+z*78.233f)*43.7585f)
        fun rnd(k:Int)=((seed*(k+1)*.6180339f)%1f)
        push();translate(x,ground,z);scale(s,s*(.9f+rnd(1)*.35f),s);rotate(rnd(2)*360f,0f,1f,0f)
        noCast { disk(0f,.01f,0f,2.0f,2.0f,contact,DECAL) }
        if(rnd(3)<.3f) {
            shape(cylinder,0f,.5f,0f,.2f,1.0f,.2f,bark,WOOD)
            for(k in 0..3) {
                val w=1.9f-k*.4f
                shape(cone,0f,.7f+k*.6f,0f,w,1.1f,w,if(k%2==0)pine else leaf,FOLIAGE)
            }
        } else {
            shape(cylinder,0f,.8f,0f,.2f,1.6f,.2f,bark,WOOD)
            rod(0f,1.3f,.45f,1.95f,.07f,bark,WOOD)
            rod(0f,1.4f,-.4f,2.05f,.06f,bark,WOOD)
            blob(0f,2.15f,0f,1.45f,1.25f,1.4f,leaf,FOLIAGE)
            for(k in 0..4) {
                val a=(k*72f+rnd(k+4)*40f)*PI.toFloat()/180f;val r=.45f+rnd(k+9)*.25f
                val size=.8f+rnd(k+14)*.4f
                blob(cos(a)*r,1.9f+rnd(k+19)*.75f,sin(a)*r,size,size*.85f,size,if(k%2==0)leafLight else leaf,FOLIAGE)
            }
            blob(0f,2.75f,0f,.95f,.8f,.95f,leafLight,FOLIAGE)
        }
        pop()
    }
    private fun cloud(x:Float,y:Float,z:Float,s:Float) {
        blob(x,y,z,5f*s,1.4f*s,2f*s,cloudColor,CLOUD);blob(x-1f*s,y+.6f*s,z,2.5f*s,2.2f*s,2f*s,cloudColor,CLOUD)
        blob(x+1.1f*s,y+.4f*s,z,1.9f*s,1.6f*s,1.8f*s,cloudColor,CLOUD)
    }
    private fun trafficCone(x:Float,z:Float) {
        box(x,.09f,z,.6f,.1f,.6f,orange,PAINT)
        shape(cone,x,.14f,z,.46f,.78f,.46f,orange,PAINT)
        // Reflective collar.
        shape(cylinder,x,.42f,z,.3f,.1f,.3f,paintWhite,PAINT)
    }
    private fun contactShadows(f:SceneFrame) {
        // Ambient occlusion pools where vehicles meet the ground; the sun shadow comes from the map.
        noCast {
            GL.glDepthMask(false)
            for(k in 0..2) {
                val g=1f-k*.18f
                disk(-6.6f,.075f,0f,3.6f*g,2.3f*g,contact,DECAL)
                disk(WorldCamera.x(f.truckX),.075f,f.truckZ,4.3f*g,2.2f*g,contact,DECAL)
                disk(WorldCamera.x(f.dozerX),.075f,f.dozerZ,4.0f*g,2.4f*g,contact,DECAL)
            }
            GL.glDepthMask(true)
        }
    }
    private fun sand(f:SceneFrame) {
        // Four pictured work bays become a raised gravel road as the blade compacts sand.
        for(section in 0..3) {
            val x=17f+section*2f
            if(f.road[section]>0) {
                box(x,.15f,8f,1.96f,.16f,1.95f,sandWet,SAND)
                for(k in 0..4)box(x-.8f+k*.4f,.24f,8f,.17f,.02f,.09f,paintWhite,MATTE)
            } else noCast {
                box(x,.085f,7.02f,1.9f,.025f,.06f,orange,DECAL)
                box(x,.085f,8.98f,1.9f,.025f,.06f,orange,DECAL)
                box(x-.97f,.085f,8f,.05f,.025f,2f,orange,DECAL)
                box(x+.97f,.085f,8f,.05f,.025f,2f,orange,DECAL)
            }
        }
        for(i in f.dug.indices)if(f.dug[i]>0 && f.sand[i]==0)noCast {
            disk(WorldMap.cellX(i),.081f,WorldMap.cellZ(i),.85f,.8f,sandWet,SAND)
            for(k in 0..2)box(WorldMap.cellX(i)-.25f+k*.24f,.088f,WorldMap.cellZ(i),.06f,.015f,.48f,sandColor,SAND)
        }
        noCast { disk(WorldCamera.x(365f),.08f,0f,3.4f,2.85f,sandWet,SAND) }
        if(f.source>0)mound(WorldCamera.x(365f),.07f,0f,3.1f,.25f+f.source/30f,2.6f,sandColor)
        for(i in f.sand.indices)if(f.sand[i]>0) {
            mound(WorldMap.cellX(i),.07f,WorldMap.cellZ(i),1.2f,.1f+f.sand[i]*.13f,1.2f,sandColor)
        }
    }
    private fun excavator(f:SceneFrame) {
        push();translate(WorldMap.worldX(f.excavatorX),0f,f.excavatorZ);rotate(f.excavatorHeading,0f,1f,0f);translate(-WorldMap.worldX(200f),0f,0f)
        push();translate(WorldMap.worldX(200f),0f,0f)
        tracks(2.75f,f.excavatorTravel)
        // Slewing ring and upper house.
        shape(cylinder,0f,.82f,0f,1.4f,.14f,1.4f,darkSteel,METAL)
        panel(0f,1.06f,0f,2.5f,.5f,1.65f,yellow)
        panel(-.6f,1.86f,0f,1.2f,1.12f,1.4f,yellow)
        cabWindows(-.6f,1.92f,1.2f,1.4f)
        panel(-.6f,2.46f,0f,1.32f,.1f,1.52f,yellowDark)
        // Counterweight, engine grille, exhaust and beacon.
        panel(.86f,1.38f,0f,.78f,.5f,1.62f,yellowDark)
        for(i in 0..3)box(.5f+i*.16f,1.33f,.82f,.07f,.32f,.02f,ink,MATTE)
        shape(cylinder,.5f,1.65f,-.5f,.1f,.5f,.1f,darkSteel,METAL)
        beaconLight(-.6f,2.58f,0f,f.seconds)
        box(-1.24f,1.5f,.73f,.04f,.7f,.04f,ink,METAL)
        pop()
        val px=WorldCamera.x(243f);val py=WorldCamera.y(412f)
        val joint=ArmRig.elbow(f.armX,f.armY)
        val jx=WorldCamera.x(joint.first);val jy=WorldCamera.y(joint.second)
        val bx=WorldCamera.x(f.armX);val by=WorldCamera.y(f.armY)
        beam(px,py,jx,jy,.38f,.5f,yellow,PAINT)
        beam(jx,jy,bx,by,.28f,.36f,yellow,PAINT)
        // Hydraulic cylinders: painted barrel with a polished rod.
        hydraulic(px-.14f,py+.27f,jx-.12f,jy-.31f,.11f)
        hydraulic(jx+.08f,jy-.12f,bx-.08f,by+.48f,.08f)
        wheelDisc(px,py,0f,.23f,.63f,darkSteel,METAL)
        wheelDisc(jx,jy,0f,.21f,.6f,darkSteel,METAL)
        wheelDisc(bx,by,0f,.12f,.54f,darkSteel,METAL)
        push();translate(bx,by-.22f,0f);rotate(if(f.bucket>0)10f else -12f,0f,0f,1f)
        box(0f,-.18f,0f,.88f,.1f,.92f,darkSteel,METAL)
        box(-.36f,.05f,0f,.11f,.5f,.92f,darkSteel,METAL)
        box(0f,.0f,-.43f,.83f,.43f,.07f,darkSteel,METAL);box(0f,.0f,.43f,.83f,.43f,.07f,darkSteel,METAL)
        for(i in 0..3)shape(cone,.46f,-.18f,-.32f+i*.21f,.1f,.24f,.1f,metal,METAL)
        if(f.bucket>0)mound(0f,-.13f,0f,.74f,.32f,.78f,sandColor)
        pop();pop()
    }
    private fun hydraulic(ax:Float,ay:Float,bx:Float,by:Float,radius:Float) {
        val mx=ax+(bx-ax)*.55f;val my=ay+(by-ay)*.55f
        rod(ax,ay,mx,my,radius,yellowDark,PAINT)
        rod(mx,my,bx,by,radius*.55f,chrome,METAL)
    }
    private fun truck(f:SceneFrame) {
        val tx=WorldCamera.x(f.truckX)
        push();translate(tx,0f,f.truckZ);rotate(f.truckHeading,0f,1f,0f)
        box(0f,.67f,0f,3.85f,.25f,1.25f,darkSteel,METAL)
        val spin=-f.truckTravel/.47f*180f/PI.toFloat()
        for(x in floatArrayOf(-1.25f,1.24f))for(z in floatArrayOf(-.91f,.91f))wheel(x,.48f,z,spin)
        // Mudguards and fuel tank.
        for(x in floatArrayOf(-1.25f,1.24f))for(z in floatArrayOf(-.92f,.92f))panel(x,1.0f,z,1.08f,.07f,.36f,ink)
        push();translate(.15f,.62f,-.66f);rotate(90f,0f,0f,1f);shape(cylinder,0f,0f,0f,.34f,.7f,.34f,chrome,METAL);pop()
        panel(1.13f,1.52f,0f,1.26f,1.38f,1.62f,orange)
        cabWindows(1.13f,1.77f,1.26f,1.62f)
        panel(1.11f,2.25f,0f,1.36f,.12f,1.7f,orange)
        // Grille, bumper, headlights and mirrors.
        panel(1.79f,1.2f,0f,.05f,.5f,1.0f,ink)
        for(i in 0..3)box(1.82f,1.02f+i*.12f,0f,.02f,.04f,.9f,chrome,METAL)
        panel(1.86f,.76f,0f,.2f,.22f,1.86f,chrome)
        for(z in floatArrayOf(-.62f,.62f))shape(cylinder,1.81f,1.2f,z,.1f,.07f,.22f,lamp,EMISSIVE)
        for(z in floatArrayOf(-.95f,.95f)) {
            box(1.65f,1.85f,z,.04f,.04f,.16f,ink,METAL)
            box(1.66f,1.85f,z*1.1f,.06f,.3f,.08f,ink,PAINT)
        }
        shape(cylinder,.42f,1.9f,-.72f,.11f,1.6f,.11f,chrome,METAL)
        // Bed hinge at the rear; positive world-Z rotation lifts the front.
        push();translate(-1.66f,.91f,0f);rotate(-f.bedAngle,0f,0f,1f)
        panel(1.05f,.07f,0f,2.7f,.14f,1.88f,teal)
        for(z in floatArrayOf(-.9f,.9f)) {
            panel(1.05f,.48f,z,2.7f,.8f,.12f,teal)
            for(i in 0..4)box(-.02f+i*.53f,.48f,z*1.08f,.055f,.66f,.05f,tealDark,PAINT)
            box(1.05f,.86f,z*1.03f,2.74f,.06f,.06f,tealDark,PAINT)
        }
        panel(2.35f,.62f,0f,.12f,1.08f,1.88f,teal)
        push();translate(-.3f,.12f,0f);rotate(if(f.tipping)-65f else 0f,0f,0f,1f)
        panel(0f,.36f,0f,.12f,.72f,1.88f,teal);pop()
        if(f.load>0)mound(1.05f,.13f,0f,2.5f,.12f+f.load*.08f,1.7f,sandColor)
        pop()
        // Tail lights.
        for(z in floatArrayOf(-.7f,.7f))box(-1.95f,.72f,z,.04f,.1f,.2f,rgba(220,40,30),EMISSIVE)
        pop()
    }
    private fun bulldozer(f:SceneFrame) {
        val dx=WorldCamera.x(f.dozerX)
        push();translate(dx,0f,f.dozerZ);rotate(f.dozerHeading,0f,1f,0f)
        tracks(2.7f,f.dozerTravel)
        panel(0f,.98f,0f,2.7f,.55f,1.55f,yellow)
        panel(-.5f,1.8f,0f,1.2f,1.12f,1.4f,yellow)
        cabWindows(-.5f,1.86f,1.2f,1.4f)
        panel(-.5f,2.42f,0f,1.36f,.1f,1.52f,ink)
        // Engine hood with grille vents, exhaust and beacon.
        panel(.78f,1.38f,0f,.95f,.36f,1.3f,yellowDark)
        for(i in 0..4)box(.45f+i*.16f,1.57f,0f,.06f,.02f,1.0f,ink,MATTE)
        shape(cylinder,.7f,1.86f,-.42f,.11f,.7f,.11f,darkSteel,METAL)
        beaconLight(-.5f,2.54f,0f,f.seconds+.35f)
        // Rear ripper.
        box(-1.45f,.8f,0f,.18f,.18f,1.0f,darkSteel,METAL)
        box(-1.6f,.45f,0f,.12f,.6f,.14f,darkSteel,METAL)
        push();translate(0f,if(f.bladeDown)0f else .55f,0f)
        for(z in floatArrayOf(-.57f,.57f))box(1.21f,.5f,z,1.05f,.14f,.15f,darkSteel,METAL)
        for(z in floatArrayOf(-.4f,.4f))rod(.95f,1.25f,1.7f,.8f,.07f,chrome,METAL)
        panel(1.83f,.53f,0f,.16f,.91f,2.15f,yellow)
        box(1.95f,.13f,0f,.16f,.1f,2.25f,metal,METAL)
        for(z in floatArrayOf(-1.02f,1.02f))box(1.79f,.5f,z,.35f,.84f,.08f,yellowDark,PAINT)
        if(f.bladeDown && f.sand[WorldMap.cell(dx+cos(f.dozerHeading*PI.toFloat()/180)*1.8f,f.dozerZ-sin(f.dozerHeading*PI.toFloat()/180)*1.8f)]>0)mound(2.1f,.05f,0f,.5f,.18f,1.6f,sandColor)
        pop();pop()
    }
    private fun beaconLight(x:Float,y:Float,z:Float,seconds:Float) {
        shape(cylinder,x,y-.04f,z,.16f,.06f,.16f,ink,METAL)
        val glow=.78f+.22f*(.5f+.5f*sin(seconds*2f))
        blob(x,y+.06f,z,.15f,.18f,.15f,rgba((255*glow).toInt(),(140*glow).toInt(),30),EMISSIVE)
    }
    private fun cabWindows(x:Float,y:Float,width:Float,length:Float) {
        for(z in floatArrayOf(-length/2-.02f,length/2+.02f)) {
            box(x,y,z,width*.84f,.78f,.03f,ink,MATTE)
            box(x,y,z*1.005f,width*.76f,.7f,.03f,glass,GLASS)
        }
        box(x+width/2+.02f,y,0f,.03f,.78f,length*.84f,ink,MATTE)
        box(x+width/2+.025f,y,0f,.03f,.7f,length*.76f,glass,GLASS)
        box(x-width/2-.02f,y+.05f,0f,.03f,.6f,length*.6f,glass,GLASS)
    }
    private fun tracks(length:Float,travel:Float) {
        val pitch=(length-.4f)/8
        val shift=((travel%pitch)+pitch)%pitch
        for(z in floatArrayOf(-.78f,.78f)) {
            box(0f,.41f,z,length-.55f,.7f,.5f,rubber,RUBBER)
            wheelDisc(-length/2+.34f,.41f,z,.35f,.5f,rubber,RUBBER)
            wheelDisc(length/2-.34f,.41f,z,.35f,.5f,rubber,RUBBER)
            // Sprocket, idler and road wheels visible on the outside.
            for(i in 0..4)wheelDisc(-length/2+.37f+i*(length-.74f)/4,.32f,z+sign(z)*.27f,.17f,.06f,darkSteel,METAL)
            wheelDisc(-length/2+.34f,.41f,z+sign(z)*.27f,.26f,.05f,metal,METAL)
            wheelDisc(length/2-.34f,.41f,z+sign(z)*.27f,.26f,.05f,metal,METAL)
            box(0f,.55f,z+sign(z)*.26f,length-.9f,.12f,.04f,darkSteel,METAL)
            // Grousers slide with travel so the tracks visibly roll.
            noCast {
                for(i in 0..7) {
                    val gx=-length/2+.2f+(i*pitch+shift)%(length-.4f)
                    box(gx,.77f,z,.1f,.05f,.54f,ink,RUBBER)
                    box(-gx,.06f,z,.1f,.05f,.54f,ink,RUBBER)
                }
            }
        }
    }
    private fun wheel(x:Float,y:Float,z:Float,angle:Float) {
        wheelDisc(x,y,z,.47f,.32f,rubber,RUBBER)
        val outer=z+sign(z)*.17f
        wheelDisc(x,y,outer,.3f,.04f,metal,METAL)
        wheelDisc(x,y,outer+sign(z)*.03f,.1f,.05f,chrome,METAL)
        // Spokes and tread blocks rotate with the wheel; they stay inside its shadow silhouette.
        noCast {
            push();translate(x,y,outer+sign(z)*.03f);rotate(angle,0f,0f,1f)
            for(k in 0..2) { rotate(60f,0f,0f,1f);box(0f,0f,0f,.5f,.05f,.02f,darkSteel,METAL) }
            pop()
            push();translate(x,y,z);rotate(angle,0f,0f,1f)
            for(k in 0..9) { rotate(36f,0f,0f,1f);box(.47f,0f,0f,.04f,.12f,.3f,rubber,RUBBER) }
            pop()
        }
    }
    private fun wrist(f:SceneFrame):Pair<Float,Float> {
        val a=f.excavatorHeading*PI.toFloat()/180f;val d=(f.armX-200f)/60f
        return WorldMap.worldX(f.excavatorX)+cos(a)*d to f.excavatorZ-sin(a)*d
    }
    private fun guide(f:SceneFrame) {
        if(f.selected!=GameState.Vehicle.EXCAVATOR)return
        val point=wrist(f)
        noCast {
            blob(point.first,WorldCamera.y(f.armY),point.second+.04f,.18f,.18f,.18f,white,EMISSIVE)
            // Ring below the bucket makes its real ground position easy to see.
            disk(point.first,.09f,point.second,.7f,.7f,rgba(255,247,204,.45f),DECAL)
        }
    }
    private fun particles(f:SceneFrame) {
        if(!shadowPass && f.transferSerial!=transferSeen) {
            transferSeen=f.transferSerial;streamStart=f.seconds
            val dumping=f.transferKind==3
            val angle=f.truckHeading*PI.toFloat()/180f
            streamX=if(dumping)WorldCamera.x(f.truckX)-cos(angle)*1.8f else wrist(f).first
            streamZ=if(dumping)f.truckZ+sin(angle)*1.8f else wrist(f).second
            streamY=if(dumping)2f else WorldCamera.y(f.armY)
            streamGround=if(f.transferKind==2)1.25f else .1f
            if(f.transferKind==4) { streamX=WorldMap.worldX(f.dozerX);streamZ=f.dozerZ;streamY=.7f }
        }
        val elapsed=f.seconds-streamStart
        if(elapsed !in 0f.. .6f)return
        for(i in 0..23) {
            val phase=(elapsed/.6f+i*.043f)%1f
            val ground=streamGround
            val y=streamY+(ground-streamY)*phase*phase
            val x=streamX+sin(i*7f)*.18f
            val r=.05f+(i%3)*.025f
            blob(x,y,streamZ+cos(i*3f)*.3f,r,r,r,sandColor,SAND)
        }
    }
    private fun dustCloud(f:SceneFrame) {
        val left=dustUntil-f.seconds
        if(left<=0f || shadowPass)return
        GL.glDepthMask(false)
        val x=WorldCamera.x(dustX)
        for(i in 0..5) {
            val t=(f.seconds*1.3f+i*.17f)%1f
            val s=.4f+t*.9f
            val c=floatArrayOf(dust[0],dust[1],dust[2],dust[3]*(1f-t)*min(left/.3f,1f))
            blob(x+sin(i*2.3f)*1.1f,.3f+t*.8f,f.dozerZ+cos(i*1.7f)*.9f,s,s*.7f,s,c,DECAL)
        }
        GL.glDepthMask(true)
    }
    private fun beam(ax:Float,ay:Float,bx:Float,by:Float,width:Float,thickness:Float,color:FloatArray,material:FloatArray) {
        val dx=bx-ax;val dy=by-ay
        push();translate((ax+bx)/2,(ay+by)/2,0f);rotate(atan2(dy,dx)*180f/PI.toFloat(),0f,0f,1f)
        draw(panelMesh,color,material,sqrt(dx*dx+dy*dy),width,thickness);pop()
    }
    /** Cylinder spanning two points in the arm plane (z=0). */
    private fun rod(ax:Float,ay:Float,bx:Float,by:Float,radius:Float,color:FloatArray,material:FloatArray) {
        val dx=bx-ax;val dy=by-ay
        push();translate((ax+bx)/2,(ay+by)/2,0f);rotate(atan2(dy,dx)*180f/PI.toFloat()-90f,0f,0f,1f)
        scale(radius*2,sqrt(dx*dx+dy*dy),radius*2);draw(cylinder,color,material);pop()
    }
    private fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray)=shape(cube,x,y,z,w,h,d,color,material)
    private fun panel(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray)=shape(panelMesh,x,y,z,w,h,d,color,PAINT)
    private fun blob(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray)=shape(sphere,x,y,z,w,h,d,color,material)
    private fun mound(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray)=shape(moundMesh,x,y,z,w,h,d,color,SAND)
    private fun disk(x:Float,y:Float,z:Float,w:Float,d:Float,color:FloatArray,material:FloatArray,h:Float=.012f)=shape(cylinder,x,y,z,w,h,d,color,material)
    private fun wheelDisc(x:Float,y:Float,z:Float,r:Float,thickness:Float,color:FloatArray,material:FloatArray) {
        push();translate(x,y,z);rotate(90f,1f,0f,0f);scale(r*2,thickness,r*2);draw(cylinder,color,material);pop()
    }
    private fun shape(mesh:Geometry,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray) {
        push();translate(x,y,z);draw(mesh,color,material,w,h,d);pop()
    }
    private fun draw(mesh:Geometry,color:FloatArray,material:FloatArray,w:Float,h:Float,d:Float) {
        push();scale(w,h,d);draw(mesh,color,material);pop()
    }
    private fun draw(mesh:Geometry,color:FloatArray,material:FloatArray) {
        if(shadowPass) {
            if(!casting || color[3]<1f || material[4]>=1f && material[4]<=3f)return
            Matrix.multiplyMM(mvp,0,lightVp,0,model,0)
            GL.glUniformMatrix4fv(uDepthMvp,1,false,mvp,0)
            mesh.draw(depthPosition,-1);drawCalls++
            return
        }
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        // Inverse-transpose normal matrix for orthogonal TRS columns, without per-object allocation.
        for(column in 0..2) {
            val k=column*4;val n=column*3
            val squared=(model[k]*model[k]+model[k+1]*model[k+1]+model[k+2]*model[k+2]).coerceAtLeast(1e-12f)
            for(row in 0..2)normals[n+row]=model[k+row]/squared
        }
        GL.glUniformMatrix4fv(uMvp,1,false,mvp,0);GL.glUniformMatrix4fv(uModel,1,false,model,0)
        GL.glUniformMatrix3fv(uNormal,1,false,normals,0);GL.glUniform4fv(uColor,1,color,0)
        GL.glUniform4f(uMaterial,material[0],material[1],material[2],material[3]);GL.glUniform1f(uKind,material[4])
        mesh.draw(position,normal);drawCalls++
    }
    private fun push() { check(depth<stack.lastIndex);model.copyInto(stack[depth+1]);depth++ }
    private fun pop() { check(depth>0);depth-- }
    private fun translate(x:Float,y:Float,z:Float)=Matrix.translateM(model,0,x,y,z)
    private fun scale(x:Float,y:Float,z:Float)=Matrix.scaleM(model,0,x,y,z)
    private fun rotate(a:Float,x:Float,y:Float,z:Float)=Matrix.rotateM(model,0,a,x,y,z)
    companion object {
        private const val GRASS_Y=-.7f
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
        const val DEPTH_VERTEX="""
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            void main() { gl_Position=uMvp*vec4(aPosition,1.0); }
        """
        const val DEPTH_FRAGMENT="""
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            void main() {
                vec4 e=fract(gl_FragCoord.z*vec4(1.0,255.0,65025.0,16581375.0));
                gl_FragColor=e-e.yzww*vec4(1.0/255.0,1.0/255.0,1.0/255.0,0.0);
            }
        """
        const val VERTEX="""
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat3 uNormal;
            uniform mat4 uLightVp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define VARYING_PRECISION highp
            #else
            #define VARYING_PRECISION mediump
            #endif
            varying VARYING_PRECISION vec3 vNormal;
            varying VARYING_PRECISION vec3 vWorld;
            varying VARYING_PRECISION vec4 vLight;
            void main() {
                vec4 world=uModel*vec4(aPosition,1.0);
                vNormal=normalize(uNormal*aNormal);
                vWorld=world.xyz;
                // Normal offset keeps lit faces from shadowing themselves.
                vLight=uLightVp*vec4(world.xyz+vNormal*0.035,1.0);
                gl_Position=uMvp*vec4(aPosition,1.0);
            }
        """
        /** Kinds: 0 lit, 1 emissive, 2 sky, 3 unlit decal, 4 grass, 5 glass, 6 metal, 7 sand, 8 cloud. */
        const val FRAGMENT="""
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec4 uColor;
            uniform vec4 uMaterial;
            uniform float uKind;
            uniform vec3 uEye;
            uniform vec3 uSun;
            uniform sampler2D uShadowMap;
            uniform float uShadowOn;
            uniform vec2 uShadowTexel;
            uniform vec4 uViewport;
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            #define VARYING_PRECISION highp
            #else
            #define VARYING_PRECISION mediump
            #endif
            varying VARYING_PRECISION vec3 vNormal;
            varying VARYING_PRECISION vec3 vWorld;
            varying VARYING_PRECISION vec4 vLight;

            float hash(vec3 p) {
                p=fract(p*0.3183099+vec3(0.71,0.113,0.419));
                p*=17.0;
                #ifdef GL_FRAGMENT_PRECISION_HIGH
                return fract(p.x*p.y*p.z*(p.x+p.y+p.z));
                #else
                return fract(dot(p,vec3(0.13,0.17,0.19)));
                #endif
            }
            float noise(vec3 x) {
                vec3 i=floor(x);vec3 f=fract(x);f=f*f*(3.0-2.0*f);
                return mix(mix(mix(hash(i),hash(i+vec3(1.0,0.0,0.0)),f.x),mix(hash(i+vec3(0.0,1.0,0.0)),hash(i+vec3(1.0,1.0,0.0)),f.x),f.y),
                           mix(mix(hash(i+vec3(0.0,0.0,1.0)),hash(i+vec3(1.0,0.0,1.0)),f.x),mix(hash(i+vec3(0.0,1.0,1.0)),hash(i+vec3(1.0,1.0,1.0)),f.x),f.y),f.z);
            }
            vec3 skyColor(vec3 dir) {
                float h=clamp(dir.y,0.0,1.0);
                vec3 horizon=vec3(0.62,0.76,0.86);
                vec3 zenith=vec3(0.10,0.30,0.66);
                vec3 sky=mix(horizon,zenith,pow(h,0.5));
                sky=mix(sky,vec3(0.42,0.44,0.40),clamp(-dir.y*3.0,0.0,1.0));
                float s=max(dot(dir,uSun),0.0);
                return sky+vec3(1.0,0.8,0.55)*(pow(s,8.0)*0.22+pow(s,900.0)*12.0);
            }
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            float unpackDepth(vec4 c) { return dot(c,vec4(1.0,1.0/255.0,1.0/65025.0,1.0/16581375.0)); }
            float shadowTap(vec2 uv,float d) { return step(d,unpackDepth(texture2D(uShadowMap,uv))); }
            float shadow(vec3 n) {
                if(uShadowOn<0.5) return 1.0;
                vec3 p=vLight.xyz/vLight.w*0.5+0.5;
                if(p.x<0.0||p.x>1.0||p.y<0.0||p.y>1.0||p.z>1.0) return 1.0;
                float d=p.z-0.0008-0.002*(1.0-clamp(dot(n,uSun),0.0,1.0));
                vec2 t=uShadowTexel*1.4;
                float s=shadowTap(p.xy,d)*2.0;
                s+=shadowTap(p.xy+vec2(-t.x,-t.y),d);
                s+=shadowTap(p.xy+vec2(t.x,-t.y),d);
                s+=shadowTap(p.xy+vec2(-t.x,t.y),d);
                s+=shadowTap(p.xy+vec2(t.x,t.y),d);
                s+=shadowTap(p.xy+vec2(0.0,2.0*t.y),d);
                s+=shadowTap(p.xy+vec2(0.0,-2.0*t.y),d);
                s+=shadowTap(p.xy+vec2(2.0*t.x,0.0),d);
                s+=shadowTap(p.xy+vec2(-2.0*t.x,0.0),d);
                return s/10.0;
            }
            #else
            float shadow(vec3 n) { return 1.0; }
            #endif
            vec3 finish(vec3 c) {
                c*=0.78;
                c=clamp((c*(2.51*c+0.03))/(c*(2.43*c+0.59)+0.14),0.0,1.0);
                c=pow(c,vec3(1.0/2.2));
                vec2 uv=(gl_FragCoord.xy-uViewport.xy)/uViewport.zw-0.5;
                return c*(1.0-0.28*dot(uv,uv));
            }
            void main() {
                vec3 toEye=uEye-vWorld;
                float dist=length(toEye);
                vec3 view=toEye/dist;
                if(uKind>1.5&&uKind<2.5) { gl_FragColor=vec4(finish(skyColor(-view)),1.0);return; }
                if(uKind>2.5&&uKind<3.5) { gl_FragColor=uColor;return; }
                vec3 albedo=pow(uColor.rgb,vec3(2.2));
                if(uKind>0.5&&uKind<1.5) { gl_FragColor=vec4(finish(albedo*3.2),uColor.a);return; }
                vec3 n=normalize(vNormal);
                float fade=clamp(1.0-dist/60.0,0.0,1.0);
                if(uMaterial.w>0.0) {
                    float detail=noise(vWorld*uMaterial.z)*0.65+noise(vWorld*uMaterial.z*3.1)*0.35;
                    albedo*=1.0+(detail-0.5)*2.0*uMaterial.w*mix(0.5,1.0,fade);
                }
                if(uKind>3.5&&uKind<4.5) {
                    // Meadow: large patches of lush and dry grass, plus fine blade speckle near the camera.
                    float patch=noise(vWorld*0.21)*0.6+noise(vWorld*0.05)*0.4;
                    albedo=mix(albedo,albedo*vec3(1.22,1.1,0.72),0.55*smoothstep(0.5,0.8,patch));
                    albedo*=mix(0.82,1.08,noise(vWorld*1.7));
                    albedo*=1.0+(noise(vWorld*9.0)-0.5)*0.45*fade;
                }
                if(uKind>6.5&&uKind<7.5) {
                    // Sand: individual sparkling grains and soft ripples.
                    albedo*=0.92+0.16*sin(vWorld.x*7.0+noise(vWorld*2.0)*4.0)*fade*0.5;
                }
                float ndl=max(dot(n,uSun),0.0);
                float lit=ndl>0.0?shadow(n):0.0;
                vec3 sunColor=vec3(1.0,0.93,0.82)*3.0;
                vec3 skyAmbient=mix(vec3(0.20,0.17,0.12),vec3(0.36,0.46,0.62),n.y*0.5+0.5);
                // Low vertical surfaces near the ground receive less sky light.
                float ao=1.0-(1.0-abs(n.y))*0.38*(1.0-smoothstep(0.0,0.9,vWorld.y));
                if(uKind>7.5) { skyAmbient=vec3(0.62,0.68,0.76);ao=1.0; }
                vec3 h=normalize(uSun+view);
                float shininess=uMaterial.y;
                float spec=pow(max(dot(n,h),0.0),shininess)*uMaterial.x*(shininess+8.0)/25.0;
                float fresnel=pow(1.0-max(dot(n,view),0.0),5.0);
                vec3 color=albedo*(skyAmbient*ao+sunColor*ndl*lit);
                vec3 reflection=skyColor(reflect(-view,n));
                if(uKind>4.5&&uKind<5.5) color=albedo*0.4+reflection*(0.12+0.75*fresnel);
                else if(uKind>5.5&&uKind<6.5) color+=albedo*reflection*0.35*ao;
                else color+=reflection*fresnel*uMaterial.x*0.25*ao;
                color+=sunColor*spec*ndl*lit;
                // Aerial perspective: distant hills soften toward the horizon colour.
                if(dist>30.0) color=mix(color,skyColor(normalize(vec3(-view.x,0.04,-view.z))),1.0-exp(-(dist-30.0)*0.0055));
                gl_FragColor=vec4(finish(color),uColor.a);
            }
        """
    }
}
