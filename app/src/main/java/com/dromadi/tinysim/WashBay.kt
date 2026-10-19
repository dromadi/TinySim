package com.dromadi.tinysim

import com.dromadi.tinysim.scene.*
import kotlin.math.*

/** Cosmetic dirt never owns soil inventory. Only a held, aimed spray cleans a patch. */
class WashBay {
    val mud=FloatArray(8)
    var open=false;private set
    var spraying=false;private set
    var aimX=600f;private set
    var aimY=337f;private set
    var cycles=0;private set
    private var rewarded=true
    private var mask=0
    val clean get()=mud.all { it<=.005f }
    fun soil() { for(i in mud.indices)mud[i]=(mud[i]+.22f*(1f+i%3*.2f)).coerceAtMost(1f);rewarded=false }
    fun ready(g:GameState)=open && g.excavatorGoal==null && hypot(WorldMap.worldX(g.excavatorX)-X,g.excavatorZ-Z)<.6f
    fun enter(g:GameState) {
        g.stopMovement();g.wind.arena.leave();g.selected=GameState.Vehicle.EXCAVATOR;g.pipes.active=false
        open=true;g.driveTo(GameState.Vehicle.EXCAVATOR,WorldMap.gameX(X),Z)
    }
    fun leave(g:GameState) { open=false;g.stopMovement() }
    fun stop() { spraying=false;mask=0 }
    fun targets(g:GameState):List<Pair<Float,Float>> {
        val camera=camera(g)
        return PATCHES.map { p->camera.project(Vec3(WorldMap.worldX(g.excavatorX)+p.x,g.excavatorHeight+p.y,g.excavatorZ+p.z)) }
    }
    fun spray(g:GameState,x:Float,y:Float) {
        if(!ready(g) || !x.isFinite() || !y.isFinite() || x !in 0f..1200f || y !in 100f..535f) { stop();return }
        aimX=x;aimY=y;spraying=true;mask=0
        targets(g).forEachIndexed { i,p->if(hypot(x-p.first,y-p.second)<42f)mask=mask or (1 shl i) }
    }
    fun update(g:GameState,dt:Float) {
        if(!ready(g)) { stop();return }
        g.excavatorHeading=0f
        if(!spraying)return
        for(i in mud.indices)if(mask and (1 shl i)!=0)mud[i]=(mud[i]-dt*1.4f).coerceAtLeast(0f)
        if(clean && !rewarded) {
            mud.fill(0f);rewarded=true;cycles++;g.rewardSerial++;g.celebrating=1.2f;g.greet(GameState.Vehicle.EXCAVATOR)
        }
    }
    fun reset() { mud.fill(0f);cycles=0;rewarded=true;open=false;stop() }
    fun snapshot()=listOf(cycles,if(rewarded)1 else 0).joinToString(",")+"#"+mud.joinToString(",")
    fun restore(s:String) {
        val p=s.split('#');require(p.size==2);val h=p[0].split(',');require(h.size==2)
        val count=h[0].toInt();require(count in 0..1000000 && h[1] in listOf("0","1"))
        val dirt=p[1].split(',').map { it.toFloat() };require(dirt.size==8 && dirt.all { it.isFinite() && it in 0f..1f })
        require((h[1]=="1")==dirt.all { it<=.005f })
        reset();cycles=count;rewarded=h[1]=="1";dirt.toFloatArray().copyInto(mud)
    }
    companion object {
        const val X=2f;const val Z=14f
        val PATCHES=listOf(Vec3(-1.05f,.4f,1.03f),Vec3(-.35f,.4f,1.03f),Vec3(.4f,.4f,1.03f),Vec3(1.1f,.4f,1.03f),
            Vec3(-.95f,1.65f,.79f),Vec3(-.4f,2.08f,.79f),Vec3(.87f,1.5f,.84f),Vec3(-.6f,2.54f,.25f))
        fun camera(g:GameState)=camera(WorldMap.worldX(g.excavatorX),g.excavatorZ,g.excavatorHeight)
        fun camera(x:Float,z:Float,y:Float)=CameraSpec(Vec3(x+4.8f,y+4f,z+7f),Vec3(x+.6f,y+1.25f,z))
    }
}
