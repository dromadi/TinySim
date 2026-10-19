package com.dromadi.tinysim.scene

import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.dromadi.tinysim.TornadoArena.Prop as ArenaProp
import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.ArmRig
import com.dromadi.tinysim.WorldMap
import com.dromadi.tinysim.Terrain
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * Real depth-tested 3D meshes with a sun shadow map, linear-space lighting, procedural surface
 * detail, sky, fog and filmic tone mapping. All graphics are generated on-device.
 */
class SceneRenderer3D(uploader:TextureUploader?=null): GLSurfaceView.Renderer {
    private val textures=MaterialTextures(uploader)
    private var uAlbedoMap=0;private var uNormalMap=0;private var uRoughMap=0
    private var uTime=0;private var uTexOn=0;private var uTexScale=0;private var uTexMean=0;private var uTexFrame=0;private var uTexRot=0
    // Texture frame: identity for the static world, the inverse vehicle pose while drawing a vehicle,
    // so photo materials stick to moving bodies instead of sliding across them.
    private val texFrame=FloatArray(16).also { Matrix.setIdentityM(it,0) }
    private val texRot=FloatArray(9)
    private var boundSlot=-2
    @Volatile private var frame=SceneFrame.capture(GameState(),0,0f)
    private val cube=Geometry.cube()
    private val panelMesh=Geometry.bevelCube()
    private val cylinder=Geometry.cylinder()
    private val tyreMesh=Geometry.tyre()
    private val cone=Geometry.cone()
    private val sphere=Geometry.sphere()
    private val moundMesh=Geometry.mound()
    private val arenaLayouts=Array(3) { com.dromadi.tinysim.TornadoArena.layout(it) }
    private val funnelMesh=Geometry.windFunnel()
    private val ribbonMesh=Geometry.windFunnel(true)
    private var sceneWind=WindFrame()
    private val plane=Geometry.plane()
    private val gable=Geometry.gable()
    private val terrain=TerrainMesh()
    private fun support(f:SceneFrame,x:Float,z:Float)=Terrain.height(f.earth,f.sand,x,z,f.bridgeStage>=3)-.07f
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
    // Vehicle life: per-vehicle motion (excavator, truck, dozer), driver wave and tyre/track marks.
    private val motion=Array(GameState.Vehicle.entries.size) { com.dromadi.tinysim.VehicleMotion() }
    private var lastSeconds=Float.NaN
    private var greetSeen=-1;private var waveStart=-10f;private var waveVehicle=0
    private val marks=Array(GameState.Vehicle.entries.size) { FloatArray(MARKS*4) }
    private val markCount=IntArray(GameState.Vehicle.entries.size);private val markNext=IntArray(GameState.Vehicle.entries.size)
    private val lastStamp=Array(GameState.Vehicle.entries.size) { floatArrayOf(Float.NaN,0f) }
    private val tint=FloatArray(4)
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
    private val glass=rgba(40,62,72,.42f)
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
    private val skin=rgba(226,172,132)
    private val vest=rgba(255,120,30)
    private val hat=rgba(255,206,40)
    private val trousers=rgba(52,70,110)
    private val smoke=rgba(88,88,92)
    private val tread=rgba(70,52,34)

