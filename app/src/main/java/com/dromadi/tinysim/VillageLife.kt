package com.dromadi.tinysim

/** Four household gardens. Persistent ownership is separate from transient walking/waving. */
class VillageLife {
    var welcomed=0; private set
    var planted=0; private set
    val arrival=FloatArray(4)
    val growth=FloatArray(4)
    val wave=FloatArray(4)
    fun homeReady(g:GameState,h:Int)=h in 0..3 && (h*4 until h*4+4).all { g.recovery.state[it]==0 }
    fun outside(g:GameState,h:Int)=h in 0..3 && welcomed and (1 shl h)!=0 && homeReady(g,h) &&
        !g.wind.active && g.wind.strength<=.05f && !g.wind.arena.open
    fun wantsTree(g:GameState,h:Int)=outside(g,h) && planted and (1 shl h)==0
    fun nextGarden(g:GameState)=if(!g.recovery.engaged)-1 else (0..3).firstOrNull { wantsTree(g,it) } ?: -1
    fun welcome(g:GameState,h:Int) {
        if(homeReady(g,h) && g.recovery.damaged and (15 shl (h*4))!=0) {
            if(welcomed and (1 shl h)==0)wave[h]=3f
            welcomed=welcomed or (1 shl h)
        }
    }
    fun migrate(g:GameState) {
        for(h in 0..3)welcome(g,h)
        // Old saves recorded completed districts, but not individual household arrivals.
        if(g.recovery.cycles>0)welcomed=15
    }
    fun update(g:GameState,dt:Float) {
        for(h in 0..3) {
            welcome(g,h)
            if(outside(g,h))arrival[h]=(arrival[h]+dt*.55f).coerceAtMost(1f) else arrival[h]=0f
            wave[h]=(wave[h]-dt).coerceAtLeast(0f)
            if(planted and (1 shl h)!=0)growth[h]=(growth[h]+dt*.7f).coerceAtMost(1f)
        }
    }
    fun plant(g:GameState,h:Int):Boolean {
        if(!g.recovery.engaged || !wantsTree(g,h))return false
        planted=planted or (1 shl h);growth[h]=0f;wave[h]=3f
        g.rewardSerial++;g.celebrating=1.3f
        return true
    }
    fun greet(g:GameState,h:Int):Boolean {
        if(!outside(g,h))return false
        wave[h]=2.5f;return true
    }
    fun reset() { welcomed=0;planted=0;arrival.fill(0f);growth.fill(0f);wave.fill(0f) }
    fun snapshot()="$welcomed,$planted"
    fun restore(s:String,g:GameState) {
        val a=s.split(',');require(a.size==2)
        val w=a[0].toInt();val p=a[1].toInt();require(w in 0..15 && p in 0..15 && p and w==p)
        // A household can only be welcomed by an actual previous recovery, including legacy saves.
        for(h in 0..3)if(w and (1 shl h)!=0)require(g.recovery.cycles>0 || g.recovery.damaged and (15 shl (h*4))!=0)
        reset();welcomed=w;planted=p
        for(h in 0..3)if(p and (1 shl h)!=0)growth[h]=1f
    }
    companion object {
        fun gardenX(h:Int)=RecoverySite.houseX(h)+3.6f
        fun gardenZ(h:Int)=RecoverySite.houseZ(h)+1.2f
        fun residentX(h:Int,t:Float)=RecoverySite.houseX(h)+2.1f*t
        fun residentZ(h:Int,t:Float)=RecoverySite.houseZ(h)+1.4f+.9f*t
    }
}
