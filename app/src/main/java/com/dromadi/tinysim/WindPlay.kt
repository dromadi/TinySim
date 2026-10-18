package com.dromadi.tinysim

import kotlin.math.*

/** Bounded touch toy: atomic sand transfers conserve material; visual lift never owns inventory. */
class WindPlay {
    val arena=TornadoArena()
    var active=false; private set
    var strength=0f; private set
    var x=24f; private set
    var z=0f; private set
    var targetX=x; private set
    var targetZ=z; private set
    var prepared=false; private set
    var phase=0f; private set
    val lift=FloatArray(BlockYard.COUNT)
    var moved=0; private set
    var trailX=x; private set
    var trailZ=z; private set
    var trailAge=1f; private set
    private var clock=0f
    val nearBridge get()=strength>.2f && hypot(x-48f,z-WorldMap.BRIDGE_Z)<7f
    fun toggle(g:GameState) {
        if(active) { active=false;return }
        g.stopMovement();g.pipes.active=false
        if(!prepared) {
            // A real six-unit delivery from the existing source, never newly created sand.
            if(g.source>=6) { g.source-=6;g.sand[WorldMap.cell(24.5f,-.5f)]+=6 }
            prepared=true
        }
        active=true
    }
    fun aim(px:Float,pz:Float) {
        if(!active || !px.isFinite() || !pz.isFinite())return
        targetX=px.coerceIn(-8f,47f);targetZ=pz.coerceIn(-2.8f,21.5f)
    }
    fun halt() { arena.pause();active=false;strength=0f;lift.fill(0f);clock=0f;trailAge=1f }
    fun reset() { halt();arena.reset();prepared=false;x=24f;z=0f;targetX=x;targetZ=z;phase=0f;moved=0 }
    fun update(g:GameState,dt:Float) {
        strength+=( (if(active)1f else 0f)-strength).coerceIn(-dt*.65f,dt*.5f)
        phase=(phase+dt*2.2f)%(2f*PI.toFloat());trailAge+=dt
        if(active) {
            val dx=targetX-x;val dz=targetZ-z;val d=hypot(dx,dz)
            if(d>.001f) { val step=min(1f,dt*4f/d);x+=dx*step;z+=dz*step }
        }
        for(i in lift.indices) {
            val ground=g.blocks.level[i]<0 && g.blocks.carried!=i
            val caught=active && strength>.35f && ground && hypot(g.blocks.x[i]-x,g.blocks.z[i]-z)<4f
            val h=if(caught)(1.1f+i*.45f)*strength else 0f
            lift[i]+=(h-lift[i]).coerceIn(-dt*1.4f,dt*1.1f)
            if(caught) {
                val a=phase+i*2f;val radius=1.35f+i*.15f
                val nx=g.blocks.x[i]+(x+cos(a)*radius-g.blocks.x[i])*min(1f,dt*1.8f)
                val nz=g.blocks.z[i]+(z+sin(a)*radius-g.blocks.z[i])*min(1f,dt*1.8f)
                if(BlockYard.validGround(nx,nz)) { g.blocks.x[i]=nx;g.blocks.z[i]=nz }
            }
        }
        if(!active || strength<.45f)return
        clock+=dt
        if(clock<.12f)return
        clock-=.12f
        val destination=WorldMap.cell(x+cos(phase)*.65f,z+sin(phase)*.65f)
        fun allowed(i:Int)=Terrain.diggable(i) && !Terrain.foundation(i) && WorldMap.cellX(i)<47f &&
            !(WorldMap.cellX(i) in 9f..17f && WorldMap.cellZ(i) in 3f..12f)
        if(!allowed(destination))return
        val source=g.sand.indices.filter { g.sand[it]>0 && it!=destination && allowed(it) &&
            hypot(WorldMap.cellX(it)-x,WorldMap.cellZ(it)-z)<3.3f }.maxByOrNull { hypot(WorldMap.cellX(it)-x,WorldMap.cellZ(it)-z) } ?: return
        g.sand[source]--;g.sand[destination]++;moved++
        trailX=WorldMap.cellX(source);trailZ=WorldMap.cellZ(source);trailAge=0f
    }
    // Only persistent content and a valid resting position are saved. Weather always restores stopped.
    fun snapshot()=listOf(if(prepared)1 else 0,x,z).joinToString(",")
    fun restore(s:String) {
        val a=s.split(',');require(a.size==3 && a[0] in listOf("0","1"))
        val px=a[1].toFloat();val pz=a[2].toFloat()
        require(px.isFinite() && pz.isFinite() && px in -8f..47f && pz in -2.8f..21.5f)
        halt();prepared=a[0]=="1";x=px;z=pz;targetX=x;targetZ=z
    }
}
