package com.dromadi.tinysim

import kotlin.math.hypot

/** Three reusable physical props, independent of the conserved earth/sand inventory. */
class BlockYard {
    val x=FloatArray(COUNT) { SUPPLY_X }
    val z=FloatArray(COUNT) { -1f+it*2f }
    /** -1 on the ground; otherwise the unique level in the tower. */
    val level=IntArray(COUNT) { -1 }
    var carried=-1
        private set
    val placed get()=level.count { it>=0 }
    val complete get()=placed==COUNT
    fun pick(id:Int):Boolean {
        if(id !in 0 until COUNT || carried>=0 || level[id]>=0)return false
        carried=id;return true
    }
    fun put(px:Float,pz:Float):Boolean {
        if(carried<0)return false
        val id=carried
        if(atTarget(px,pz)) { level[id]=placed;x[id]=TARGET_X;z[id]=TARGET_Z }
        else { x[id]=px;z[id]=pz }
        carried=-1;return true
    }
    fun reset() { carried=-1;for(i in 0 until COUNT) { x[i]=SUPPLY_X;z[i]=-1f+i*2f;level[i]=-1 } }
    fun snapshot()=(listOf(carried.toString())+(0 until COUNT).map { "${x[it]},${z[it]},${level[it]}" }).joinToString(":")
    fun restore(value:String) {
        val p=value.split(':');require(p.size==COUNT+1)
        val held=p[0].toInt();require(held in -1 until COUNT)
        val xx=FloatArray(COUNT);val zz=FloatArray(COUNT);val ll=IntArray(COUNT)
        for(i in 0 until COUNT) {
            val v=p[i+1].split(',');require(v.size==3)
            xx[i]=v[0].toFloat();zz[i]=v[1].toFloat();ll[i]=v[2].toInt()
            require(validGround(xx[i],zz[i]) && ll[i] in -1 until COUNT)
            if(ll[i]>=0)require(xx[i]==TARGET_X && zz[i]==TARGET_Z)
        }
        val installed=ll.filter { it>=0 }.sorted()
        require(installed==installed.indices.toList() && (held<0 || ll[held]<0))
        xx.copyInto(x);zz.copyInto(z);ll.copyInto(level);carried=held
    }
    companion object {
        const val COUNT=3;const val HEIGHT=.85f;const val WIDTH=1.35f
        const val SUPPLY_X=24f;const val TARGET_X=28f;const val TARGET_Z=6f
        fun atTarget(x:Float,z:Float)=hypot(x-TARGET_X,z-TARGET_Z)<=1.8f
        fun validGround(x:Float,z:Float)=x.isFinite() && z.isFinite() && x in WorldMap.MIN_X+1..WorldMap.MAX_X-1 && z in WorldMap.MIN_Z+1..WorldMap.MAX_Z-1 && x !in WorldMap.RIVER_MIN_X-1..WorldMap.RIVER_MAX_X+1
    }
}