    // Material: specular strength, shininess, detail frequency, detail amount, kind.
    private val PAINT=floatArrayOf(.55f,60f,5f,.02f,0f,MaterialTextures.PAINT.toFloat(),2.2f)
    private val MATTE=floatArrayOf(.08f,10f,3f,.08f,0f)
    private val METAL=floatArrayOf(.9f,90f,9f,.05f,6f)
    private val GLASS=floatArrayOf(1.2f,160f,0f,0f,5f)
    private val RUBBER=floatArrayOf(.12f,14f,16f,.12f,0f)
    private val GRASS=floatArrayOf(.04f,8f,1.1f,.18f,4f,MaterialTextures.GRASS.toFloat(),3.5f)
    private val SAND=floatArrayOf(.07f,12f,26f,.04f,7f,MaterialTextures.SAND.toFloat(),2.4f)
    private val SOIL=floatArrayOf(.05f,8f,6f,.05f,7f,MaterialTextures.SOIL.toFloat(),3.2f)
    private val FOLIAGE=floatArrayOf(.12f,12f,2.6f,.28f,0f)
    private val WATER=floatArrayOf(0f,1f,0f,0f,9f)
    private val BARK=floatArrayOf(.06f,10f,7f,.18f,0f)
    private val CONCRETE=floatArrayOf(.06f,10f,4f,.03f,0f,MaterialTextures.CONCRETE.toFloat(),2.6f)
    private val ASPHALT=floatArrayOf(.08f,14f,4f,.02f,0f,MaterialTextures.ASPHALT.toFloat(),3f)
    private val PLASTER=floatArrayOf(.05f,8f,4f,.02f,0f,MaterialTextures.PLASTER.toFloat(),2.2f)
    private val ROOF=floatArrayOf(.18f,20f,4f,.02f,0f,MaterialTextures.ROOF.toFloat(),2f)
    private val WOOD=floatArrayOf(.06f,10f,7f,.04f,0f,MaterialTextures.WOOD.toFloat(),1.6f)
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
        uAlbedoMap=GL.glGetUniformLocation(program,"uAlbedoMap");uNormalMap=GL.glGetUniformLocation(program,"uNormalMap");uRoughMap=GL.glGetUniformLocation(program,"uRoughMap")
        uTexOn=GL.glGetUniformLocation(program,"uTexOn");uTexScale=GL.glGetUniformLocation(program,"uTexScale");uTexMean=GL.glGetUniformLocation(program,"uTexMean")
        uTime=GL.glGetUniformLocation(program,"uTime")
        uTexFrame=GL.glGetUniformLocation(program,"uTexFrame");uTexRot=GL.glGetUniformLocation(program,"uTexRot")
        textures.load()
        for(mesh in arrayOf(cube,panelMesh,cylinder,tyreMesh,cone,sphere,moundMesh,plane,gable,funnelMesh,ribbonMesh)) { mesh.contextLost();mesh.upload() }
        terrain.update(frame.earth,frame.sand);terrain.contextLost();terrain.upload()
        GL.glEnable(GL.GL_DEPTH_TEST);GL.glDepthFunc(GL.GL_LEQUAL)
        GL.glEnable(GL.GL_CULL_FACE);GL.glCullFace(GL.GL_BACK)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)
        createShadowMap()
        // The sun is fixed, so its orthographic frustum is fitted once around the work site.
        val c=Vec3(31f,0f,12f)
        Matrix.setLookAtM(lightView,0,c.x+sun.x*75f,c.y+sun.y*75f,c.z+sun.z*75f,c.x,c.y,c.z,0f,1f,0f)
        Matrix.orthoM(lightProjection,0,-58f,58f,-46f,46f,5f,220f)
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
        // Only the quads around changed cells are recomputed and re-uploaded.
        if(terrain.update(f.earth,f.sand)!=null)terrain.upload()
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
        val camera=if(f.wash.ready)com.dromadi.tinysim.WashBay.camera(f.wash.x,f.wash.z,f.wash.base) else WorldCamera.spec(f.camera,f.yaw,f.pitch,f.zoom,f.panX,f.panZ)
        Matrix.setLookAtM(view,0,camera.eye.x,camera.eye.y,camera.eye.z,camera.target.x,camera.target.y,camera.target.z,0f,1f,0f)
        Matrix.perspectiveM(projection,0,camera.fov,WorldCamera.ASPECT,.8f,260f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        GL.glUseProgram(program);GL.glEnableVertexAttribArray(position);GL.glEnableVertexAttribArray(normal)
        GL.glUniform3f(uEye,camera.eye.x,camera.eye.y,camera.eye.z);GL.glUniform3f(uSun,sun.x,sun.y,sun.z)
        GL.glUniformMatrix4fv(uLightVp,1,false,lightVp,0)
        GL.glUniform1f(uShadowOn,if(shadowsEnabled)1f else 0f);GL.glUniform2f(uShadowTexel,1f/shadowSize,1f/shadowSize)
        GL.glUniform4f(uViewport,viewportX.toFloat(),viewportY.toFloat(),viewportW.toFloat(),viewportH.toFloat())
        GL.glActiveTexture(GL.GL_TEXTURE0);GL.glBindTexture(GL.GL_TEXTURE_2D,if(shadowsEnabled)shadowTexture else 0);GL.glUniform1i(uShadowMap,0)
        GL.glUniform1f(uTime,f.seconds%600f)
        GL.glUniform1i(uAlbedoMap,1);GL.glUniform1i(uNormalMap,2);GL.glUniform1i(uRoughMap,3);boundSlot=-2
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
        if(f.arena.open) { arenaScene(f);return }
        sceneWind=f.wind
        environment(f)
        windGarden(f)
        destination(f)
        contactShadows(f)
        treadMarks(f)
        sand(f)
        sandCaps(f)
        bridgeYard(f)
        bridge(f)
        blockYard(f)
        pipeSite(f)
        workYard(f)
        recoverySite(f)
        washBay(f)
        village(f)
        serviceTruck(f,false)
        serviceTruck(f,true)
        bridgeKeeper(f)
        crane(f)
        house(f)
        excavator(f)
        vehicleDirt(f)
        truck(f)
        bulldozer(f)
        if(f.selected==GameState.Vehicle.EXCAVATOR)guide(f)
        particles(f)
        dustCloud(f)
        water(f)
        tornado(f,if(f.wind.z>=25f).5f+f.recovery.level*.25f else 1f)
    }
    private fun skyDome(eye:Vec3) {
        GL.glDisable(GL.GL_CULL_FACE);GL.glDepthMask(false)
        push();translate(eye.x,eye.y,eye.z);scale(400f,400f,400f);draw(sphere,cloudColor,SKY);pop()
        GL.glDepthMask(true);GL.glEnable(GL.GL_CULL_FACE)
    }
    private fun updateEffects(f:SceneFrame) {
        val dt=if(lastSeconds.isNaN())0f else (f.seconds-lastSeconds).coerceIn(0f,.1f)
        lastSeconds=f.seconds
        val travel=floatArrayOf(f.excavatorTravel,f.truckTravel,f.dozerTravel,f.craneTravel,f.work.mixerTravel,f.work.wreckTravel)
        val px=floatArrayOf(WorldMap.worldX(f.excavatorX),WorldMap.worldX(f.truckX),WorldMap.worldX(f.dozerX),WorldMap.worldX(f.craneX),WorldMap.worldX(f.work.mixerX),WorldMap.worldX(f.work.wreckX))
        val pz=floatArrayOf(f.excavatorZ,f.truckZ,f.dozerZ,f.craneZ,f.work.mixerZ,f.work.wreckZ)
        val heading=floatArrayOf(f.excavatorHeading,f.truckHeading,f.dozerHeading,f.craneHeading,f.work.mixerHeading,f.work.wreckHeading)
        for(v in motion.indices) {
            motion[v].update(travel[v],dt)
            val last=lastStamp[v]
            if(last[0].isNaN() || hypot(px[v]-last[0],pz[v]-last[1])>.45f) {
                if(!last[0].isNaN()) {
                    val o=markNext[v]*4
                    marks[v][o]=px[v];marks[v][o+1]=pz[v];marks[v][o+2]=heading[v];marks[v][o+3]=f.seconds
                    markNext[v]=(markNext[v]+1)%MARKS;markCount[v]=min(MARKS,markCount[v]+1)
                }
                last[0]=px[v];last[1]=pz[v]
            }
        }
        if(f.greetSerial!=greetSeen) { if(greetSeen>=0) { waveStart=f.seconds;waveVehicle=f.greetVehicle.ordinal };greetSeen=f.greetSerial }
        if(!lastDozerX.isNaN() && hypot((f.dozerX-lastDozerX)/60f,f.dozerZ-lastDozerZ)>.008f && f.bladeDown) { dustUntil=f.seconds+.7f;dustX=f.dozerX }
        lastDozerX=f.dozerX;lastDozerZ=f.dozerZ
    }
    private inline fun noCast(block:()->Unit) { val was=casting;casting=false;block();casting=was }
    private fun environment(f:SceneFrame) {
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
        // Open-top soil volume: the heightfield can dip below the original surface.
        noCast {
            for(z in floatArrayOf(-7f,WorldMap.MAX_Z))box(31f,-.8f,z,86f,1.7f,.08f,soilSide,SOIL)
            for(x in floatArrayOf(-12f,74f))box(x,-.8f,17f,.08f,1.7f,48f,soilSide,SOIL)
        }
        draw(terrain,soil,SOIL)
        noCast { for(i in 0..37)for(z in floatArrayOf(-1.6f,1.6f)) {
            val x=-1.2f+i*1.2f;box(x,Terrain.height(f.earth,f.sand,x,z)+.012f,z,.55f,.012f,.07f,paintWhite,MATTE)
        } }
        // Concrete kerb around the site; too low to cast a visible shadow.
        noCast {
            for(i in 0..67) {
                val x=-10.6f+i*1.25f
                if(x>WorldMap.WEST_BANK_X+.6f && x<WorldMap.EAST_BANK_X-.6f)continue
                box(x,.075f,-6.65f,1.08f,.09f,.22f,stone,CONCRETE)
                box(x,.075f,40.65f,1.08f,.09f,.22f,stone,CONCRETE)
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
        for(i in 0..5)box(56f+i*3f,.55f,-6.5f,.14f,1.05f,.14f,bark,WOOD)
        box(64f,.35f,-6.5f,16f,.09f,.06f,paintWhite,MATTE);box(64f,.8f,-6.5f,16f,.09f,.06f,orange,PAINT)
        eastBank(f)
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
        for(x in floatArrayOf(25f,31f,38f,45f,49f)) { tree(x,-6f,1f);tree(x,43f,1.4f) }
        for(x in floatArrayOf(11f,25f,30f,44f))for(z in floatArrayOf(2.5f,11.5f))trafficCone(x,z)
    }
    private fun destination(f:SceneFrame) {
        if(shadowPass)return
        noCast {
            GL.glDepthMask(false)
            val sx=when(f.selected) { GameState.Vehicle.EXCAVATOR->WorldMap.worldX(f.excavatorX);GameState.Vehicle.TRUCK->WorldMap.worldX(f.truckX);GameState.Vehicle.DOZER->WorldMap.worldX(f.dozerX);GameState.Vehicle.CRANE->WorldMap.worldX(f.craneX);GameState.Vehicle.MIXER->WorldMap.worldX(f.work.mixerX);GameState.Vehicle.WRECKER->WorldMap.worldX(f.work.wreckX) }
            val sz=when(f.selected) { GameState.Vehicle.EXCAVATOR->f.excavatorZ;GameState.Vehicle.TRUCK->f.truckZ;GameState.Vehicle.DOZER->f.dozerZ;GameState.Vehicle.CRANE->f.craneZ;GameState.Vehicle.MIXER->f.work.mixerZ;GameState.Vehicle.WRECKER->f.work.wreckZ }
            disk(sx,.092f,sz,4.5f,3f,rgba(53,196,180,.16f),DECAL)
            val goal=when(f.selected) { GameState.Vehicle.TRUCK->f.truckGoal;GameState.Vehicle.DOZER->f.dozerGoal;GameState.Vehicle.CRANE->f.craneGoal;GameState.Vehicle.MIXER->f.work.mixerGoal;GameState.Vehicle.WRECKER->f.work.wreckGoal;else->f.excavatorGoal }
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
    /** Same sixteen parts in the house, on the ground, in the bed and beneath the hook. */
    private fun recoveryPart(id:Int) {
        val color=if(id/4%2==0)rgba(238,166,91) else rgba(103,182,174)
        if(id%4==3) {
            for(side in floatArrayOf(-1f,1f)) {
                push();translate(0f,.25f,side*.62f);rotate(side*28f,1f,0f,0f)
                surface(0f,0f,0f,2.5f,.18f,1.5f,rgba(177,78,53),ROOF);pop()
            }
            box(0f,.61f,0f,2.55f,.12f,.14f,orange,PAINT)
        } else {
            val front=id%4==2
            push();if(!front)rotate(90f,0f,1f,0f)
            // A real opening between piers keeps the blue window visibly inset.
            for(dx in floatArrayOf(-.8f,.8f))surface(dx,.85f,0f,.45f,1.7f,.2f,color,PLASTER)
            surface(0f,.26f,0f,1.3f,.52f,.2f,color,PLASTER)
            surface(0f,1.54f,0f,1.3f,.32f,.2f,color,PLASTER)
            box(0f,.98f,0f,1.1f,.94f,.07f,rgba(123,204,221),GLASS)
            for(dx in floatArrayOf(-.56f,0f,.56f))box(dx,.98f,.12f,.05f,1f,.05f,paintWhite,PAINT)
            for(dy in floatArrayOf(.5f,1f,1.47f))box(0f,dy,.12f,1.17f,.05f,.05f,paintWhite,PAINT)
            pop()
        }
    }
    private fun recoverySite(f:SceneFrame) {
        val r=f.recovery;val site=com.dromadi.tinysim.RecoverySite
        // Connected gravel roads and broad work bays are part of the expanded main terrain.
        for(z in floatArrayOf(24f,32f,40f))surface(23f,.055f,z,41f,.06f,2.2f,rgba(165,158,143),SOIL)
        for(h in 0..3) {
            val x=site.houseX(h);val z=site.houseZ(h)
            surface(x,.1f,z,3.4f,.15f,3.4f,if(r.pads[h]>0)rgba(165,174,169) else sandColor,if(r.pads[h]>0)CONCRETE else SAND)
            for(k in 0 until r.pads[h])box(x-1.2f+k*.8f,.2f,z,.7f,.08f,2.8f,stone,CONCRETE)
            for(dx in floatArrayOf(-2f,2f))trafficCone(x+dx,z-2f)
            // Four footprints remain visible while parts are away.
            for(k in 0..3)noCast { box(x-1.15f+k*.77f,.22f,z+1.4f,.4f,.015f,.12f,if(r.state[h*4+k]==0)teal else yellow,DECAL) }
        }
        for(i in r.state.indices)if(r.state[i] in listOf(0,1,2,3,5)) {
            push();translate(r.x[i],support(f,r.x[i],r.z[i])+r.y[i]+.18f,r.z[i])
            if(r.state[i]==1) { rotate(f.seconds*65f+i*28f,0f,1f,0f);rotate(sin(f.seconds*2+i)*18f,1f,0f,0f) }
            else if(r.state[i]!=0) {
                rotate(i*31f,0f,1f,0f)
                when(i%4) { 0,1->rotate(90f,0f,0f,1f);2->rotate(90f,1f,0f,0f);else->rotate(12f,1f,0f,0f) }
                if(i%4!=3)translate(0f,-.85f,0f)
            }
            recoveryPart(i);pop()
        }
        // Loading ramp and depot pictograms use the same silhouettes as the working parts.
        surface(site.PILE_X,.06f,site.PILE_Z,3.5f,.08f,3.5f,stone,CONCRETE)
        for(z in floatArrayOf(34f,38f))box(site.PILE_X,.38f,z,4f,.65f,.15f,yellow,PAINT)
        for(x in floatArrayOf(35.5f,40f)) { box(x,1.2f,24.4f,.18f,2.4f,.18f,teal,PAINT) }
        box(37.75f,2.4f,24.4f,4.7f,.28f,.3f,teal,PAINT)
        for(x in floatArrayOf(5f,19f,34f,46f)) { tree(x,43f,1.3f);trafficCone(x,24f) }
    }
    /** Residents stay on their own porch/yard; storms hide them before the next rendered frame. */
    private fun vehicleDirt(f:SceneFrame) {
        val bodies=arrayOf(floatArrayOf(WorldMap.worldX(f.excavatorX),f.excavatorZ,f.excavatorHeading),
            floatArrayOf(WorldMap.worldX(f.truckX),f.truckZ,f.truckHeading),floatArrayOf(WorldMap.worldX(f.dozerX),f.dozerZ,f.dozerHeading),
            floatArrayOf(WorldMap.worldX(f.craneX),f.craneZ,f.craneHeading),floatArrayOf(WorldMap.worldX(f.work.mixerX),f.work.mixerZ,f.work.mixerHeading),
            floatArrayOf(WorldMap.worldX(f.work.wreckX),f.work.wreckZ,f.work.wreckHeading))
        for(v in GameState.Vehicle.entries) {
            val b=bodies[v.ordinal];push();translate(b[0],support(f,b[0],b[1]),b[1]);rotate(b[2],0f,1f,0f)
            for((i,q) in com.dromadi.tinysim.WashBay.patches(v).withIndex()) {
                val d=f.wash.dirt[v.ordinal][i];val foam=f.wash.foam[v.ordinal][i]
                if(d>.005f) { val a=sqrt(d);if(i==7)blob(q.x,q.y,q.z,.6f*a,.045f,.5f*a,rgba(108,73,41),SOIL) else blob(q.x,q.y,q.z,.43f*a,.28f*a,.06f,rgba(108,73,41),SOIL) }
                if(foam>.005f)for(k in 0..4) {
                    val dx=(k%3-1)*.15f;val dy=(k/3-.5f)*.17f;val s=(.14f+k%2*.04f)*sqrt(foam)
                    blob(q.x+dx,q.y+(if(i==7).08f else dy),q.z+(if(i==7)dy else .08f),s,s,s,rgba(240,250,253),MATTE)
                }
            }
            pop()
        }
    }
    private fun washBay(f:SceneFrame) {
        val x=com.dromadi.tinysim.WashBay.X;val z=com.dromadi.tinysim.WashBay.Z;val y=support(f,x,z)
        surface(x,y+.03f,z,5f,.06f,4.3f,stone,CONCRETE)
        for(dx in floatArrayOf(-2.4f,2.4f))box(x+dx,y+.1f,z,.12f,.08f,4.3f,teal,PAINT)
        for(k in 0..7)box(x-1.4f+k*.4f,y+.08f,z+1.7f,.2f,.04f,.13f,ink,METAL)
        box(x-2.6f,y+.75f,z-.8f,.65f,1.5f,.6f,teal,PAINT)
        shape(cylinder,x-2.6f,y+1.6f,z-.8f,.16f,.2f,.16f,chrome,METAL)
        for(k in 0..5)blob(x-2.25f+k*.23f,y+.13f,z-1.1f+sin(k*.9f)*.12f,.18f,.13f,.18f,ink,MATTE)
        if(f.wash.open)noCast { disk(x,y+.075f,z,2.4f,1.8f,rgba(116,190,205),DECAL) }
    }
    private fun village(f:SceneFrame) {
        val v=f.village;val site=com.dromadi.tinysim.VillageLife
        for(h in 0..3) {
            val gx=site.gardenX(h);val gz=site.gardenZ(h)
            if(v.welcomed and (1 shl h)!=0) {
                val ground=support(f,gx,gz)
                surface(gx,ground+.08f,gz,2.2f,.12f,1.8f,rgba(107,154,84),GRASS)
                for(dx in floatArrayOf(-1.15f,1.15f))box(gx+dx,ground+.18f,gz,.1f,.22f,1.95f,bark,WOOD)
                for(dz in floatArrayOf(-.95f,.95f))box(gx,ground+.18f,gz+dz,2.4f,.22f,.1f,bark,WOOD)
            }
            if(v.planted and (1 shl h)!=0) {
                push();translate(0f,support(f,gx,gz)+.15f,0f);tree(gx,gz,.18f+.58f*v.growth[h]);pop()
                for(k in 0..3) { val fx=gx-.8f+k*.5f;shape(cylinder,fx,support(f,fx,gz+.65f)+.25f,gz+.65f,.035f,.35f,.035f,leaf,MATTE);blob(fx,support(f,fx,gz+.65f)+.45f,gz+.65f,.14f,.1f,.14f,if(k%2==0)yellow else orange,MATTE) }
            }
            if(!v.outside[h])continue
            val t=v.arrival[h];val x=site.residentX(h,t);val z=site.residentZ(h,t)
            push();translate(x,support(f,x,z)+.12f,z)
            val shirt=if(h%2==0)teal else orange
            val face=if(h<2)skin else rgba(150,103,76)
            val walk=if(t<1f)sin(t*34f)*.14f else 0f
            for(side in floatArrayOf(-1f,1f)) {
                box(side*.13f,.29f,side*walk,.16f,.48f,.18f,trousers,MATTE)
                box(side*.13f,.06f,.06f+side*walk,.21f,.13f,.28f,ink,MATTE)
            }
            box(0f,.71f,0f,.48f,.47f,.3f,shirt,PAINT)
            blob(0f,1.12f,0f,.32f,.34f,.3f,face,MATTE)
            blob(0f,1.33f,-.02f,.34f,.15f,.32f,ink,MATTE)
            for(dx in floatArrayOf(-.1f,.1f))blob(dx,1.15f,.275f,.035f,.045f,.025f,ink,MATTE)
            val wave=if(v.wave[h]>0f || v.planted and (1 shl h)==0)1f else 0f
            val handX=.34f+sin(f.seconds*9f+h)*.12f*wave
            rod3(.25f,.82f,0f,handX,.63f+.5f*wave,.05f,.07f,shirt)
            blob(handX,.63f+.5f*wave,.05f,.1f,.1f,.1f,face,MATTE)
            rod3(-.25f,.82f,0f,-.34f,.5f,.02f,.07f,shirt)
            blob(-.34f,.5f,.02f,.1f,.1f,.1f,face,MATTE)
            if(v.planted and (1 shl h)==0) {
                blob(.65f,1.9f,.1f,.5f,.43f,.12f,paintWhite,MATTE)
                box(.65f,1.78f,.23f,.07f,.32f,.05f,bark,MATTE)
                blob(.65f,2.01f,.24f,.25f,.23f,.06f,leaf,MATTE)
            }
            pop()
        }
    }
    private fun arenaScene(f:SceneFrame) {
        val a=f.arena
        val w=WindFrame(a.running,a.strength,a.x,a.z,a.phase)
        sceneWind=w
        noCast { box(64f,-.65f,17f,100f,.1f,90f,if(a.map==2)rgba(58,154,181) else grass,if(a.map==2)WATER else GRASS) }
        val floor=when(a.map) { 0->GRASS;1->CONCRETE;else->WOOD }
        surface(64.5f,-.12f,17f,15.6f,.3f,14.5f,if(a.map==0)rgba(132,168,83) else if(a.map==1)rgba(203,198,177) else rgba(176,142,98),floor)
        if(a.map==0) {
            noCast { disk(64.5f,.055f,17f,9f,8.5f,sandColor,SAND) }
            for(x in floatArrayOf(56.5f,72.5f))for(z in floatArrayOf(10f,24f))tree(x,z,1.4f)
        } else if(a.map==1) {
            box(64.5f,.05f,16.8f,14f,.02f,1.7f,darkSteel,ASPHALT)
            noCast { for(i in 0..10)box(58.3f+i*1.2f,.07f,16.8f,.6f,.01f,.06f,paintWhite,MATTE) }
            for(x in floatArrayOf(57f,72f)) { box(x,1.5f,10.5f,.12f,3f,.12f,metal,METAL);blob(x,3f,10.5f,.5f,.3f,.5f,lamp,EMISSIVE) }
        } else {
            for(i in 0..25)box(57.2f+i*.58f,.05f,17f,.045f,.015f,14.3f,bark,WOOD)
            for(x in floatArrayOf(57f,72f))for(z in floatArrayOf(10f,24f)) { shape(cylinder,x,.3f,z,.3f,1.3f,.3f,bark,WOOD);disk(x,1f,z,.35f,.35f,paintWhite,MATTE) }
            push();translate(74f,.1f,15f);rotate(-10f,0f,1f,0f)
            surface(0f,0f,0f,2f,.6f,4.2f,teal,PAINT);surface(0f,.5f,-.3f,1.2f,.8f,1.4f,white,PAINT);box(0f,.7f,.42f,.8f,.4f,.03f,glass,GLASS);pop()
        }
        for(z in floatArrayOf(10.1f,23.9f))for(i in 0..14)surface(57.5f+i,.1f,z,.8f,.16f,.2f,if(i%2==0)paintWhite else yellow,PAINT)
        val pieces=arenaLayouts[a.map]
        for(i in a.state.indices)if(a.state[i]!=2) {
            push();translate(a.px[i],.06f+a.py[i],a.pz[i])
            if(a.state[i]==1) { rotate(a.phase*70f+i*23f,0f,1f,0f);rotate(sin(a.phase+i)*15f,0f,0f,1f) }
            arenaProp(pieces[i].prop,i,a.frost[i]);pop()
            if(!shadowPass && a.state[i]==0 && pieces[i].needed>a.level)noCast {
                // Weight dots are pictograms, so a child can see why a bigger vortex is needed.
                for(j in 0 until pieces[i].needed)blob(a.px[i]+(j-(pieces[i].needed-1)*.5f)*.16f,.95f+a.py[i],a.pz[i],.09f,.09f,.09f,yellow,EMISSIVE)
            }
        }
        // Contained inventory remains visible as miniatures; identities are never destroyed.
        val held=a.state.indices.filter { a.state[it]==2 }.take(12)
        if(a.strength>.1f)for((n,i) in held.withIndex()) {
            val angle=a.phase*1.8f+n*2.4f;val radius=(.45f+n%3*.2f)*a.scale
            val cx=if(a.buddy && a.puller[i]==1)a.buddyX else a.x;val cz=if(a.buddy && a.puller[i]==1)a.buddyZ else a.z
            push();translate(cx+cos(angle)*radius,1.1f+n%6*.5f*a.scale,cz+sin(angle)*radius);rotate(angle*40f,0f,1f,0f);scale(.22f,.22f,.22f);arenaProp(pieces[i].prop,i,if(a.kind==2)1f else 0f);pop()
        }
        if(a.buddy) {
            val helper=WindFrame(a.running,a.strength,a.buddyX,a.buddyZ,a.phase+1f)
            tornado(f.copy(wind=helper),a.scale*.8f,a.kind,.06f)
        }
        tornado(f.copy(wind=w),a.scale,a.kind,.06f)
    }
    private fun arenaProp(prop:com.dromadi.tinysim.TornadoArena.Prop,id:Int,frost:Float) {
        val color=if(frost>.1f)rgba(142,212,235) else when(id%4) { 0->orange;1->teal;2->yellow;else->rgba(191,105,87) }
        when(prop) {
            ArenaProp.BALL->{ blob(0f,.34f,0f,.66f,.66f,.66f,color,PAINT);disk(0f,.36f,0f,.67f,.67f,paintWhite,PAINT,.07f) }
            ArenaProp.CONE->{surface(0f,.05f,0f,.6f,.1f,.6f,rubber,RUBBER);shape(cone,0f,.1f,0f,.5f,.7f,.5f,color,PAINT);disk(0f,.4f,0f,.3f,.3f,paintWhite,PAINT,.08f)}
            ArenaProp.CRATE->{
                surface(0f,.38f,0f,.78f,.76f,.78f,color,WOOD)
                for(x in floatArrayOf(-.34f,.34f))for(z in floatArrayOf(-.4f,.4f))box(x,.38f,z,.09f,.76f,.04f,paintWhite,WOOD)
                for(y in floatArrayOf(.1f,.67f)) { box(0f,y,.4f,.78f,.08f,.04f,paintWhite,WOOD);box(0f,y,-.4f,.78f,.08f,.04f,paintWhite,WOOD) }
            }
            ArenaProp.BARREL->{shape(cylinder,0f,.44f,0f,.68f,.88f,.68f,color,PAINT);for(y in floatArrayOf(.12f,.73f))disk(0f,y,0f,.71f,.71f,metal,METAL,.07f);disk(0f,.89f,0f,.64f,.64f,darkSteel,METAL);disk(.16f,.9f,0f,.1f,.1f,chrome,METAL)}
            ArenaProp.BRICK->{surface(0f,.35f,0f,.84f,.7f,.9f,color,CONCRETE);for(x in floatArrayOf(-.2f,.2f))disk(x,.705f,0f,.19f,.3f,ink,MATTE)}
            ArenaProp.ROOF->{ for(side in floatArrayOf(-1f,1f)) { push();translate(0f,.25f,side*.3f);rotate(side*28f,1f,0f,0f);surface(0f,0f,0f,1.8f,.14f,.8f,color,ROOF);pop() } }
            ArenaProp.BUOY->{blob(0f,.3f,0f,.6f,.6f,.6f,color,PAINT);disk(0f,.3f,0f,.63f,.63f,paintWhite,PAINT,.08f);rod3(0f,.5f,0f,0f,1.05f,0f,.035f,metal,METAL);box(.14f,.94f,0f,.28f,.2f,.035f,paintWhite,MATTE)}
            ArenaProp.CONTAINER->{surface(0f,.35f,0f,.9f,.7f,.9f,color,PAINT);for(j in 0..5)for(side in floatArrayOf(-1f,1f))box(-.35f+j*.14f,.35f,side*.46f,.035f,.6f,.035f,paintWhite,PAINT)}
        }
    }
    private fun windGarden(f:SceneFrame) {
        // An invitation to play: windsock, spinning flower and curved ring around the sand patch.
        tree(18f,-4.8f,1.4f);tree(29f,-4.7f,1.3f)
        val x=21f;val z=-4.6f
        rod3(x,0f,z,x,3.1f,z,.07f,metal,METAL)
        push();translate(x,3.1f,z);rotate(12f+f.wind.strength*12f*sin(f.wind.phase),0f,0f,1f)
        for(i in 0..5) { val size=.7f-i*.07f;box(.18f+i*.27f,0f,0f,.28f,size,size,if(i%2==0)orange else paintWhite,PAINT) };pop()
        rod3(27f,0f,-4.6f,27f,2.6f,-4.6f,.055f,teal,PAINT)
        push();translate(27f,2.6f,-4.6f);rotate(f.seconds*12f+f.wind.phase*50f*f.wind.strength,0f,0f,1f)
        for(i in 0..3) { rotate(90f,0f,0f,1f);box(.3f,0f,0f,.6f,.2f,.06f,if(i%2==0)yellow else teal,PAINT) };blob(0f,0f,.08f,.19f,.19f,.12f,orange,PAINT);pop()
        noCast { for(i in 0..23) { val a=i*PI.toFloat()/12f;disk(24f+cos(a)*3.8f,.085f,sin(a)*3.8f,.22f,.22f,if(i%2==0)teal else yellow,DECAL) } }
    }
    private fun tornado(f:SceneFrame,size:Float=1f,kind:Int=0,base:Float?=null) {
        val w=f.wind;if(w.strength<=.001f || shadowPass)return
        val ground=base ?: Terrain.height(f.earth,f.sand,w.x,w.z)
        noCast {
            GL.glDepthMask(false);GL.glDisable(GL.GL_CULL_FACE)
            push();translate(w.x,ground,w.z);scale(w.strength*size,w.strength*size,w.strength*size);rotate(w.phase*180f/PI.toFloat(),0f,1f,0f)
            val core=when(kind) { 1->rgba(213,161,87,.4f*w.strength);2->rgba(118,200,231,.32f*w.strength);else->rgba(198,187,157,.24f*w.strength) }
            val ribbon=when(kind) { 1->rgba(250,213,141,.55f*w.strength);2->rgba(218,247,255,.55f*w.strength);else->rgba(242,233,209,.47f*w.strength) }
            draw(funnelMesh,core,MATTE)
            for(i in 0..2) { rotate(120f,0f,1f,0f);draw(ribbonMesh,ribbon,MATTE) }
            for(i in 0..47) {
                val t=((i/48f+w.phase*.12f)%1f);val a=i*2.4f+w.phase*2f+t*10f;val radius=.4f+2.2f*t*t
                val px=cos(a)*radius;val pz=sin(a)*radius
                if(i%3==0) { push();translate(px,t*6.5f,pz);rotate(a*60f,1f,1f,0f);if(kind==2) {
                    for(j in 0..2) { rotate(60f,0f,1f,0f);box(0f,0f,0f,.35f,.035f,.035f,paintWhite,MATTE) }
                } else box(0f,0f,0f,.16f,.04f,.3f,if(kind==1)sandColor else if(i%2==0)leafLight else yellowDark,MATTE);pop() }
                else blob(px,t*6.5f,pz,.08f,.08f,.08f,sandColor,MATTE)
            }
            for(i in 0..9) { val a=i*PI.toFloat()/5f;blob(cos(a)*1.65f,6.5f+sin(a*3f)*.16f,sin(a)*1.65f,2.1f,.8f,1.6f,rgba(248,246,227,.7f*w.strength),CLOUD) }
            pop()
            // A short curved grain trail connects the actual last source cell to the funnel.
            if(w.trailAge<.5f)for(i in 0..11) {
                val t=(i/12f+w.trailAge*1.5f).coerceIn(0f,1f)
                blob(w.trailX+(w.x-w.trailX)*t,ground+.12f+sin(t*PI.toFloat())*1.7f,w.trailZ+(w.z-w.trailZ)*t,.065f,.065f,.065f,sandColor,MATTE)
            }
            GL.glEnable(GL.GL_CULL_FACE);GL.glDepthMask(true)
        }
    }
    private fun tree(x:Float,z:Float,s:Float) {
        val ground=if(x in WorldMap.MIN_X..WorldMap.MAX_X && z in WorldMap.MIN_Z..WorldMap.MAX_Z)0f else GRASS_Y
        val seed=abs((x*12.9898f+z*78.233f)*43.7585f)
        fun rnd(k:Int)=((seed*(k+1)*.6180339f)%1f)
        push();translate(x,ground,z)
        val bend=sceneWind.strength*(1f-hypot(x-sceneWind.x,z-sceneWind.z)/14f).coerceIn(0f,1f)
        rotate(bend*(8f+2f*sin(sceneWind.phase)),0f,0f,1f)
        scale(s,s*(.9f+rnd(1)*.35f),s);rotate(rnd(2)*360f,0f,1f,0f)
        noCast { disk(0f,.01f,0f,2.0f,2.0f,contact,DECAL) }
        if(rnd(3)<.3f) {
            shape(cylinder,0f,.5f,0f,.2f,1.0f,.2f,bark,BARK)
            for(k in 0..3) {
                val w=1.9f-k*.4f
                shape(cone,0f,.7f+k*.6f,0f,w,1.1f,w,if(k%2==0)pine else leaf,FOLIAGE)
            }
        } else {
            shape(cylinder,0f,.8f,0f,.2f,1.6f,.2f,bark,BARK)
            rod(0f,1.3f,.45f,1.95f,.07f,bark,BARK)
            rod(0f,1.4f,-.4f,2.05f,.06f,bark,BARK)
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
                disk(WorldMap.worldX(f.excavatorX),support(f,WorldMap.worldX(f.excavatorX),f.excavatorZ)+.075f,f.excavatorZ,3.6f*g,2.3f*g,contact,DECAL)
                disk(WorldCamera.x(f.truckX),support(f,WorldMap.worldX(f.truckX),f.truckZ)+.075f,f.truckZ,4.3f*g,2.2f*g,contact,DECAL)
                disk(WorldCamera.x(f.dozerX),support(f,WorldMap.worldX(f.dozerX),f.dozerZ)+.075f,f.dozerZ,4.0f*g,2.4f*g,contact,DECAL)
            }
            GL.glDepthMask(true)
        }
    }
    private fun sand(f:SceneFrame) {
        // Four pictured work bays become a raised gravel road as the blade compacts sand.
        for(section in 0..3) {
            val x=17f+section*2f
            if(f.road[section]>0) {
                box(x,.15f,8f,1.96f,.16f,1.95f,rgba(104,102,100),ASPHALT)
                for(k in 0..4)box(x-.8f+k*.4f,.24f,8f,.17f,.02f,.09f,paintWhite,MATTE)
            } else noCast {
                box(x,.085f,7.02f,1.9f,.025f,.06f,orange,DECAL)
                box(x,.085f,8.98f,1.9f,.025f,.06f,orange,DECAL)
                box(x-.97f,.085f,8f,.05f,.025f,2f,orange,DECAL)
                box(x+.97f,.085f,8f,.05f,.025f,2f,orange,DECAL)
            }
        }
        if(f.source>0)noCast { disk(WorldCamera.x(365f),.08f,0f,3.4f,2.85f,sandWet,SAND) }
        if(f.source>0)mound(WorldCamera.x(365f),.07f,0f,3.1f,.25f+f.source/30f,2.6f,sandColor)
    }
    private fun house(f:SceneFrame) {
        val ready=f.foundationProgress>=4
        if(f.houseStage==0) {
            noCast { for(x in floatArrayOf(33.5f,34.5f,35.5f,36.5f)) {
                val cell=WorldMap.cell(x,6.5f)
                disk(x,Terrain.height(f.earth,f.sand,x,6.5f)+.018f,6.5f,.34f,.34f,if(f.earth[cell]<4)teal else orange,DECAL)
            } }
            noCast {
                for(x in floatArrayOf(33f,39f))box(x,.12f,7f,.08f,.08f,4f,if(ready)teal else orange,PAINT)
                for(z in floatArrayOf(5f,9f))box(36f,.12f,z,6f,.08f,.08f,if(ready)teal else orange,PAINT)
            }
            for(i in 0 until f.houseFill)surface(34.2f+(i%4)*1.15f,.2f,6f+(i/4)*1.2f,.8f,.25f,.8f,stone,CONCRETE)
        }
        if(f.houseStage>=1) {
            surface(36f,.18f,7f,6.2f,.32f,4.2f,stone,CONCRETE)
            for(x in floatArrayOf(33.2f,38.8f))panel(x,.4f,7f,.22f,.45f,4f,metal)
        }
        if(f.houseStage>=2) {
            // Separate wall panels leave actual door/window openings.
            surface(36f,1.65f,5.08f,5.8f,2.6f,.22f,rgba(255,222,158),PLASTER)
            for(x in floatArrayOf(33.1f,38.9f))surface(x,1.65f,7f,.22f,2.6f,4f,rgba(247,203,126),PLASTER)
            surface(34.1f,1.65f,8.92f,1.8f,2.6f,.22f,rgba(255,222,158),PLASTER)
            surface(37.8f,1.65f,8.92f,2f,2.6f,.22f,rgba(255,222,158),PLASTER)
            surface(36f,2.75f,8.92f,1.8f,.4f,.22f,rgba(255,222,158),PLASTER)
            panel(34.1f,1.6f,9.06f,1.1f,1f,.08f,tealDark)
            box(34.1f,1.6f,9.12f,.9f,.8f,.04f,glass,GLASS)
            box(34.1f,1.6f,9.16f,.06f,.86f,.04f,paintWhite,PAINT)
        }
        if(f.houseStage>=3) {
            shape(gable,36f,2.95f,7f,5.8f,1.1f,3.9f,rgba(247,203,126),PLASTER)
            surface(37f,3.95f,6.3f,.55f,1.1f,.55f,rgba(184,105,77),PLASTER)
            surface(37f,4.55f,6.3f,.7f,.12f,.7f,stone,CONCRETE)
            for(side in floatArrayOf(-1f,1f)) {
                push();translate(36f,3.5f,7f+side*1.05f);rotate(side*27f,1f,0f,0f)
                surface(0f,0f,0f,6.6f,.18f,2.5f,rgba(209,91,60),ROOF)
                                pop()
            }
            panel(36f,1.4f,9.02f,1.6f,2.1f,.12f,teal)
            blob(36.5f,1.35f,9.13f,.09f,.09f,.09f,metal,METAL)
            surface(36f,.25f,9.65f,2f,.22f,1.1f,stone,CONCRETE)
            for(x in floatArrayOf(32.6f,39.4f)) { shape(cylinder,x,.4f,9.3f,.7f,.6f,.7f,orange,PAINT);blob(x,.95f,9.3f,1f,1.2f,1f,grass,GRASS) }
        }
        if(f.buildClock>0f)for(i in 0..5)blob(33f+i*1.1f,1f+sin(f.seconds*2f+i)*.5f,9.3f,.08f,.08f,.08f,white,EMISSIVE)
    }
    private fun excavator(f:SceneFrame) {
        push();translate(WorldMap.worldX(f.excavatorX),support(f,WorldMap.worldX(f.excavatorX),f.excavatorZ),f.excavatorZ);rotate(f.excavatorHeading,0f,1f,0f);translate(-WorldMap.worldX(200f),0f,0f)
        beginTexFrame()
        val bob=bob(f,0);translate(0f,bob,0f)
        push();translate(WorldMap.worldX(200f),0f,0f);windSway(f,0)
        push();translate(0f,-bob,0f);tracks(2.75f,f.excavatorTravel);wheelDust(f,0,2.75f);pop()
        // Slewing ring and upper house.
        shape(cylinder,0f,.82f,0f,1.4f,.14f,1.4f,darkSteel,METAL)
        panel(0f,1.06f,0f,2.5f,.5f,1.65f,yellow)
        cab(-.6f,1.30f,2.42f,1.53f,2.31f,1.2f,1.4f,yellow) { driver(-.74f,1.62f,0f,wave(f,0),f.seconds) }
        panel(-.6f,2.46f,0f,1.32f,.1f,1.52f,yellowDark)
        exhaust(.5f,1.92f,-.5f,f,0)
        // Counterweight, engine grille, exhaust and beacon.
        panel(.86f,1.38f,0f,.78f,.5f,1.62f,yellowDark)
        grille(.85f,1.38f,.826f,.62f,.31f)
        safetyPanel(.04f,1.08f,.844f,1.6f,.14f)
        panel(.84f,1.65f,0f,.7f,.04f,1.4f,yellow)
        for(z in floatArrayOf(-.56f,.56f)) {
            rod3(.36f,1.58f,z,.36f,1.94f,z,.022f,darkSteel,METAL)
            rod3(.36f,1.94f,z,1.12f,1.94f,z,.022f,darkSteel,METAL)
            rod3(1.12f,1.94f,z,1.12f,1.58f,z,.022f,darkSteel,METAL)
        }
        for(x in floatArrayOf(-1f,1f))wheelDisc(x,1.04f,.85f,.055f,.03f,chrome,METAL)
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
        for(z in floatArrayOf(-.265f,.265f)) {
            rod3(px,py,z,jx,jy,z,.035f,yellowDark,METAL)
            hose(px,py+.18f,z,jx,jy+.2f,z,.26f)
            hose(jx,jy+.16f,z,bx,by+.23f,z,.18f)
        }
        // Hydraulic cylinders: painted barrel with a polished rod.
        hydraulic(px-.14f,py+.27f,jx-.12f,jy-.31f,.11f)
        hydraulic(jx+.08f,jy-.12f,bx-.08f,by+.48f,.08f)
        wheelDisc(px,py,0f,.23f,.63f,darkSteel,METAL)
        wheelDisc(jx,jy,0f,.21f,.6f,darkSteel,METAL)
        wheelDisc(bx,by,0f,.12f,.54f,darkSteel,METAL)
        for(z in floatArrayOf(-.33f,.33f)) {
            wheelDisc(px,py,z,.11f,.035f,chrome,METAL)
            wheelDisc(jx,jy,z,.09f,.035f,chrome,METAL)
            wheelDisc(bx,by,z*.86f,.06f,.035f,chrome,METAL)
        }
        push();translate(bx,by-.22f,0f);rotate(if(f.bucket>0)10f else -12f,0f,0f,1f)
        box(0f,-.18f,0f,.88f,.1f,.92f,darkSteel,METAL)
        box(-.36f,.05f,0f,.11f,.5f,.92f,darkSteel,METAL)
        box(0f,.0f,-.43f,.83f,.43f,.07f,darkSteel,METAL);box(0f,.0f,.43f,.83f,.43f,.07f,darkSteel,METAL)
        for(i in 0..3) {
            push();translate(.46f,-.18f,-.32f+i*.21f);rotate(-90f,0f,0f,1f)
            shape(cone,0f,0f,0f,.12f,.26f,.13f,metal,METAL);pop()
        }
        for(z in floatArrayOf(-.48f,.48f)) {
            box(-.24f,.04f,z,.065f,.39f,.045f,metal,METAL)
            wheelDisc(-.25f,.12f,z,.035f,.04f,chrome,METAL)
        }
        if(f.bucket>0)mound(0f,-.13f,0f,.74f,.32f,.78f,sandColor)
        pop();endTexFrame();pop()
    }
    private fun hydraulic(ax:Float,ay:Float,bx:Float,by:Float,radius:Float) {
        val mx=ax+(bx-ax)*.55f;val my=ay+(by-ay)*.55f
        for(z in floatArrayOf(-.30f,.30f)) {
            rod3(ax,ay,z,mx,my,z,radius,yellowDark,PAINT)
            rod3(mx,my,z,bx,by,z,radius*.52f,chrome,METAL)
            rod3(mx-(bx-ax)*.025f,my-(by-ay)*.025f,z,mx+(bx-ax)*.025f,my+(by-ay)*.025f,z,radius*1.18f,darkSteel,METAL)
        }
    }
    private fun truck(f:SceneFrame) {
        val tx=WorldCamera.x(f.truckX)
        push();translate(tx,support(f,tx,f.truckZ),f.truckZ);rotate(f.truckHeading,0f,1f,0f);beginTexFrame()
        val spin=-f.truckTravel/.47f*180f/PI.toFloat()
        for(x in floatArrayOf(-1.25f,1.24f))for(z in floatArrayOf(-.91f,.91f))wheel(x,.48f,z,spin)
        wheelDust(f,1,3.85f)
        // Body rides on the suspension: it settles after stopping and trembles while idling.
        translate(0f,bob(f,1),0f);windSway(f,1)
        box(0f,.67f,0f,3.85f,.25f,1.25f,darkSteel,METAL)
        for(x in floatArrayOf(-1.25f,1.24f)) {
            rod3(x,.48f,-.83f,x,.48f,.83f,.09f,darkSteel,METAL)
            for(z in floatArrayOf(-.59f,.59f))for(i in 0..2)box(x,.58f+i*.045f,z,.8f-i*.12f,.026f,.08f,metal,METAL)
        }
        for(z in floatArrayOf(-.94f,.94f)) {
            panel(-.3f,.72f,z,.68f,.24f,.24f,tealDark)
            box(-1.72f,.42f,z,.06f,.43f,.3f,rubber,RUBBER)
            box(-.3f,.85f,z,.56f,.035f,.26f,chrome,METAL)
        }
        // Mudguards and fuel tank.
        for(x in floatArrayOf(-1.25f,1.24f))for(z in floatArrayOf(-.92f,.92f))panel(x,1.0f,z,1.08f,.07f,.36f,ink)
        push();translate(.15f,.62f,-.66f);rotate(90f,0f,0f,1f);shape(cylinder,0f,0f,0f,.34f,.7f,.34f,chrome,METAL);pop()
        cab(1.13f,.83f,2.21f,1.42f,2.12f,1.26f,1.62f,orange) { driver(1.02f,1.4f,.32f,wave(f,1),f.seconds) }
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
        exhaust(.42f,2.72f,-.72f,f,1)
        // Tip ram follows the changing front of the bed, with a thicker barrel at the chassis.
        val tip=-f.bedAngle*PI.toFloat()/180f
        val ramX=-1.66f+cos(tip)*2.05f;val ramY=.91f+sin(tip)*2.05f
        rod3(-.5f,.72f,0f,ramX,ramY,0f,.075f,chrome,METAL)
        rod3(-.5f,.72f,0f,(-.5f+ramX)*.5f,(.72f+ramY)*.5f,0f,.13f,darkSteel,METAL)
        // Bed hinge at the rear; positive world-Z rotation lifts the front.
        push();translate(-1.66f,.91f,0f);rotate(-f.bedAngle,0f,0f,1f)
        panel(1.05f,.07f,0f,2.7f,.14f,1.88f,teal)
        for(z in floatArrayOf(-.9f,.9f)) {
            panel(1.05f,.48f,z,2.7f,.8f,.12f,teal)
            for(i in 0..4)box(-.02f+i*.53f,.48f,z*1.08f,.055f,.66f,.05f,tealDark,PAINT)
            panel(1.05f,.86f,z*1.03f,2.8f,.085f,.13f,tealDark)
            if(z>0) safetyPanel(1.05f,.22f,z*1.11f,1.4f,.14f)
            for(i in 0..4)wheelDisc(-.02f+i*.53f,.7f,z*1.11f,.025f,.02f,chrome,METAL)
        }
        panel(2.35f,.62f,0f,.12f,1.08f,1.88f,teal)
        push();translate(-.3f,.12f,0f);rotate(if(f.tipping)-65f else 0f,0f,0f,1f)
        panel(0f,.36f,0f,.12f,.72f,1.88f,teal)
        for(z in floatArrayOf(-.7f,.7f))box(-.075f,.36f,z,.03f,.58f,.06f,tealDark,PAINT)
        for(z in floatArrayOf(-.86f,.86f))wheelDisc(0f,.76f,z,.07f,.15f,metal,METAL)
        pop()
        if(f.load>0)mound(1.05f,.13f,0f,2.5f,.12f+f.load*.08f,1.7f,sandColor)
        for((n,id) in f.recovery.state.indices.filter { f.recovery.state[it]==4 }.withIndex()) {
            push();translate(.3f+(n%4)*.47f,.16f+(n/4)*.35f,0f);scale(.23f,.23f,.23f);recoveryPart(id);pop()
        }
        pop()
        // Tail lights.
        for(z in floatArrayOf(-.7f,.7f))box(-1.95f,.72f,z,.04f,.1f,.2f,rgba(220,40,30),EMISSIVE)
        endTexFrame();pop()
    }
    private fun bulldozer(f:SceneFrame) {
        val dx=WorldCamera.x(f.dozerX)
        push();translate(dx,support(f,dx,f.dozerZ),f.dozerZ);rotate(f.dozerHeading,0f,1f,0f);beginTexFrame()
        tracks(2.7f,f.dozerTravel);wheelDust(f,2,2.7f)
        translate(0f,bob(f,2),0f);windSway(f,2)
        panel(0f,.98f,0f,2.7f,.55f,1.55f,yellow)
        cab(-.5f,1.24f,2.36f,1.51f,2.21f,1.2f,1.4f,yellow) { driver(-.64f,1.56f,0f,wave(f,2),f.seconds) }
        panel(-.5f,2.42f,0f,1.36f,.1f,1.52f,ink)
        // Engine hood with grille vents, exhaust and beacon.
        panel(.78f,1.38f,0f,.95f,.36f,1.3f,yellowDark)
        for(i in 0..4)box(.45f+i*.16f,1.57f,0f,.06f,.02f,1.0f,ink,MATTE)
        shape(cylinder,.7f,1.86f,-.42f,.11f,.7f,.11f,darkSteel,METAL)
        exhaust(.7f,2.23f,-.42f,f,2)
        beaconLight(-.5f,2.54f,0f,f.seconds+.35f)
        grille(.78f,1.35f,.67f,.84f,.26f)
        safetyPanel(.15f,1.02f,.79f,1.9f,.14f)
        for(z in floatArrayOf(-.6f,.6f)) {
            rod3(-1.23f,1.23f,z,-1.23f,1.63f,z,.024f,chrome,METAL)
            rod3(-1.23f,1.63f,z,-.99f,1.63f,z,.024f,chrome,METAL)
        }
        // Rear ripper.
        box(-1.45f,.8f,0f,.18f,.18f,1.0f,darkSteel,METAL)
        for(z in floatArrayOf(-.43f,0f,.43f)) {
            box(-1.6f,.45f,z,.12f,.6f,.14f,darkSteel,METAL)
            push();translate(-1.63f,.18f,z);rotate(115f,0f,0f,1f);shape(cone,0f,0f,0f,.15f,.26f,.17f,metal,METAL);pop()
        }
        push();translate(0f,if(f.bladeDown)0f else .55f,0f)
        for(z in floatArrayOf(-.57f,.57f))box(1.21f,.5f,z,1.05f,.14f,.15f,darkSteel,METAL)
        for(z in floatArrayOf(-.4f,.4f)) {
            rod3(.95f,1.25f,z,1.7f,.8f,z,.07f,chrome,METAL)
            rod3(.95f,1.25f,z,1.28f,1.05f,z,.105f,yellowDark,PAINT)
            hose(.8f,1.32f,z,1.28f,1.05f,z,.19f)
        }
        panel(1.83f,.53f,0f,.16f,.91f,2.15f,yellow)
        box(1.95f,.13f,0f,.16f,.1f,2.25f,metal,METAL)
        for(z in floatArrayOf(-.84f,-.42f,0f,.42f,.84f)) {
            shape(cylinder,2.037f,.16f,z,.035f,.055f,.055f,darkSteel,METAL)
            box(1.735f,.53f,z,.045f,.7f,.065f,yellowDark,PAINT)
        }
        for(z in floatArrayOf(-1.02f,1.02f))box(1.79f,.5f,z,.35f,.84f,.08f,yellowDark,PAINT)
        if(f.bladeDown && f.sand[WorldMap.cell(dx+cos(f.dozerHeading*PI.toFloat()/180)*1.8f,f.dozerZ-sin(f.dozerHeading*PI.toFloat()/180)*1.8f)]>0)mound(2.1f,.05f,0f,.5f,.18f,1.6f,sandColor)
        pop();endTexFrame();pop()
    }
    /** Sand-coloured piles on every cell holding loose sand (the heightfield alone reads as soil). */
    private fun sandCaps(f:SceneFrame) {
        for(i in f.sand.indices) {
            val n=f.sand[i];if(n<=0 || WorldMap.river(i))continue
            val x=WorldMap.cellX(i);val z=WorldMap.cellZ(i)
            val base=.07f+(f.earth[i]-Terrain.INITIAL_EARTH)*.22f
            val h=n.coerceAtMost(24)*.13f+.06f
            mound(x,base,z,1.3f+min(n,8)*.03f,h,1.3f+min(n,8)*.03f,sandColor)
        }
    }
    /** Animated river surface between the banks; drawn last because it is translucent. */
    private fun water(f:SceneFrame) {
        if(shadowPass)return
        GL.glDepthMask(false)
        push();translate((WorldMap.RIVER_MIN_X+WorldMap.RIVER_MAX_X)/2,-.38f,17f);scale(WorldMap.RIVER_MAX_X-WorldMap.RIVER_MIN_X+1.2f,1f,48.1f)
        draw(plane,rgba(40,118,128,.86f),WATER);pop()
        GL.glDepthMask(true)
    }
    /** River banks and the east bank: quarry flag, picnic garden, trees and flowers. */
    private fun eastBank(f:SceneFrame) {
        noCast {
            for(i in 0..21) {
                val z=-6.5f+i+((i*37)%10)/20f
                if(abs(z-WorldMap.BRIDGE_Z)<2.2f)continue
                for(x in floatArrayOf(WorldMap.RIVER_MIN_X-.25f,WorldMap.RIVER_MAX_X+.25f)) {
                    blob(x+sin(i*2.1f)*.2f,-.05f,z,.45f+(i%3)*.12f,.3f,.5f,stone,CONCRETE)
                    for(k in 0..2)shape(cone,x+(if(x<52f)-.55f else .55f)+k*.12f,0f,z+.3f+k*.15f,.08f,.7f+(k%2)*.3f,.08f,leafLight,FOLIAGE)
                }
            }
        }
        // Quarry marker beside the sand waiting across the river.
        box(66.2f,1.2f,2.6f,.08f,2.4f,.08f,metal,METAL)
        box(66.65f,2.15f,2.6f,.8f,.5f,.04f,orange,PAINT)
        // Picnic garden.
        for(t in 0..1) {
            val x=60f+t*5f;val z=9.5f
            surface(x,.75f,z,1.8f,.08f,.8f,bark,WOOD)
            for(dz in floatArrayOf(-.7f,.7f))surface(x,.45f,z+dz,1.8f,.06f,.3f,bark,WOOD)
            for(dx in floatArrayOf(-.7f,.7f))box(x+dx,.4f,z,.08f,.7f,.08f,bark,WOOD)
        }
        noCast {
            for(i in 0..23) {
                val x=57f+((i*29)%150)/10f;val z=7.6f+((i*17)%35)/10f
                val petal=when(i%4) { 0->rgba(240,90,110);1->rgba(255,214,70);2->rgba(250,250,245);else->rgba(170,120,230) }
                shape(cylinder,x,.12f,z,.03f,.24f,.03f,leaf,FOLIAGE);blob(x,.28f,z,.13f,.08f,.13f,petal,MATTE)
            }
        }
        for(x in floatArrayOf(58f,63f,68f,72f)) { tree(x,-5.6f,1.05f);tree(x+1.5f,13.5f,1.2f) }
        tree(72.5f,6f,1.3f)
        for(z in floatArrayOf(1.2f,7.8f))trafficCone(56.2f,z)
    }
    /** West-bank material yard: frame turns teal once all eight units have arrived. */
    private fun bridgeYard(f:SceneFrame) {
        val full=f.bridgeFill>=8
        if(f.bridgeStage<3)noCast {
            for(x in floatArrayOf(44f,48f))box(x,.12f,4.5f,.08f,.08f,5f,if(full)teal else orange,PAINT)
            for(z in floatArrayOf(2f,7f))box(46f,.12f,z,4f,.08f,.08f,if(full)teal else orange,PAINT)
        }
        val stock=if(f.bridgeStage==0 && f.bridgeClock==0f)f.bridgeFill else max(0,8-(f.bridgeStage+1)*3)
        for(i in 0 until stock)surface(45.2f+(i%2)*1.6f,.15f+(i/2)*.17f,4.5f,1.2f,.15f,2.6f,stone,CONCRETE)
    }
    /** Bridge stages: 1 abutments and piers, 2 steel girders, 3 asphalt deck with railings and lamps. */
    private fun bridge(f:SceneFrame) {
        val z=WorldMap.BRIDGE_Z
        if(f.bridgeStage>=1) {
            for(x in floatArrayOf(49.1f,54.9f))surface(x,-.3f,z,1.1f,1.25f,3.5f,stone,CONCRETE)
            for(x in floatArrayOf(51.3f,52.7f)) {
                surface(x,-.42f,z,.55f,.95f,2.6f,stone,CONCRETE)
                surface(x,.02f,z,.75f,.14f,3.1f,stone,CONCRETE)
            }
        }
        if(f.bridgeStage>=2) {
            for(dz in floatArrayOf(-1.05f,1.05f))surface(52f,.13f,z+dz,6.7f,.2f,.28f,orange,PAINT)
            for(i in 0..6)box(48.9f+i*1.03f,.13f,z,.1f,.12f,2.2f,darkSteel,METAL)
        }
        if(f.bridgeStage>=3) {
            surface(52f,.27f,z,6.8f,.1f,3.3f,rgba(104,102,100),ASPHALT)
            noCast { for(i in 0..5)box(49.3f+i*1.08f,.325f,z,.5f,.008f,.08f,paintWhite,MATTE) }
            for(side in floatArrayOf(-1f,1f)) {
                val rz=z+side*1.62f
                surface(52f,.37f,rz,6.8f,.1f,.16f,stone,CONCRETE)
                for(i in 0..8)box(48.8f+i*.85f,.64f,rz,.07f,.5f,.07f,paintWhite,PAINT)
                box(52f,.88f,rz,6.9f,.08f,.08f,orange,PAINT);box(52f,.62f,rz,6.9f,.05f,.05f,paintWhite,PAINT)
                for(x in floatArrayOf(48.6f,55.4f)) {
                    shape(cylinder,x,1.2f,rz+side*.15f,.09f,2.2f,.09f,darkSteel,METAL)
                    blob(x,2.35f,rz+side*.15f,.22f,.22f,.22f,lamp,EMISSIVE)
                }
            }
        }
    }
    /**
     * The child's mobile crane, posed from the game state: carrier, slewing turret, two-stage boom,
     * cable and clamshell grab. While a bridge stage builds, the hook follows the lift-swing-lower
     * timeline from the yard to the span instead.
     */
    /** Original pipe model: orange wall, dark bore, bright coupling rings and steel lift straps. */
    private fun serviceTruck(f:SceneFrame,wreck:Boolean) {
        val a=f.work;val v=if(wreck)5 else 4
        val x=WorldMap.worldX(if(wreck)a.wreckX else a.mixerX);val z=if(wreck)a.wreckZ else a.mixerZ
        val heading=if(wreck)a.wreckHeading else a.mixerHeading
        val travel=if(wreck)a.wreckTravel else a.mixerTravel
        val paint=if(wreck)orange else teal
        push();translate(x,support(f,x,z),z);rotate(heading,0f,1f,0f);beginTexFrame()
        wheelDust(f,v,4.4f)
        for(wx in floatArrayOf(-1.55f,-.65f,1.55f))for(wz in floatArrayOf(-.9f,.9f))wheel(wx,.48f,wz,-travel/.47f*180f/PI.toFloat())
        translate(0f,bob(f,v),0f);windSway(f,v)
        box(0f,.72f,0f,4.4f,.35f,1.65f,darkSteel,METAL)
        panel(0f,.98f,0f,4.15f,.24f,1.8f,paint)
        cab(1.4f,1.05f,2.55f,1.55f,2.42f,1.4f,1.75f,paint) { driver(1.35f,1.55f,.3f,wave(f,v),f.seconds) }
        panel(2.16f,.75f,0f,.22f,.26f,1.85f,paintWhite)
        for(zz in floatArrayOf(-.67f,.67f))box(2.29f,1.13f,zz,.045f,.18f,.28f,lamp,EMISSIVE)
        for(i in 0..3)box(2.3f,.94f,-.35f+i*.23f,.03f,.18f,.09f,ink,METAL)
        for(zz in floatArrayOf(-.96f,.96f))box(1.5f,2f,zz,.18f,.22f,.09f,chrome,METAL)
        box(-2.18f,.85f,0f,.18f,.2f,1.8f,paintWhite,PAINT)
        for(zz in floatArrayOf(-.65f,.65f))box(-2.29f,.91f,zz,.03f,.14f,.24f,rgba(215,54,45),EMISSIVE)
        exhaust(.65f,2.1f,-.75f,f,v);beaconLight(1.1f,2.7f,.1f,f.seconds)
        for(side in floatArrayOf(-1f,1f)) {
            panel(-1.12f,1.01f,side*.93f,1.95f,.12f,.3f,paint)
            box(-2.02f,.44f,side*.94f,.06f,.43f,.34f,rubber,RUBBER)
            panel(.35f,.78f,side*.91f,.52f,.3f,.22f,metal)
            box(.35f,.78f,side*1.035f,.035f,.28f,.025f,ink,METAL)
            if(side>0) safetyPanel(-.8f,1.08f,side*.94f,1.7f,.13f)
        }
        for(wx in floatArrayOf(-1.55f,-.65f,1.55f))rod3(wx,.48f,-.9f,wx,.48f,.9f,.07f,darkSteel,METAL)
        if(!wreck) {
            // Cradle, roller bearings, water tank and feed hopper remain outside the rotating drum.
            for(xx in floatArrayOf(-1.45f,-.05f)) {
                for(zz in floatArrayOf(-.52f,.52f)) {
                    rod3(xx,1.08f,zz,xx,1.36f,zz*.8f,.075f,tealDark,METAL)
                    wheelDisc(xx,1.29f,zz,.15f,.16f,darkSteel,METAL)
                }
            }
            shape(cylinder,.32f,1.9f,-.68f,.42f,.7f,.42f,paintWhite,PAINT)
            shape(cylinder,.32f,2.28f,-.68f,.15f,.08f,.15f,ink,METAL)
            panel(-1.98f,2.55f,0f,.52f,.12f,.65f,metal)
            for(side in floatArrayOf(-1f,1f))panel(-1.98f,2.68f,side*.32f,.6f,.28f,.045f,paintWhite)
            // Turning drum with a continuous orange helical stripe and two steel support rings.
            push();translate(-.65f,1.9f,0f);rotate(a.drum,1f,0f,0f)
            rod3(-1.2f,0f,0f,1f,0f,0f,.76f,paintWhite,PAINT)
            push();translate(-1.35f,0f,0f);rotate(90f,0f,0f,1f);shape(cone,0f,0f,0f,1.45f,.6f,1.45f,paintWhite,PAINT);pop()
            for(i in 0..27) {
                val t=i/28f;val t2=(i+1)/28f;val q=t*PI.toFloat()*4;val q2=t2*PI.toFloat()*4
                rod3(-1.1f+t*2f,cos(q)*.77f,sin(q)*.77f,-1.1f+t2*2f,cos(q2)*.77f,sin(q2)*.77f,.055f,orange,PAINT)
            }
            for(xx in floatArrayOf(-.8f,.6f))rod3(xx-.035f,0f,0f,xx+.035f,0f,0f,.8f,chrome,METAL)
            for(i in 0..7) {
                val q=i*PI.toFloat()/4
                rod3(.55f,cos(q)*.81f,sin(q)*.81f,.65f,cos(q)*.81f,sin(q)*.81f,.025f,darkSteel,METAL)
            }
            pop()
            for(yy in floatArrayOf(1.1f,1.35f,1.6f,1.85f,2.1f))box(-2f,yy,.9f,.38f,.045f,.07f,chrome,METAL)
            for(xx in floatArrayOf(-2.2f,-1.8f))box(xx,1.6f,.9f,.045f,1.1f,.045f,chrome,METAL)
            val pouring=a.pouring || f.recovery.pouring>=0
            val chute=if(pouring)1f else .3f
            rod3(-1.8f,1.75f,0f,-2.9f*chute,1.1f,0f,.17f,metal,METAL)
            if(pouring && !shadowPass)for(i in 0..7) {
                val t=(f.seconds*2+i*.125f)%1f
                blob(-2.85f,1.04f-t*.9f,.05f*sin(i*2f),.17f,.2f,.17f,stone,CONCRETE)
            }
        } else {
            panel(-.75f,1.2f,0f,2.3f,.45f,1.6f,orange)
            grille(-.85f,1.24f,.826f,1.15f,.25f)
            // Twin winch drums and cable guide behind the boom pivot.
            for(zz in floatArrayOf(-.45f,.45f)) {
                wheelDisc(-1.45f,1.65f,zz,.26f,.35f,darkSteel,METAL)
                for(i in 0..6)wheelDisc(-1.45f,1.65f,zz-.13f+i*.043f,.235f,.022f,metal,METAL)
            }
            val progress=if(a.swing>0)1f-a.swing/com.dromadi.tinysim.WorkFleet.SWING_SECONDS else 0f
            val unfold=if(a.swing>0)min(1f,min(progress*6f,(1-progress)*6f)) else 0f
            val tipX=-1.4f+unfold*4.6f;val tipY=4.5f+unfold*3f
            val bx=-1.2f+unfold*(4.6f+a.ball)
            val tier=(a.hits-if(a.swing>0f && progress>=.55f)1 else 0).coerceAtLeast(0)
            val by=2.55f+unfold*(-.25f+.25f*a.ball-tier*.8f)+2f*sin(unfold*PI.toFloat())
            for(zz in floatArrayOf(-.3f,.3f)) {
                rod3(-.7f,1.5f,zz,tipX,tipY,zz,.12f,yellow,PAINT)
                for(i in 0..5) {
                    val t=i/6f;val t2=(i+1)/6f
                    rod3(-.7f+(tipX+.7f)*t,1.5f+(tipY-1.5f)*t,zz,-.7f+(tipX+.7f)*t2,1.5f+(tipY-1.5f)*t2,-zz,.035f,yellowDark,METAL)
                }
            }
            for(i in 0..3) {
                val t=i/3f
                rod3(-.7f+(tipX+.7f)*t,1.5f+(tipY-1.5f)*t,-.3f,-.7f+(tipX+.7f)*t,1.5f+(tipY-1.5f)*t,.3f,.04f,yellowDark,METAL)
            }
            wheelDisc(tipX,tipY,0f,.17f,.17f,darkSteel,METAL)
            rod3(-1.45f,1.85f,0f,tipX,tipY,0f,.018f,metal,METAL)
            rod3(tipX,tipY,0f,bx,by,0f,.028f,darkSteel,METAL)
            blob(bx,by-.4f,0f,.9f,.9f,.9f,darkSteel,METAL)
            rod3(bx,by,0f,bx,by+.15f,0f,.1f,chrome,METAL)
        }
        endTexFrame();pop()
    }
    private fun workYard(f:SceneFrame) {
        val a=f.work;val z=com.dromadi.tinysim.WorkFleet.YARD_Z
        for(i in 0..7) {
            val x=18.5f+(i%4);val zz=z-.5f+(i/4)
            if(i<a.pad)surface(x,.13f,zz,.98f,.18f,.98f,stone,CONCRETE)
            else { box(x,.09f,zz- .47f,.96f,.05f,.05f,orange,PAINT);box(x-.47f,.09f,zz,.05f,.05f,.96f,orange,PAINT) }
        }
        for(x in floatArrayOf(17.7f,22.3f))for(zz in floatArrayOf(z-1.4f,z+1.4f))trafficCone(x,zz)
        // Nine original training bricks become nine rubble props; no soil is manufactured.
        for(level in 0 until 3-a.hits)for(i in 0..2) {
            val x=33f+i
            surface(x,.45f+level*.9f,z,.94f,.84f,.85f,if(level%2==0)orange else stone,CONCRETE)
            for(d in floatArrayOf(-.28f,.28f))box(x+d,.45f+level*.9f,z+.44f,.04f,.55f,.025f,paintWhite,MATTE)
        }
        for(i in 0 until a.hits*3) {
            val xx=32f+(i%3)*1.2f;val zz=z+1.7f+(i/3)*.55f
            val newest=i>=(a.hits-1)*3
            val t=if(newest)(1f-a.debris/1.1f).coerceIn(0f,1f) else 1f
            val x=if(t<1f)33f+(i%3)+(xx-33f-(i%3))*t else xx
            val dz=z+(zz-z)*t
            val y=.25f+(1-t)*(3-a.hits)*.9f+sin(t*PI.toFloat())*.45f
            push();translate(x,y,dz);rotate(15f+i*23f+t*30f,0f,1f,0f);surface(0f,0f,0f,.85f,.4f,.7f,orange,CONCRETE);pop()
        }
        // New map strip: lane markings, sheds, flowers and an east-bank picnic clearing.
        noCast { for(i in 0..31)box(2f+i*1.35f,.085f,15.8f,.65f,.015f,.08f,paintWhite,MATTE) }
        panel(7f,1.1f,23f,5f,2.2f,2.5f,teal);panel(7f,2.3f,23f,5.4f,.2f,2.9f,yellow)
        box(7f,.9f,21.73f,2f,1.8f,.06f,ink,MATTE)
        for(x in floatArrayOf(1f,15f,27f,42f,60f,70f))tree(x,43f,1.1f)
        for(x in floatArrayOf(58f,64f,70f)) {
            box(x,.65f,19f,2.3f,.15f,.75f,bark,WOOD)
            for(dx in floatArrayOf(-.8f,.8f))box(x+dx,.3f,19f,.14f,.6f,.14f,bark,WOOD)
            blob(x,2.3f,23.5f,2f,2.5f,2f,leaf,FOLIAGE)
        }
    }
    private fun bridgeKeeper(f:SceneFrame) {
        val k=com.dromadi.tinysim.BridgeKeeper
        panel(k.X,.8f,k.Z,2.3f,1.6f,1.7f,teal)
        panel(k.X,1.75f,k.Z,2.6f,.2f,2f,orange)
        box(k.X,.95f,k.Z+.86f,1.3f,.65f,.03f,glass,GLASS)
        // Attendant stands on a protected platform beside the cabin, outside the driving band.
        push();translate(k.X-1.7f,.2f,k.Z+.3f);rotate(-90f,0f,1f,0f)
        surface(0f,.05f,0f,1.1f,.1f,1.1f,stone,CONCRETE)
        driver(0f,1f,0f,if(f.work.keeperWave>0)1f else .1f,f.seconds)
        if(f.wind.strength>.2f && hypot(f.wind.x-48f,f.wind.z-4.5f)<7f) {
            push();translate(.45f,1.3f,.1f);rotate(sin(f.wind.phase*2f)*15f,1f,0f,0f)
            rod3(0f,0f,0f,0f,1.4f,0f,.035f,metal,METAL)
            box(.3f,1.12f,0f,.6f,.45f,.035f,yellow,PAINT);pop()
        }
        for(zz in floatArrayOf(-.12f,.12f))box(0f,.45f,zz,.13f,.65f,.13f,ink,MATTE)
        pop()
        for(x in floatArrayOf(48.1f,55.9f)) {
            box(x,.55f,6.45f,.35f,1.1f,.35f,teal,PAINT)
            push();translate(x,1.05f,6.45f);rotate(f.work.gate*88f,1f,0f,0f)
            box(0f,0f,-1.8f,.13f,.14f,3.6f,paintWhite,PAINT)
            for(i in 0..5)box(0f,.005f,-.3f-i*.58f,.145f,.15f,.26f,orange,PAINT)
            pop()
            blob(x,1.25f,6.45f,.17f,.17f,.17f,if(f.work.gate>.98f)teal else yellow,EMISSIVE)
        }
    }

    private fun pipe(x:Float,y:Float,z:Float) {
        rod3(x-.94f,y,z,x+.94f,y,z,.18f,orange,PAINT)
        for(side in floatArrayOf(-1f,1f)) {
            val end=x+side*.9f
            rod3(end-.075f,y,z,end+.075f,y,z,.21f,orange,PAINT)
            rod3(end+side*.08f,y,z,end+side*.09f,y,z,.145f,ink,MATTE)
        }
        for(dx in floatArrayOf(-.6f,.6f))rod3(x+dx-.025f,y,z,x+dx+.025f,y,z,.19f,chrome,METAL)
    }
    private fun pipeSite(f:SceneFrame) {
        val site=com.dromadi.tinysim.PipeSite
        if(!f.pipeActive && f.pipeInstalled==0)return
        // Freshly exposed earth is darker than the dry surface. It follows the real cut,
        // and is occluded/removed as the same heightfield is backfilled.
        noCast {
            for(i in 0..5)if(site.cells(i).all { f.earth[it]<=2 && f.sand[it]==0 }) {
                box(site.x(i),-.356f,site.Z,.98f,.018f,.96f,rgba(116,79,46),SOIL)
            }
        }
        for(i in 0..2) {
            val placed=f.pipeInstalled and (1 shl i)!=0
            if(i!=f.pipeCarried) {
                val x=site.pipeX(i);val z=if(placed)site.Z else 11.5f
                pipe(x,if(placed)-.15f else support(f,x,z)+.28f,z)
                if(!placed)for(dx in floatArrayOf(-.6f,.6f))box(x+dx,support(f,x,z)+.06f,z,.3f,.12f,.9f,bark,WOOD)
            }
        }
        for(x in floatArrayOf(9.7f,16.3f))for(z in floatArrayOf(3.6f,6.4f)) {
            val y=support(f,x,z)
            box(x,y+.5f,z,.09f,1f,.09f,paintWhite,PAINT)
            box(x+.2f,y+.83f,z,.4f,.25f,.04f,if(f.pipeFilled==7)teal else orange,PAINT)
        }
        // Two inspection caps remain visible after the utility is buried.
        if(f.pipeFilled==7)for(x in floatArrayOf(10f,16f)) {
            val y=support(f,x,site.Z)
            disk(x,y+.07f,site.Z,.65f,.65f,darkSteel,METAL,.1f)
            for(d in floatArrayOf(-.13f,.13f))box(x,y+.125f,site.Z+d,.36f,.015f,.035f,chrome,METAL)
        }
    }
    private val blockColors=arrayOf(rgba(235,113,65),rgba(33,167,164),rgba(250,195,60))
    private fun block(x:Float,y:Float,z:Float,id:Int) {
        val b=com.dromadi.tinysim.BlockYard
        surface(x,y,z,b.WIDTH,b.HEIGHT,b.WIDTH,blockColors[id],PAINT)
        // Reinforced corners and a clearly visible steel lifting eye.
        for(dx in floatArrayOf(-.48f,.48f))for(dz in floatArrayOf(-.48f,.48f))surface(x+dx,y,z+dz,.09f,b.HEIGHT+.025f,.09f,paintWhite,PAINT)
        rod3(x-.16f,y+.5f,z,x+.16f,y+.5f,z,.045f,darkSteel,METAL)
    }
    private fun blockYard(f:SceneFrame) {
        val b=com.dromadi.tinysim.BlockYard
        val ground=support(f,b.TARGET_X,b.TARGET_Z)
        surface(b.TARGET_X,ground+.035f,b.TARGET_Z,2.1f,.07f,2.1f,stone,CONCRETE)
        val count=f.blockLevel.count { it>=0 }
        if(count<3 && !shadowPass) {
            val y=ground+.08f+count*b.HEIGHT
            val pulse=.7f+.15f*sin(f.seconds*2f)
            val tint=rgba(100,220,195)
            for(d in floatArrayOf(-.85f,.85f)) {
                box(b.TARGET_X+d,y+.04f,b.TARGET_Z,.08f,.08f,1.8f,tint,DECAL)
                box(b.TARGET_X,y+.04f,b.TARGET_Z+d,1.8f,.08f,.08f,tint,DECAL)
            }
            // Thin ghost edges show the volume the next block will occupy, with a slow pulse.
            for(dx in floatArrayOf(-.67f,.67f))for(dz in floatArrayOf(-.67f,.67f))box(b.TARGET_X+dx,y+b.HEIGHT/2,b.TARGET_Z+dz,.035f,pulse*b.HEIGHT,.035f,tint,DECAL)
        }
        for(i in 0 until 3)if(i!=f.blockCarried) {
            val level=max(0,f.blockLevel[i])
            val base=support(f,f.blockX[i],f.blockZ[i])+if(f.blockLevel[i]>=0).08f else 0f
            push();translate(f.blockX[i],base+b.HEIGHT/2+level*b.HEIGHT+f.wind.lift[i],f.blockZ[i]);rotate(f.wind.lift[i]*sin(f.wind.phase+i)*15f,0f,1f,0f)
            block(0f,0f,0f,i);pop()
        }
    }
    private fun crane(f:SceneFrame) {
        val cx=WorldMap.worldX(f.craneX);val cz=f.craneZ;val base=support(f,cx,cz)
        push();translate(cx,base,cz);rotate(f.craneHeading,0f,1f,0f);beginTexFrame()
        for(x in floatArrayOf(-1.5f,1.5f))for(z in floatArrayOf(-.85f,.85f))wheel(x,.48f,z,-f.craneTravel/.47f*180f/PI.toFloat())
        wheelDust(f,3,4.4f)
        translate(0f,bob(f,3),0f);windSway(f,3)
        panel(0f,.75f,0f,4.4f,.5f,1.9f,yellow)
        val parked=!motion[3].moving
        for(x in floatArrayOf(-1.9f,1.9f))for(z in floatArrayOf(-1.4f,1.4f)) {
            box(x,.62f,z*.8f,.14f,.14f,.9f,darkSteel,METAL)
            if(parked) {
                rod3(x,.62f,z,x,.14f,z,.055f,chrome,METAL)
                surface(x,.09f,z,.5f,.1f,.5f,darkSteel,METAL)
                box(x,.14f,z,.18f,.08f,.18f,yellowDark,PAINT)
            }
        }
        for(side in floatArrayOf(-1f,1f)) {
            panel(-1.35f,1.12f,side*.72f,1.0f,.25f,.42f,yellowDark)
            if(side>0)safetyPanel(0f,.82f,side*.97f,2.1f,.16f)
            for(i in 0..5)box(-.7f+i*.22f,1.02f,side*.77f,.035f,.022f,.24f,metal,METAL)
        }
        grille(-1.35f,1.15f,.95f,.8f,.19f)
        cab(1.75f,1f,2f,1.2f,1.9f,.9f,.8f,yellow) { driver(1.65f,1.3f,0f,wave(f,3),f.seconds) }
        exhaust(-1.6f,1.6f,-.7f,f,3)
        beaconLight(-1.2f,1.12f,.7f,f.seconds+.7f)
        endTexFrame();pop()
        val building=f.bridgeClock>0f
        var angle=f.craneAngle*PI.toFloat()/180f;var radius=f.craneReach;var hy=f.hookY
        if(building) {
            // Lift at the yard, slew over the river, lower onto the bridge.
            val yardX=46f;val yardZ=4.5f;val spanX=52f;val spanZ=WorldMap.BRIDGE_Z
            val p=1f-f.bridgeClock/com.dromadi.tinysim.GameState.BRIDGE_STAGE_SECONDS
            val lift=(p/.3f).coerceIn(0f,1f);val swing=((p-.3f)/.4f).coerceIn(0f,1f);val lower=((p-.7f)/.3f).coerceIn(0f,1f)
            val ease={ t:Float-> t*t*(3f-2f*t) }
            val a0=atan2(yardZ-cz,yardX-cx);val a1=atan2(spanZ-cz,spanX-cx)
            val e=ease(swing);angle=a0+(a1-a0)*e;radius=hypot(yardX-cx,yardZ-cz)*(1-e)+hypot(spanX-cx,spanZ-cz)*e
            hy=if(swing<=0f).5f+2.6f*ease(lift) else if(lower<=0f)3.1f else 3.1f-2.7f*ease(lower)
        } else hy-=base
        val pivotX=cx;val pivotY=base+1.55f;val pivotZ=cz
        val hx=pivotX+cos(angle)*radius;val hz=pivotZ+sin(angle)*radius
        val hookY=(if(building)0f else base)+hy
        val tipX=pivotX+cos(angle)*(radius+.2f);val tipZ=pivotZ+sin(angle)*(radius+.2f);val tipY=max(hookY+2.2f,5.2f)
        push();translate(pivotX,pivotY-.25f,pivotZ);rotate(-angle*180f/PI.toFloat(),0f,1f,0f)
        shape(cylinder,0f,-.22f,0f,1.48f,.16f,1.48f,darkSteel,METAL)
        surface(0f,0f,0f,1.6f,.5f,1.3f,yellowDark,PAINT)
        for(i in 0..2)panel(-.8f,.12f+i*.15f,0f,.65f,.13f,1.3f,darkSteel)
        wheelDisc(-.45f,.44f,0f,.23f,.8f,darkSteel,METAL)
        for(i in 0..5)wheelDisc(-.45f,.44f,-.32f+i*.13f,.22f,.035f,metal,METAL)
        pop()
        val midX=pivotX+(tipX-pivotX)*.55f;val midY=pivotY+(tipY-pivotY)*.55f;val midZ=pivotZ+(tipZ-pivotZ)*.55f
        rod3(pivotX,pivotY,pivotZ,midX,midY,midZ,.24f,yellow,PAINT)
        rod3(midX,midY,midZ,tipX,tipY,tipZ,.16f,yellowDark,PAINT)
        rod3(pivotX+.3f,pivotY-.3f,pivotZ,pivotX+(midX-pivotX)*.5f,pivotY+(midY-pivotY)*.5f-.25f,pivotZ+(midZ-pivotZ)*.5f,.08f,chrome,METAL)
        // Telescopic collars and cable sheaves share the existing boom endpoints.
        for(t in floatArrayOf(.5f,.57f,.92f)) {
            val x=pivotX+(tipX-pivotX)*t;val y=pivotY+(tipY-pivotY)*t;val z=pivotZ+(tipZ-pivotZ)*t
            val u=(t+.025f).coerceAtMost(1f)
            rod3(x,y,z,pivotX+(tipX-pivotX)*u,pivotY+(tipY-pivotY)*u,pivotZ+(tipZ-pivotZ)*u,if(t<.6f).28f else .20f,darkSteel,METAL)
        }
        rod3(pivotX-.3f,pivotY+.7f,pivotZ,tipX,tipY+.1f,tipZ,.018f,metal,METAL)
        wheelDisc(tipX,tipY,tipZ,.19f,.2f,darkSteel,METAL)
        rod3(tipX,tipY,tipZ,hx,hookY+.45f,hz,.02f,ink,METAL)
        box(hx,hookY+.4f,hz,.22f,.18f,.18f,yellow,PAINT)
        if(building) {
            push();translate(hx,hookY+.1f,hz)
            when(f.bridgeStage) {
                0->surface(0f,-.3f,0f,.55f,.6f,1.4f,stone,CONCRETE)
                1->surface(0f,-.1f,0f,3.2f,.2f,.28f,orange,PAINT)
                else->surface(0f,-.08f,0f,2.4f,.1f,1.6f,rgba(104,102,100),ASPHALT)
            }
            pop()
        } else if(f.recovery.held>=0) {
            push();translate(hx,hookY-(if(f.recovery.held%4==3).9f else 1.9f),hz);recoveryPart(f.recovery.held);pop()
            for(dx in floatArrayOf(-.6f,.6f))rod3(hx,hookY+.25f,hz,hx+dx,hookY-.25f,hz,.025f,darkSteel,METAL)
        } else if(f.pipeCarried>=0) {
            pipe(hx,hookY-.4f,hz)
            for(dx in floatArrayOf(-.65f,.65f))rod3(hx,hookY+.25f,hz,hx+dx,hookY-.2f,hz,.022f,darkSteel,METAL)
        } else if(f.blockCarried>=0) {
            val by=hookY-.45f
            val sway=.035f*sin(f.seconds*2.3f)
            block(hx+sway,by,hz,f.blockCarried)
            for(dx in floatArrayOf(-.48f,.48f))rod3(hx,hookY+.25f,hz,hx+dx+sway,by+.47f,hz,.025f,darkSteel,METAL)
        } else {
            // Clamshell grab: two jaws that close around a load of sand.
            val open=if(f.craneCargo>0)0f else 12f
            for(side in floatArrayOf(-1f,1f)) {
                push();translate(hx,hookY+.2f,hz);rotate(side*open,1f,0f,0f)
                box(0f,-.16f,side*.2f,.62f,.34f,.06f,darkSteel,METAL)
                box(0f,-.32f,side*.1f,.62f,.06f,.24f,darkSteel,METAL)
                pop()
            }
            if(f.craneCargo>0)mound(hx,hookY-.2f,hz,.55f,.25f,.5f,sandColor)
        }
    }
    /** Photo materials on a vehicle follow its body: texture space = the vehicle's local space. */
    private fun beginTexFrame() { Matrix.invertM(texFrame,0,model,0) }
    private fun endTexFrame() { Matrix.setIdentityM(texFrame,0) }
    private fun engineOn(f:SceneFrame,v:Int)=motion[v].moving || f.selected.ordinal==v
    /** Settle bounce after stopping plus a faint diesel tremble while the engine runs. */
    private fun windSway(f:SceneFrame,v:Int) {
        val point=when(v) {
            0->WorldMap.worldX(f.excavatorX) to f.excavatorZ
            1->WorldMap.worldX(f.truckX) to f.truckZ
            2->WorldMap.worldX(f.dozerX) to f.dozerZ
            3->WorldMap.worldX(f.craneX) to f.craneZ
            4->WorldMap.worldX(f.work.mixerX) to f.work.mixerZ
            else->WorldMap.worldX(f.work.wreckX) to f.work.wreckZ
        }
        val amount=f.wind.strength*(1f-hypot(point.first-f.wind.x,point.second-f.wind.z)/7f).coerceIn(0f,1f)
        rotate(amount*sin(f.wind.phase*1.4f)*1.2f,0f,0f,1f)
    }
    private fun bob(f:SceneFrame,v:Int)=motion[v].settle+if(engineOn(f,v)).005f*sin(f.seconds*2f*PI.toFloat()*21f) else 0f
    /** 0..1 waving strength for a couple of seconds after the vehicle was picked. */
    private fun wave(f:SceneFrame,v:Int):Float {
        val age=f.seconds-waveStart
        return if(v!=waveVehicle || age !in 0f..2.4f)0f else min(1f,age/.2f)*min(1f,(2.4f-age)/.4f)
    }
    /** Seated driver in a hi-vis vest and hard hat, facing +x; one arm waves when greeted. */
    private fun driver(x:Float,y:Float,z:Float,waving:Float,seconds:Float) {
        panel(x,y,z,.3f,.36f,.4f,vest)
        box(x+.16f,y-.2f,z,.32f,.12f,.34f,trousers,MATTE)
        blob(x+.02f,y+.32f,z,.25f,.27f,.25f,skin,MATTE)
        blob(x+.02f,y+.43f,z,.29f,.17f,.29f,hat,PAINT)
        disk(x+.06f,y+.42f,z,.36f,.34f,hat,PAINT,.02f)
        for(eye in floatArrayOf(-.05f,.05f))blob(x+.14f,y+.34f,z+eye,.03f,.04f,.03f,ink,MATTE)
        val side=if(z>=0f)-1f else 1f
        // Steering-wheel hand on the inside, waving hand toward the window.
        rod3(x+.04f,y+.12f,z+side*.18f,x+.3f,y+.02f,z+side*.12f,.045f,vest)
        blob(x+.31f,y+.02f,z+side*.12f,.08f,.08f,.08f,skin,MATTE)
        val hx:Float;val hy:Float;val hz:Float
        if(waving>0f) { hx=x+.18f;hy=y+.12f+.4f*waving;hz=z-side*(.24f+.13f*waving*sin(seconds*15f)) }
        else { hx=x+.3f;hy=y+.02f;hz=z-side*.12f }
        rod3(x+.04f,y+.12f,z-side*.18f,hx,hy,hz,.045f,vest)
        blob(hx,hy,hz,.09f,.09f,.09f,skin,MATTE)
    }
    /** Diesel smoke puffs drifting back from the stack while the engine runs. */
    private fun exhaust(x:Float,y:Float,z:Float,f:SceneFrame,v:Int) {
        if(shadowPass || !engineOn(f,v))return
        val effort=motion[v].effort
        GL.glDepthMask(false)
        for(i in 0..4) {
            val t=(f.seconds*(.9f+effort)+i*.2f+v*.13f)%1f
            val size=.12f+.38f*t
            tint[0]=smoke[0];tint[1]=smoke[1];tint[2]=smoke[2];tint[3]=(1f-t)*(.08f+.22f*effort)
            blob(x-t*(.4f+.8f*effort),y+t*1.1f,z+sin(i*1.9f+v)*.1f*t,size,size,size,tint,DECAL)
        }
        GL.glDepthMask(true)
    }
    /** Dust kicked up behind the wheels or tracks of a moving vehicle (local coordinates). */
    private fun wheelDust(f:SceneFrame,v:Int,length:Float) {
        if(shadowPass || !motion[v].moving)return
        val effort=motion[v].effort
        GL.glDepthMask(false)
        for(i in 0..5) {
            val t=(f.seconds*1.6f+i/6f)%1f
            val size=.25f+.6f*t
            tint[0]=dust[0];tint[1]=dust[1];tint[2]=dust[2];tint[3]=(1f-t)*.22f*effort
            blob(-length/2-.2f-t*.9f,.2f+t*.55f,(if(i%2==0).75f else -.75f)+sin(i*2.7f)*.2f,size,size*.7f,size,tint,DECAL)
        }
        GL.glDepthMask(true)
    }
    /** Fading tyre and track prints left on the ground. */
    private fun treadMarks(f:SceneFrame) {
        if(shadowPass)return
        GL.glDepthMask(false)
        for(v in motion.indices)for(k in 0 until markCount[v]) {
            val o=k*4;val m=marks[v];val age=f.seconds-m[o+3]
            if(age<0f || age>MARK_SECONDS)continue
            tint[0]=tread[0];tint[1]=tread[1];tint[2]=tread[2];tint[3]=.26f*(1f-age/MARK_SECONDS)
            val gauge=if(v==1 || v==3).91f else .78f;val width=if(v==1 || v==3).3f else .48f
            val a=m[o+2]*PI.toFloat()/180f
            for(side in floatArrayOf(-gauge,gauge)) {
                val x=m[o]+sin(a)*side;val z=m[o+1]+cos(a)*side
                push();translate(x,Terrain.height(f.earth,f.sand,x,z)+.012f,z);rotate(m[o+2],0f,1f,0f)
                draw(cube,tint,DECAL,.42f,.004f,width);pop()
            }
        }
        GL.glDepthMask(true)
    }
    /** Cylinder between two arbitrary 3D points in the current model space. */
    private fun rod3(ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,radius:Float,color:FloatArray,material:FloatArray=MATTE) {
        val dx=bx-ax;val dy=by-ay;val dz=bz-az;val length=sqrt(dx*dx+dy*dy+dz*dz)
        if(length<1e-4f)return
        push();translate((ax+bx)/2,(ay+by)/2,(az+bz)/2)
        val axis=hypot(dz,dx)
        if(axis>1e-5f)rotate(acos((dy/length).coerceIn(-1f,1f))*180f/PI.toFloat(),dz/axis,0f,-dx/axis)
        else if(dy<0f)rotate(180f,1f,0f,0f)
        scale(radius*2,length,radius*2);draw(cylinder,color,material);pop()
    }
    private fun workLamp(x:Float,y:Float,z:Float) {
        panel(x,y,z,.13f,.13f,.22f,ink)
        box(x+.069f,y,z,.012f,.08f,.15f,lamp,EMISSIVE)
    }
    private fun safetyPanel(x:Float,y:Float,z:Float,width:Float,height:Float) {
        panel(x,y,z,width,height,.035f,ink)
        for(i in 0..3) {
            val px=x-width*.36f+i*width*.24f
            rod3(px-height*.18f,y-height*.31f,z+.025f,px+height*.18f,y+height*.31f,z+.025f,height*.075f,yellow,PAINT)
        }
    }
    private fun grille(x:Float,y:Float,z:Float,width:Float,height:Float,slots:Int=7) {
        panel(x,y,z,width,height,.035f,ink)
        for(i in 0 until slots)box(x-width*.42f+i*width*.84f/(slots-1),y,z+.025f,.025f,height*.8f,.025f,darkSteel,METAL)
        for(sx in floatArrayOf(-1f,1f))for(sy in floatArrayOf(-1f,1f))wheelDisc(x+sx*width*.43f,y+sy*height*.38f,z+.028f,.014f,.014f,metal,METAL)
    }
    private fun hose(ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,sag:Float=.18f) {
        for(i in 0 until 8) {
            val t=i/8f;val u=(i+1)/8f
            rod3(ax+(bx-ax)*t,ay+(by-ay)*t-sin(t*PI.toFloat())*sag,az+(bz-az)*t,
                ax+(bx-ax)*u,ay+(by-ay)*u-sin(u*PI.toFloat())*sag,az+(bz-az)*u,.022f,ink,RUBBER)
        }
    }
    private fun beaconLight(x:Float,y:Float,z:Float,seconds:Float) {
        shape(cylinder,x,y-.04f,z,.16f,.06f,.16f,ink,METAL)
        val glow=.78f+.22f*(.5f+.5f*sin(seconds*2f))
        blob(x,y+.06f,z,.15f,.18f,.15f,rgba((255*glow).toInt(),(140*glow).toInt(),30),EMISSIVE)
    }
    /**
     * Open cab: painted lower body, roof and corner pillars around see-through glass, so the
     * driver inside is visible. The interior is drawn before the glass for correct blending.
     */
    private inline fun cab(x:Float,bottom:Float,top:Float,glassBottom:Float,glassTop:Float,width:Float,length:Float,color:FloatArray,interior:()->Unit) {
        panel(x,(bottom+glassBottom)/2,0f,width,glassBottom-bottom,length,color)
        panel(x,(glassTop+top)/2,0f,width+.04f,top-glassTop,length+.04f,color)
        val h=glassTop-glassBottom;val mid=(glassTop+glassBottom)/2
        for(sx in floatArrayOf(-1f,1f))for(sz in floatArrayOf(-1f,1f))box(x+sx*(width/2-.04f),mid,sz*(length/2-.04f),.08f,h,.08f,ink,PAINT)
        box(x+width/2-.12f,glassBottom+.06f,0f,.14f,.16f,length*.8f,ink,MATTE)
        interior()
        box(x+width/2-.01f,mid,0f,.02f,h,length-.16f,glass,GLASS)
        box(x-width/2+.01f,mid,0f,.02f,h,length-.16f,glass,GLASS)
        for(sz in floatArrayOf(-1f,1f))box(x,mid,sz*(length/2-.01f),width-.16f,h,.02f,glass,GLASS)
        for(side in floatArrayOf(-1f,1f)) {
            val z=side*(length/2+.012f)
            box(x,glassBottom,z,width-.14f,.036f,.025f,ink,RUBBER)
            box(x,glassTop,z,width-.14f,.036f,.025f,ink,RUBBER)
            // Door seam, handle, hinge plates and a serrated access step.
            box(x-width*.34f,(bottom+glassBottom)/2,z,.014f,glassBottom-bottom-.04f,.02f,darkSteel,METAL)
            panel(x-width*.18f,glassBottom-.11f,z+side*.025f,width*.22f,.035f,.055f,chrome)
            for(y in floatArrayOf(bottom+.07f,glassBottom-.08f))box(x+width*.36f,y,z,.04f,.06f,.028f,metal,METAL)
            panel(x,bottom-.1f,z+side*.09f,width*.8f,.07f,.22f,darkSteel)
            for(i in 0..5)box(x-width*.32f+i*width*.125f,bottom-.06f,z+side*.09f,.026f,.014f,.18f,chrome,METAL)
            rod3(x-width*.45f,bottom+.1f,z+side*.065f,x-width*.45f,glassBottom+.12f,z+side*.065f,.022f,chrome,METAL)
            workLamp(x+width*.4f,top+.055f,side*length*.32f)
        }
        val front=x+width/2+.012f
        rod3(front,glassBottom+.04f,-length*.25f,front,glassBottom+h*.6f,length*.08f,.012f,ink,RUBBER)
        rod3(front+.005f,glassBottom+h*.5f,-length*.05f,front+.005f,glassBottom+h*.68f,length*.22f,.017f,ink,RUBBER)
        panel(x+width*.47f,glassTop-.015f,0f,.16f,.06f,length*.95f,ink)
    }
    private fun tracks(length:Float,travel:Float) {
        val half=length/2-.34f;val r=.35f;val straight=half*2;val arc=PI.toFloat()*r
        val perimeter=straight*2+arc*2
        for(z in floatArrayOf(-.78f,.78f)) {
            box(0f,.41f,z,length-.55f,.62f,.48f,rubber,RUBBER)
            val outer=z+sign(z)*.27f
            for(i in 0..4) {
                val x=-half+i*straight/4
                wheelDisc(x,.32f,outer,.18f,.07f,darkSteel,METAL)
                wheelDisc(x,.32f,outer+sign(z)*.045f,.075f,.03f,chrome,METAL)
            }
            for(x in floatArrayOf(-half,half)) {
                wheelDisc(x,.41f,z,.33f,.48f,rubber,RUBBER)
                wheelDisc(x,.41f,outer,.27f,.05f,yellowDark,PAINT)
                wheelDisc(x,.41f,outer+sign(z)*.035f,.12f,.035f,darkSteel,METAL)
                for(i in 0..7) {
                    val a=i*PI.toFloat()/4-travel/r
                    wheelDisc(x+cos(a)*.19f,.41f+sin(a)*.19f,outer+sign(z)*.045f,.025f,.025f,chrome,METAL)
                }
            }
            box(0f,.55f,outer,length-.9f,.1f,.04f,darkSteel,METAL)
            // Each shoe follows one continuous closed loop, including both curved ends.
            for(i in 0 until 36) {
                val t=((i*perimeter/36+travel)%perimeter+perimeter)%perimeter
                var x:Float;var y:Float;var angle:Float
                when {
                    t<straight -> { x=-half+t;y=.41f+r;angle=0f }
                    t<straight+arc -> { val a=PI.toFloat()/2-(t-straight)/r;x=half+cos(a)*r;y=.41f+sin(a)*r;angle=a*180f/PI.toFloat()-90f }
                    t<straight*2+arc -> { x=half-(t-straight-arc);y=.41f-r;angle=180f }
                    else -> { val a=-PI.toFloat()/2-(t-straight*2-arc)/r;x=-half+cos(a)*r;y=.41f+sin(a)*r;angle=a*180f/PI.toFloat()-90f }
                }
                push();translate(x,y,z);rotate(angle,0f,0f,1f)
                panel(0f,0f,0f,perimeter/36-.014f,.045f,.55f,darkSteel)
                box(0f,.032f,0f,.045f,.035f,.58f,metal,METAL)
                pop()
            }
        }
    }
    private fun wheel(x:Float,y:Float,z:Float,angle:Float) {
        val side=sign(z)
        push();translate(x,y,z);rotate(angle,0f,0f,1f)
        shape(tyreMesh,0f,0f,0f,.94f,.94f,1f,rubber,RUBBER)
        // Recessed rim, dark ventilation holes, centre cap and eight actual lug nuts.
        wheelDisc(0f,0f,side*.177f,.30f,.045f,darkSteel,METAL)
        wheelDisc(0f,0f,side*.204f,.267f,.025f,chrome,METAL)
        for(i in 0..7) {
            val a=i*PI.toFloat()/4
            wheelDisc(cos(a)*.21f,sin(a)*.21f,side*.221f,.033f,.014f,ink,MATTE)
            wheelDisc(cos(a)*.128f,sin(a)*.128f,side*.237f,.023f,.028f,metal,METAL)
        }
        wheelDisc(0f,0f,side*.241f,.086f,.065f,darkSteel,METAL)
        wheelDisc(0f,0f,side*.278f,.05f,.014f,chrome,METAL)
        pop()
    }
    private fun wrist(f:SceneFrame):Pair<Float,Float> {
        val a=f.excavatorHeading*PI.toFloat()/180f;val d=(f.armX-200f)/60f
        return WorldMap.worldX(f.excavatorX)+cos(a)*d to f.excavatorZ-sin(a)*d
    }
    private fun guide(f:SceneFrame) {
        if(f.selected!=GameState.Vehicle.EXCAVATOR)return
        val point=wrist(f)
        noCast {
            blob(point.first,WorldCamera.y(f.armY)+support(f,WorldMap.worldX(f.excavatorX),f.excavatorZ),point.second+.04f,.18f,.18f,.18f,white,EMISSIVE)
            // Ring below the bucket makes its real ground position easy to see.
            disk(point.first,Terrain.height(f.earth,f.sand,point.first,point.second)+.02f,point.second,.7f,.7f,rgba(255,247,204,.45f),DECAL)
        }
    }
    private fun particles(f:SceneFrame) {
        if(!shadowPass && f.transferSerial!=transferSeen) {
            transferSeen=f.transferSerial;streamStart=f.seconds
            val dumping=f.transferKind==3
            val angle=f.truckHeading*PI.toFloat()/180f
            streamX=if(dumping)WorldCamera.x(f.truckX)-cos(angle)*1.8f else wrist(f).first
            streamZ=if(dumping)f.truckZ+sin(angle)*1.8f else wrist(f).second
            streamY=if(dumping)2f else WorldCamera.y(f.armY)+support(f,WorldMap.worldX(f.excavatorX),f.excavatorZ)
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
    private fun surface(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray)=shape(panelMesh,x,y,z,w,h,d,color,material)
    private fun blob(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray)=shape(sphere,x,y,z,w,h,d,color,material)
    private fun mound(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray)=shape(moundMesh,x,y,z,w,h,d,color,SAND)
    private fun disk(x:Float,y:Float,z:Float,w:Float,d:Float,color:FloatArray,material:FloatArray,h:Float=.012f)=shape(cylinder,x,y,z,w,h,d,color,material)
    private fun wheelDisc(x:Float,y:Float,z:Float,r:Float,thickness:Float,color:FloatArray,material:FloatArray) {
        push();translate(x,y,z);rotate(90f,1f,0f,0f);scale(r*2,thickness,r*2);draw(cylinder,color,material);pop()
    }
    private fun shape(mesh:Mesh,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,color:FloatArray,material:FloatArray) {
        push();translate(x,y,z);draw(mesh,color,material,w,h,d);pop()
    }
    private fun draw(mesh:Mesh,color:FloatArray,material:FloatArray,w:Float,h:Float,d:Float) {
        push();scale(w,h,d);draw(mesh,color,material);pop()
    }
    private fun draw(mesh:Mesh,color:FloatArray,material:FloatArray) {
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
        val slot=if(material.size>6 && textures.loaded)material[5].toInt() else -1
        GL.glUniform1f(uTexOn,if(slot>=0)1f else 0f)
        if(slot>=0) {
            if(slot!=boundSlot) { textures.bind(slot);boundSlot=slot }
            val m=textures.mean[slot]
            GL.glUniform1f(uTexScale,material[6]);GL.glUniform3f(uTexMean,m[0],m[1],m[2])
            GL.glUniformMatrix4fv(uTexFrame,1,false,texFrame,0)
            for(c in 0..2)for(r in 0..2)texRot[c*3+r]=texFrame[c*4+r]
            GL.glUniformMatrix3fv(uTexRot,1,false,texRot,0)
        }
        mesh.draw(position,normal);drawCalls++
    }
    private fun push() { check(depth<stack.lastIndex);model.copyInto(stack[depth+1]);depth++ }
    private fun pop() { check(depth>0);depth-- }
    private fun translate(x:Float,y:Float,z:Float)=Matrix.translateM(model,0,x,y,z)
    private fun scale(x:Float,y:Float,z:Float)=Matrix.scaleM(model,0,x,y,z)
    private fun rotate(a:Float,x:Float,y:Float,z:Float)=Matrix.rotateM(model,0,a,x,y,z)
    companion object {
        private const val MARKS=60;private const val MARK_SECONDS=30f
        private const val GRASS_Y=-1.6f
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
            uniform mat4 uTexFrame;
            uniform mat3 uTexRot;
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
            varying VARYING_PRECISION vec3 vTex;
            varying VARYING_PRECISION vec3 vTexNormal;
            void main() {
                vec4 world=uModel*vec4(aPosition,1.0);
                vNormal=normalize(uNormal*aNormal);
                vWorld=world.xyz;
                // Photo materials are projected in the static world or the vehicle's own frame.
                vTex=(uTexFrame*world).xyz;
                vTexNormal=uTexRot*vNormal;
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
            varying VARYING_PRECISION vec3 vTex;
            varying VARYING_PRECISION vec3 vTexNormal;
            uniform sampler2D uAlbedoMap;
            uniform sampler2D uNormalMap;
            uniform sampler2D uRoughMap;
            uniform float uTexOn;
            uniform float uTime;
            uniform float uTexScale;
            uniform vec3 uTexMean;
            uniform mat3 uTexRot;

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
                if(uKind>8.5&&uKind<9.5) {
                    // River: ripples flowing south, sky reflection by Fresnel, sun glints and light foam.
                    vec2 q=vWorld.xz;
                    float n1=noise(vec3(q.x*1.6,q.y*0.9-uTime*0.8,uTime*0.15));
                    float n2=noise(vec3(q.x*3.7+5.0,q.y*2.3-uTime*1.6,uTime*0.3));
                    vec3 wn=normalize(vec3((n1-0.5)*0.35+(n2-0.5)*0.18,1.0,(n2-0.5)*0.35));
                    float fr=0.04+0.96*pow(1.0-max(dot(wn,view),0.0),5.0);
                    vec3 deep=albedo*vec3(0.42,0.5,0.55);
                    float lit=shadow(wn);
                    vec3 col=mix(deep*(0.5+0.5*lit),skyColor(reflect(-view,wn)),fr);
                    col+=vec3(1.0,0.9,0.75)*pow(max(dot(reflect(-uSun,wn),view),0.0),160.0)*5.0*lit;
                    float foam=smoothstep(0.74,0.92,noise(vec3(q.x*2.5,q.y*1.2-uTime*1.1,0.0)));
                    col=mix(col,vec3(0.85),foam*0.22);
                    gl_FragColor=vec4(finish(col),uColor.a+0.12*fr);return;
                }
                vec3 n=normalize(vNormal);
                float fade=clamp(1.0-dist/60.0,0.0,1.0);
                float specScale=1.0;
                if(uTexOn>0.5) {
                    // Triplanar CC0 photo material: albedo recoloured toward the palette colour,
                    // whiteout-blended normal map and roughness-driven highlights.
                    vec3 bn=normalize(vTexNormal);
                    vec3 w=pow(abs(bn),vec3(4.0));w/=(w.x+w.y+w.z);
                    vec3 p=vTex/uTexScale;
                    vec2 ux=p.zy;vec2 uy=p.xz;vec2 uz=p.xy;
                    vec3 tex=texture2D(uAlbedoMap,ux).rgb*w.x+texture2D(uAlbedoMap,uy).rgb*w.y+texture2D(uAlbedoMap,uz).rgb*w.z;
                    albedo*=min(pow(tex,vec3(2.2))/pow(uTexMean,vec3(2.2)),vec3(4.0));
                    float rough=texture2D(uRoughMap,ux).r*w.x+texture2D(uRoughMap,uy).r*w.y+texture2D(uRoughMap,uz).r*w.z;
                    specScale=1.25-rough;
                    vec3 tx=texture2D(uNormalMap,ux).xyz*2.0-1.0;
                    vec3 ty=texture2D(uNormalMap,uy).xyz*2.0-1.0;
                    vec3 tz=texture2D(uNormalMap,uz).xyz*2.0-1.0;
                    tx=vec3(tx.xy+bn.zy,abs(tx.z)*bn.x);
                    ty=vec3(ty.xy+bn.xz,abs(ty.z)*bn.y);
                    tz=vec3(tz.xy+bn.xy,abs(tz.z)*bn.z);
                    vec3 tn=normalize(tx.zyx*w.x+ty.xzy*w.y+tz.xyz*w.z);
                    n=normalize(vec3(dot(uTexRot[0],tn),dot(uTexRot[1],tn),dot(uTexRot[2],tn)));
                }
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
                float spec=pow(max(dot(n,h),0.0),shininess)*uMaterial.x*(shininess+8.0)/25.0*specScale;
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
