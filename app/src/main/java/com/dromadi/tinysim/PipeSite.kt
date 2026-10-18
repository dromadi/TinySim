package com.dromadi.tinysim

import kotlin.math.*

/** A six-metre utility trench. Soil is always in earth, bucket or a visible spoil heap. */
class PipeSite {
    var active=false
    var installed=0
        private set
    var filled=0
        private set
    var carried=-1
        private set
    private var dig=-1
    private var phase=0
    private var clock=0f
    private var fill=-1
    val busy get()=dig>=0 || fill>=0
    val diggingCell get()=if(dig>=0 && phase==0) nextCell else null
    private var nextCell=0
    fun dug(g:GameState,i:Int)=cells(i).all { g.earth[it]<=2 && g.sand[it]==0 }
    fun open(g:GameState)= (0..5).all { dug(g,it) }
    fun hasPipe(i:Int)=installed and (1 shl i)!=0
    fun covered(i:Int)=filled and (1 shl i)!=0
    val complete get()=filled==7
    fun next(g:GameState):Int = (0..5).firstOrNull { !dug(g,it) && !hasPipe(it/2) }
        ?: (0..2).firstOrNull { !hasPipe(it) }?.let { it+6 }
        ?: (0..2).firstOrNull { !covered(it) }?.let { it+9 } ?: -1
    fun reset() { active=false;installed=0;filled=0;carried=-1;cancel() }
    fun cancel() { dig=-1;fill=-1;phase=0;clock=0f }
    fun tap(g:GameState,id:Int):Boolean {
        if(!active || busy || g.craneBusy || g.craneGoal!=null || id !in 0..11)return false
        if(id<6) {
            if(hasPipe(id/2) || dug(g,id) || g.bucket>0)return false
            g.stopMovement();phase=0;clock=0f
            val x=x(id);val z=Z
            if(hypot(WorldMap.worldX(g.excavatorX)-x,g.excavatorZ-z) !in 2f..5.3f)
                g.driveTo(GameState.Vehicle.EXCAVATOR,WorldMap.gameX(x-3.8f),z-1.5f)
            dig=id;return true
        }
        if(id<9) {
            val i=id-6
            if(carried==i)return g.installPipe(i)
            if(!open(g) || hasPipe(i) || carried>=0 || g.bucket>0 || g.craneCargo>0 || g.blocks.carried>=0)return false
            return g.installPipe(i)
        }
        val i=id-9
        if(installed!=7 || covered(i) || g.bucket>0)return false
        g.stopMovement();clock=0f;g.bladeDown=false
        g.driveTo(GameState.Vehicle.DOZER,WorldMap.gameX(pipeX(i)-2.5f),SPOIL_Z)
        fill=i;return true
    }
    fun update(g:GameState,dt:Float) {
        if(dig>=0) {
            if(g.excavatorGoal!=null)return
            val x=x(dig)
            val z=if(phase==0)Z else SPOIL_Z
            val d=hypot(WorldMap.worldX(g.excavatorX)-x,g.excavatorZ-z)
            if(d !in 1.4f..7.4f) { cancel();g.moveArm(g.armX,300f);return }
            if(phase==0) {
                nextCell=cells(dig).firstOrNull { g.sand[it]>0 || g.earth[it]>2 } ?: run { finishDig(g);return }
                g.aimAt(WorldMap.cellX(nextCell),WorldMap.cellZ(nextCell),g.pipeDigY(WorldMap.cellX(nextCell),WorldMap.cellZ(nextCell)))
            } else g.aimAt(x,SPOIL_Z,320f)
            if(abs(g.armX-g.targetX)>2f || abs(g.armY-g.targetY)>2f) { clock=0f;return }
            clock+=dt
            if(clock<.35f)return
            clock=0f
            if(phase==0) {
                if(!g.scoop()) { cancel();g.moveArm(g.armX,300f);return }
                phase=1
            } else {
                val n=g.bucket
                if(n>0) { g.sand[WorldMap.cell(x,SPOIL_Z)]+=n;g.bucket=0;g.transferKind=3;g.transferSerial++ }
                if(dug(g,dig))finishDig(g) else phase=0
            }
        }
        if(fill>=0) {
            if(g.dozerGoal!=null)return
            val i=fill
            if(hypot(WorldMap.worldX(g.dozerX)-(pipeX(i)-2.5f),g.dozerZ-SPOIL_Z)>.7f) { cancel();return }
            g.bladeDown=true
            clock+=dt
            if(clock<.25f)return
            clock=0f
            val target=(i*2..i*2+1).flatMap { cells(it).toList() }.firstOrNull { g.earth[it]<Terrain.INITIAL_EARTH || g.sand[it]>0 }
            if(target==null) {
                filled=filled or (1 shl i);fill=-1;if(complete)active=false;g.rewardSerial++;g.celebrating=if(complete)2.5f else .7f;return
            }
            // The blade sweeps soil from the adjacent spoil strip, one real unit at a time.
            val pile=(0..5).map { WorldMap.cell(x(it),SPOIL_Z) }.filter { g.sand[it]>0 }.minByOrNull { abs(WorldMap.cellX(it)-pipeX(i)) }
            if(g.sand[target]>0 && g.earth[target]<4) { g.sand[target]--;g.earth[target]++ }
            else if(g.earth[target]<4 && pile!=null) { g.sand[pile]--;g.earth[target]++ }
            else { cancel();return }
            g.flattened++;g.transferKind=4;g.transferSerial++
        }
    }
    private fun finishDig(g:GameState) { dig=-1;clock=0f;g.moveArm(g.armX,300f);g.rewardSerial++;g.celebrating=.65f }
    fun pick(i:Int):Boolean { if(i !in 0..2 || carried>=0 || hasPipe(i))return false;carried=i;return true }
    fun place(g:GameState,i:Int):Boolean {
        if(carried!=i || !open(g))return false
        installed=installed or (1 shl i);carried=-1;return true
    }
    fun snapshot()="${if(active)1 else 0},$installed,$filled,$carried"
    fun restore(s:String) {
        val a=s.split(',').map { it.toInt() };require(a.size==4 && a[0] in 0..1 && a[1] in 0..7 && a[2] in 0..7 && a[3] in -1..2)
        require(a[2] and a[1]==a[2] && (a[3]<0 || a[1] and (1 shl a[3])==0))
        active=a[0]==1;installed=a[1];filled=a[2];carried=a[3];cancel()
    }
    companion object {
        const val Z=5f
        const val SPOIL_Z=8.5f
        fun x(i:Int)=10.5f+i
        fun pipeX(i:Int)=11f+i*2
        fun cells(i:Int)=intArrayOf(WorldMap.cell(x(i),4.5f),WorldMap.cell(x(i),5.5f))
        fun markerX(id:Int)=if(id<6)x(id) else pipeX((id-6)%3)
        fun markerZ(id:Int)=if(id<6)6.9f+(id%2)*1.4f else 9.7f
    }
}
