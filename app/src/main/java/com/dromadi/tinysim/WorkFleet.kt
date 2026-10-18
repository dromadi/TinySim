package com.dromadi.tinysim

import kotlin.math.*

/** Two new machines use the existing collision/river route planner; their jobs stay separate. */
class WorkFleet {
    class Machine(val startX:Float) {
        var x=WorldMap.gameX(startX);var z=19f;var heading=0f;var travel=0f
        var goal:DriveGoal?=null
        var requested=false
        fun reset() { x=WorldMap.gameX(startX);z=19f;heading=0f;travel=0f;goal=null;requested=false }
    }
    val mixer=Machine(10f)
    val wrecker=Machine(40f)
    var concrete=CONCRETE
        private set
    var pad=0
        private set
    var hits=0
        private set
    var drumAngle=0f
        private set
    var mixClock=0f
        private set
    var pouring=false
        private set
    var swing=0f
        private set
    var debris=0f
        private set
    private var pourClock=0f
    private var impacted=false
    val busy get()=mixClock>0f || pouring || swing>0f
    val totalConcrete get()=concrete+pad
    fun machine(v:GameState.Vehicle)=if(v==GameState.Vehicle.MIXER)mixer else wrecker
    fun action(g:GameState,v:GameState.Vehicle):Boolean {
        val m=machine(v)
        if(m.goal!=null || (v==GameState.Vehicle.MIXER && (mixClock>0 || pouring || concrete==0)) || (v==GameState.Vehicle.WRECKER && (swing>0 || hits==3)))return false
        val x=if(v==GameState.Vehicle.MIXER)MIXER_X else WRECKER_X
        if(hypot(WorldMap.worldX(m.x)-x,m.z-YARD_Z)>.35f) {
            g.driveTo(v,WorldMap.gameX(x),YARD_Z);m.requested=true;return true
        }
        m.heading=0f
        if(v==GameState.Vehicle.MIXER)mixClock=2f else { swing=SWING_SECONDS;impacted=false }
        return true
    }
    fun cancelWork(v:GameState.Vehicle) {
        machine(v).requested=false
        if(v==GameState.Vehicle.MIXER) { mixClock=0f;pouring=false;pourClock=0f } else { swing=0f;impacted=false }
    }
    fun cancel() { mixer.goal=null;wrecker.goal=null;mixer.requested=false;wrecker.requested=false;mixClock=0f;pouring=false;pourClock=0f;swing=0f;impacted=false }
    fun reset() { mixer.reset();wrecker.reset();concrete=CONCRETE;pad=0;hits=0;drumAngle=0f;debris=0f;cancel() }
    fun update(g:GameState,dt:Float) {
        debris=max(0f,debris-dt)
        if(mixClock>0 || pouring || mixer.goal!=null || g.selected==GameState.Vehicle.MIXER)drumAngle=(drumAngle+dt*if(mixClock>0)135f else 48f)%360f
        if(mixClock>0) { mixClock=max(0f,mixClock-dt);if(mixClock==0f)pouring=true }
        if(pouring) {
            pourClock+=dt
            if(pourClock>=.48f) {
                pourClock-=.48f
                if(concrete>0 && pad<CONCRETE) { concrete--;pad++;g.transferKind=9;g.transferSerial++ }
                if(concrete==0 || pad==CONCRETE) { pouring=false;g.rewardSerial++;g.celebrating=2.5f }
            }
        }
        if(swing>0) {
            swing=max(0f,swing-dt)
            if(!impacted && swing<=SWING_SECONDS*.45f) {
                impacted=true;hits=(hits+1).coerceAtMost(3);debris=1.1f;g.transferKind=8;g.transferSerial++
                g.rewardSerial++;g.celebrating=if(hits==3)2.2f else .6f
            }
        }
    }
    /** Ball follows a bounded, smooth arc; no free physics impulses can explode or hit people. */
    val ballOffset get()=if(swing>0)sin((1f-swing/SWING_SECONDS)*PI.toFloat())*2.6f else 0f
    fun snapshot()=listOf(mixer.x,mixer.z,mixer.heading,wrecker.x,wrecker.z,wrecker.heading,concrete,pad,hits,drumAngle).joinToString(",")
    fun restore(s:String) {
        val a=s.split(',');require(a.size==10)
        fun f(i:Int)=a[i].toFloat().also { require(it.isFinite()) }
        for((m,offset) in listOf(mixer to 0,wrecker to 3)) {
            m.x=f(offset);m.z=f(offset+1);m.heading=f(offset+2)
            require(m.x in WorldMap.DRIVE_MIN_X..WorldMap.EAST_MAX_X && m.z in WorldMap.DRIVE_MIN_Z..WorldMap.DRIVE_MAX_Z && m.heading in 0f..360f)
        }
        concrete=a[6].toInt();pad=a[7].toInt();hits=a[8].toInt();drumAngle=f(9)
        require(concrete in 0..CONCRETE && pad in 0..CONCRETE && totalConcrete==CONCRETE && hits in 0..3 && drumAngle in 0f..360f)
        cancel()
    }
    companion object {
        const val CONCRETE=8
        const val PAD_X=20f;const val YARD_Z=18.5f;const val MIXER_X=22.8f
        const val TOWER_X=34f;const val WRECKER_X=28.5f
        const val SWING_SECONDS=2.4f
    }
}

/** Friendly attendant. Requests open both gates; occupied/waiting traffic keeps them open. */
class BridgeKeeper {
    var lift=0f
        private set
    var wave=0f
        private set
    var greetings=0
        private set
    private var hold=0f
    val open get()=lift>=.98f
    fun request(built:Boolean) { if(hold<=0f) { greetings++;wave=2.4f };if(built)hold=8f }
    fun update(dt:Float,built:Boolean,occupied:Boolean,waiting:Boolean) {
        wave=max(0f,wave-dt)
        if(occupied || waiting) { if(hold<=0f)request(built);hold=8f } else hold=max(0f,hold-dt)
        val target=if(built && (hold>0 || occupied))1f else 0f
        lift+=(target-lift).coerceIn(-dt*.7f,dt*.85f)
    }
    fun reset() { lift=0f;wave=0f;greetings=0;hold=0f }
    companion object { const val X=46.2f;const val Z=-4.7f }
}
