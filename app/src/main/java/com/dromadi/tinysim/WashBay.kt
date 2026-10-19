package com.dromadi.tinysim

import com.dromadi.tinysim.scene.*
import kotlin.math.*

/** Six independent cosmetic inventories. Dirt and soap never own construction material. */
class WashBay {
    enum class Tool { WATER, SOAP }
    val dirt=Array(6){FloatArray(8)}
    val bubbles=Array(6){FloatArray(8)}
    val mud get()=dirt[vehicle.ordinal]
    val foam get()=bubbles[vehicle.ordinal]
    var vehicle=GameState.Vehicle.EXCAVATOR;private set
    var tool=Tool.WATER;private set
    var open=false;private set
    var spraying=false;private set
    var aimX=600f;private set
    var aimY=337f;private set
    var cycles=0;private set
    private val rewarded=BooleanArray(6){true}
    private val previousTravel=FloatArray(6)
    private var mask=0
    val clean get()=mud.all { it<=.005f } && foam.all { it<=.005f }
    fun soil(v:GameState.Vehicle=GameState.Vehicle.EXCAVATOR,amount:Float=.22f) {
        if(!amount.isFinite() || amount<=0f)return
        val row=dirt[v.ordinal]
        for(i in row.indices)row[i]=(row[i]+amount.coerceAtMost(1f)*(1f+i%3*.2f)).coerceAtMost(1f)
        rewarded[v.ordinal]=false
    }
    private fun goal(g:GameState,v:GameState.Vehicle)=when(v) {
        GameState.Vehicle.EXCAVATOR->g.excavatorGoal;GameState.Vehicle.TRUCK->g.truckGoal
        GameState.Vehicle.DOZER->g.dozerGoal;GameState.Vehicle.CRANE->g.craneGoal
        else->g.fleet.machine(v).goal
    }
    fun ready(g:GameState)=open && goal(g,vehicle)==null && g.vehicleBody(vehicle).point.distance(VehicleNavigation.Point(X,Z))<.6f
    fun enter(g:GameState,v:GameState.Vehicle=g.selected) {
        g.stopMovement();g.wind.arena.leave();g.selected=v;g.pipes.active=false;vehicle=v;open=true
        // Vacate the shared bay using actual routes; rapid choices also clear older occupants.
        for(other in GameState.Vehicle.entries)if(other!=v && g.vehicleBody(other).point.distance(VehicleNavigation.Point(X,Z))<4.5f)
            g.driveTo(other,WorldMap.gameX(-5f+other.ordinal*7f),21.5f)
        g.driveTo(v,WorldMap.gameX(X),Z)
    }
    fun choose(tool:Tool) { stop();this.tool=tool }
    fun leave(g:GameState) { open=false;g.stopMovement() }
    fun stop() { spraying=false;mask=0 }
    fun targets(g:GameState):List<Pair<Float,Float>> {
        val p=g.vehicleBody(vehicle).point;val camera=camera(g)
        return patches(vehicle).map { q->camera.project(Vec3(p.x+q.x,g.surface(p.x,p.z)+q.y,p.z+q.z)) }
    }
    fun spray(g:GameState,x:Float,y:Float) {
        if(!ready(g) || !x.isFinite() || !y.isFinite() || x !in 0f..1200f || y !in 100f..535f) { stop();return }
        aimX=x;aimY=y;spraying=true;mask=0
        targets(g).forEachIndexed { i,p->if(hypot(x-p.first,y-p.second)<42f)mask=mask or (1 shl i) }
    }
    fun update(g:GameState,dt:Float) {
        val travel=floatArrayOf(g.excavatorTravel,g.truckTravel,g.dozerTravel,g.craneTravel,g.fleet.mixer.travel,g.fleet.wrecker.travel)
        for(v in GameState.Vehicle.entries) {
            val delta=(travel[v.ordinal]-previousTravel[v.ordinal]).coerceAtLeast(0f)
            previousTravel[v.ordinal]=travel[v.ordinal]
            if(!open && delta>0f)soil(v,delta*.008f)
        }
        if(!ready(g)) { stop();return }
        when(vehicle) {
            GameState.Vehicle.EXCAVATOR->g.excavatorHeading=0f;GameState.Vehicle.TRUCK->g.truckHeading=0f
            GameState.Vehicle.DOZER->g.dozerHeading=0f;GameState.Vehicle.CRANE->g.craneHeading=0f
            else->g.fleet.machine(vehicle).heading=0f
        }
        if(!spraying)return
        for(i in mud.indices)if(mask and (1 shl i)!=0) {
            if(tool==Tool.SOAP) { foam[i]=(foam[i]+dt*1.6f).coerceAtMost(1f);mud[i]=(mud[i]-dt*.35f).coerceAtLeast(0f) }
            else { mud[i]=(mud[i]-dt*(1.4f+foam[i])).coerceAtLeast(0f);foam[i]=(foam[i]-dt*1.8f).coerceAtLeast(0f) }
        }
        if(clean && !rewarded[vehicle.ordinal]) {
            mud.fill(0f);foam.fill(0f);rewarded[vehicle.ordinal]=true;cycles++;g.rewardSerial++;g.celebrating=1.2f;g.greet(vehicle)
        }
    }
    fun reset() { dirt.forEach { it.fill(0f) };bubbles.forEach { it.fill(0f) };rewarded.fill(true);previousTravel.fill(0f);cycles=0;vehicle=GameState.Vehicle.EXCAVATOR;tool=Tool.WATER;open=false;stop() }
    fun snapshot()=listOf(cycles,vehicle.ordinal,tool.ordinal).joinToString(",")+"#"+dirt.flatMap { it.toList() }.joinToString(",")+"#"+bubbles.flatMap { it.toList() }.joinToString(",")+"#"+rewarded.joinToString(","){if(it)"1" else "0"}
    fun restore(s:String) {
        val p=s.split('#');require(p.size==4);val h=p[0].split(',');require(h.size==3)
        val count=h[0].toInt();val v=h[1].toInt();val t=h[2].toInt();require(count in 0..1000000 && v in 0..5 && t in 0..1)
        fun row(text:String)=text.split(',').map { it.toFloat() }.also { a->require(a.size==48 && a.all { it.isFinite() && it in 0f..1f }) }
        val d=row(p[1]);val f=row(p[2]);val r=p[3].split(',');require(r.size==6 && r.all { it in listOf("0","1") })
        for(i in 0..5) {
            val indices=i*8 until i*8+8
            require(if(r[i]=="1")indices.all { d[it]<=.005f } else indices.any { d[it]>.005f || f[it]>.005f })
        }
        reset();cycles=count;vehicle=GameState.Vehicle.entries[v];tool=Tool.entries[t]
        for(i in 0 until 48) { dirt[i/8][i%8]=d[i];bubbles[i/8][i%8]=f[i] }
        for(i in 0..5)rewarded[i]=r[i]=="1"
    }
    fun restoreLegacy(s:String) {
        val p=s.split('#');require(p.size==2);val h=p[0].split(',');require(h.size==2)
        val count=h[0].toInt();require(count in 0..1000000 && h[1] in listOf("0","1"))
        val d=p[1].split(',').map { it.toFloat() };require(d.size==8 && d.all { it.isFinite() && it in 0f..1f } && (h[1]=="1")==d.all { it<=.005f })
        reset();cycles=count;rewarded[0]=h[1]=="1";d.toFloatArray().copyInto(dirt[0])
    }
    companion object {
        const val X=2f;const val Z=14f
        val PATCHES=listOf(Vec3(-1.05f,.47f,1.2f),Vec3(-.35f,.47f,1.2f),Vec3(.4f,.47f,1.2f),Vec3(1.1f,.47f,1.2f),
            Vec3(-.95f,1.65f,.79f),Vec3(-.4f,2.08f,.79f),Vec3(.87f,1.5f,.84f),Vec3(-.6f,2.54f,.25f))
        fun patches(v:GameState.Vehicle):List<Vec3> = when(v) {
            GameState.Vehicle.EXCAVATOR,GameState.Vehicle.DOZER->PATCHES
            GameState.Vehicle.TRUCK->listOf(Vec3(-1.25f,.48f,1.15f),Vec3(1.24f,.48f,1.15f),Vec3(-1.5f,1.4f,1.03f),Vec3(-.7f,1.4f,1.03f),Vec3(0f,1.4f,1.03f),Vec3(.8f,1.5f,.84f),Vec3(1.4f,1.85f,.84f),Vec3(1.1f,2.33f,.15f))
            GameState.Vehicle.CRANE->listOf(Vec3(-1.5f,.48f,1.1f),Vec3(1.5f,.48f,1.1f),Vec3(-1.5f,.82f,1.01f),Vec3(-.6f,.82f,1.01f),Vec3(.4f,.82f,1.01f),Vec3(1.5f,.82f,1.01f),Vec3(1.75f,1.5f,.46f),Vec3(1.75f,2.03f,.1f))
            else->listOf(Vec3(-1.55f,.48f,1.15f),Vec3(-.65f,.48f,1.15f),Vec3(1.55f,.48f,1.15f),Vec3(-.75f,.9f,.96f),Vec3(.35f,.9f,.96f),Vec3(1.1f,1.7f,.94f),Vec3(1.65f,2.1f,.94f),Vec3(1.4f,2.59f,.15f))
        }
        fun camera(g:GameState)=g.vehicleBody(g.wash.vehicle).point.let { camera(it.x,it.z,g.surface(it.x,it.z)) }
        fun camera(x:Float,z:Float,y:Float)=CameraSpec(Vec3(x+5.3f,y+4.2f,z+8f),Vec3(x+.6f,y+1.25f,z))
    }
}
