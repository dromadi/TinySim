package com.dromadi.tinysim

import kotlin.math.*

/** Reusable toy arenas. Every prop has a stable identity: ground -> airborne -> contained -> released. */
class TornadoArena {
    enum class Kind(val speed:Float,val reach:Float) { WIND(4.8f,1f),SAND(3.6f,1.3f),ICE(3.2f,1f) }
    enum class Prop { BALL,CONE,CRATE,BARREL,BRICK,ROOF,BUOY,CONTAINER }
    data class Piece(val prop:Prop,val x:Float,val z:Float,val base:Float=0f,val mass:Int=1,val needed:Int=1,val support:Int=-1)
    class Board(val map:Int) {
        val pieces=layout(map)
        val state=IntArray(COUNT)
        val puller=IntArray(COUNT)
        val credited=BooleanArray(COUNT)
        val x=FloatArray(COUNT);val z=FloatArray(COUNT);val y=FloatArray(COUNT)
        val age=FloatArray(COUNT);val frost=FloatArray(COUNT)
        val collected get()=credited.count { it }
        val contained get()=state.count { it==2 }
        val points get()=pieces.indices.sumOf { if(credited[it])pieces[it].mass else 0 }
        val level get()=1+listOf(12,36,68).count { points>=it }
        val complete get()=collected==COUNT
        init { reset() }
        fun reset() { for(i in pieces.indices) { state[i]=0;puller[i]=0;credited[i]=false;x[i]=pieces[i].x;z[i]=pieces[i].z;y[i]=pieces[i].base;age[i]=0f;frost[i]=0f } }
    }
    val boards=Array(3) { Board(it) }
    var buddy=false;private set
    var buddyX=60f;private set
    var buddyZ=12f;private set
    var map=0;private set
    var kind=Kind.WIND;private set
    val board get()=boards[map]
    var open=false;private set
    var running=false;private set
    var strength=0f;private set
    var x=CENTER_X;private set
    var z=CENTER_Z;private set
    var targetX=x;private set
    var targetZ=z;private set
    var phase=0f;private set
    var visualLevel=1f;private set
    var celebrating=0f;private set
    var absorbSerial=0;private set
    var rewardSerial=0;private set
    val scale get()=.5f+visualLevel*.25f
    val radius get()=(1.5f+board.level*.8f)*kind.reach
    fun enter(index:Int=map) {
        require(index in boards.indices)
        pause();map=index;buddyX=60f;buddyZ=12f;open=true;running=true;x=CENTER_X;z=CENTER_Z;targetX=x;targetZ=z;visualLevel=board.level.toFloat()
    }
    fun choose(value:Kind) { kind=value }
    fun toggleBuddy() {
        buddy=!buddy
        if(!buddy)for(i in board.state.indices)if(board.state[i]==1 && board.puller[i]==1) { board.state[i]=0;board.y[i]=0f;board.age[i]=0f }
    }
    fun aim(px:Float,pz:Float) {
        if(!open || !px.isFinite() || !pz.isFinite())return
        targetX=px.coerceIn(MIN_X,MAX_X);targetZ=pz.coerceIn(MIN_Z,MAX_Z)
        running=true
    }
    fun toggle() { if(open)running=!running }
    fun leave() { pause();open=false }
    /** Lifecycle settles flying objects, keeps contained inventory, and never resumes wind automatically. */
    fun pause() {
        running=false;strength=0f
        for(b in boards)for(i in b.state.indices)if(b.state[i]==1 || b.state[i]==3) { b.state[i]=0;b.y[i]=0f;b.age[i]=0f }
    }
    fun reset() { leave();boards.forEach { it.reset() };map=0;kind=Kind.WIND;buddy=false;buddyX=60f;buddyZ=12f;x=CENTER_X;z=CENTER_Z;targetX=x;targetZ=z;visualLevel=1f;celebrating=0f }
    fun replay() { board.reset();visualLevel=1f;celebrating=0f;x=CENTER_X;z=CENTER_Z;targetX=x;targetZ=z;buddyX=60f;buddyZ=12f;if(open)running=true }
    /** Release is not a second reward: credited identities stay credited until a fresh round. */
    fun release():Boolean {
        val b=board;val ids=b.state.indices.filter { b.state[it]!=0 }
        if(ids.isEmpty())return false
        running=false
        ids.forEachIndexed { n,i->
            val a=n*2.399963f;val radius=1f+sqrt(n.toFloat())*.45f
            b.state[i]=3;b.x[i]=(x+cos(a)*radius).coerceIn(MIN_X,MAX_X);b.z[i]=(z+sin(a)*radius).coerceIn(MIN_Z,MAX_Z)
            b.y[i]=1.6f+n%4*.2f;b.age[i]=0f;b.frost[i]=0f
        }
        return true
    }
    fun hintTarget():Pair<Float,Float>? {
        val b=board
        val id=b.state.indices.filter { b.state[it]==0 && b.pieces[it].needed<=b.level+(if(kind==Kind.ICE)1 else 0) }
            .minByOrNull { hypot(b.x[it]-x,b.z[it]-z) } ?: return null
        return b.x[id] to b.z[id]
    }
    fun update(raw:Float) {
        if(!open || !raw.isFinite())return
        val dt=raw.coerceIn(0f,.04f);if(dt==0f)return
        val b=board;val wasLevel=b.level;val wasComplete=b.complete
        celebrating=max(0f,celebrating-dt)
        strength+=((if(running)1f else 0f)-strength).coerceIn(-dt*.8f,dt*.65f)
        phase=(phase+dt*(2f+visualLevel*.15f))%(PI.toFloat()*2f)
        visualLevel+=(b.level-visualLevel)*(1f-exp(-dt*2f))
        if(running) {
            val dx=targetX-x;val dz=targetZ-z;val d=hypot(dx,dz)
            if(d>.001f) { val step=min(1f,dt*kind.speed/d);x+=dx*step;z+=dz*step }
        }
        if(buddy && running) {
            val next=b.state.indices.filter { b.state[it]==0 && b.pieces[it].needed<=b.level+(if(kind==Kind.ICE)1 else 0) }
                .minByOrNull { hypot(b.x[it]-buddyX,b.z[it]-buddyZ)+(if(hypot(b.x[it]-x,b.z[it]-z)<radius)10f else 0f) }
            if(next!=null) {
                val dx=b.x[next]-buddyX;val dz=b.z[next]-buddyZ;val d=hypot(dx,dz)
                if(d>.01f) { val step=min(1f,dt*1.8f/d);buddyX+=dx*step;buddyZ+=dz*step }
            }
        }
        for(i in b.state.indices) {
            val def=b.pieces[i]
            if(b.state[i]==2)continue
            if(b.state[i]==3) { b.y[i]=max(0f,b.y[i]-dt*2.2f);if(b.y[i]==0f)b.state[i]=0;continue }
            val primary=hypot(b.x[i]-x,b.z[i]-z)<=radius
            val helper=buddy && hypot(b.x[i]-buddyX,b.z[i]-buddyZ)<=radius*.8f
            val near=running && strength>.35f && (primary || helper)
            b.frost[i]=(b.frost[i]+if(near && kind==Kind.ICE)dt else -dt*.4f).coerceIn(0f,1f)
            val need=(def.needed-if(b.frost[i]>.7f)1 else 0).coerceAtLeast(1)
            if(b.state[i]==0) {
                if(def.support>=0) {
                    val support=def.support
                    val top=if(b.state[support]==0 && hypot(b.x[i]-b.x[support],b.z[i]-b.z[support])<1.5f)b.y[support]+(def.base-b.pieces[support].base) else 0f
                    val target=min(b.y[i],min(def.base,top))
                    b.y[i]+=(target-b.y[i]).coerceIn(-dt*2.2f,dt*2.2f)
                }
                if(near && b.level>=need) { b.state[i]=1;b.puller[i]=if(primary)0 else 1;b.age[i]=0f }
            }
            if(b.state[i]==1) {
                if(!running || strength<.2f) {
                    b.y[i]=max(0f,b.y[i]-dt*2f)
                    if(b.y[i]==0f) { b.state[i]=0;b.age[i]=0f }
                    continue
                }
                b.age[i]+=dt
                val cx=if(b.puller[i]==1)buddyX else x;val cz=if(b.puller[i]==1)buddyZ else z
                val a=phase*1.7f+i*2.4f;val orbit=(.6f+visualLevel*.27f)*(1f-(b.age[i]/2f).coerceIn(0f,.85f))
                val ease=1f-exp(-dt*(3f+strength))
                b.x[i]+=(cx+cos(a)*orbit-b.x[i])*ease;b.z[i]+=(cz+sin(a)*orbit-b.z[i])*ease
                b.x[i]=b.x[i].coerceIn(MIN_X,MAX_X);b.z[i]=b.z[i].coerceIn(MIN_Z,MAX_Z)
                b.y[i]+=(1.2f+visualLevel*.6f+i%3*.2f-b.y[i])*min(1f,dt*2f)
                if(b.age[i]>=1.4f && hypot(b.x[i]-cx,b.z[i]-cz)<1.4f+visualLevel*.2f) {
                    b.state[i]=2;b.y[i]=0f;b.credited[i]=true;absorbSerial++
                }
            }
        }
        if(b.level>wasLevel || (!wasComplete && b.complete)) { celebrating=2.5f;rewardSerial++ }
    }
    fun snapshot()=listOf(map.toString(),kind.ordinal.toString(),if(buddy)"1" else "0").joinToString(",")+"#"+boards.joinToString("#") { b->
        b.state.indices.joinToString(":") { i->listOf(b.state[i],if(b.credited[i])1 else 0,b.x[i],b.z[i],b.y[i],b.puller[i]).joinToString(",") }
    }
    fun restore(value:String) {
        val groups=value.split('#');require(groups.size==4)
        val header=groups[0].split(',');require(header.size==3 && header[2] in listOf("0","1"))
        val selectedMap=header[0].toInt();val selectedKind=header[1].toInt();require(selectedMap in 0..2 && selectedKind in Kind.entries.indices)
        val parsed=Array(3) { Board(it) }
        for(m in 0..2) {
            val cells=groups[m+1].split(':');require(cells.size==COUNT)
            val b=parsed[m]
            for(i in cells.indices) {
                val a=cells[i].split(',');require(a.size==6 && a[1] in listOf("0","1"))
                val state=a[0].toInt();require(state in 0..3);b.puller[i]=a[5].toInt();require(b.puller[i] in 0..1)
                fun f(j:Int)=a[j].toFloat().also { require(it.isFinite()) }
                b.x[i]=f(2);b.z[i]=f(3);b.y[i]=f(4)
                require(b.x[i] in MIN_X..MAX_X && b.z[i] in MIN_Z..MAX_Z && b.y[i] in 0f..5f)
                b.credited[i]=a[1]=="1";require(state!=2 || b.credited[i])
                b.state[i]=if(state==1 || state==3)0 else state;if(state==1 || state==3)b.y[i]=0f
            }
        }
        leave();map=selectedMap;kind=Kind.entries[selectedKind];buddy=header[2]=="1"
        for(m in 0..2) { val source=parsed[m];val target=boards[m];source.state.copyInto(target.state);source.puller.copyInto(target.puller);source.credited.copyInto(target.credited);source.x.copyInto(target.x);source.z.copyInto(target.z);source.y.copyInto(target.y);target.age.fill(0f);target.frost.fill(0f) }
        visualLevel=board.level.toFloat()
    }
    companion object {
        const val COUNT=48
        const val CENTER_X=64.5f;const val CENTER_Z=17f
        const val MIN_X=58f;const val MAX_X=71f;const val MIN_Z=11f;const val MAX_Z=23f
        fun layout(map:Int):List<Piece> {
            require(map in 0..2)
            val out=mutableListOf<Piece>()
            for(i in 0 until 32) {
                val row=i/8;val col=i%8
                val a=i*2.399963f;val radius=2.2f+(i%5)*.65f
                val x=if(map==0)CENTER_X+cos(a)*radius else 59.2f+col*1.5f+if(map==2 && row%2==0).35f else 0f
                val z=if(map==0)16f+sin(a)*radius else 11.8f+row*2.7f
                val type=when { i<16->if(map==2)Prop.BUOY else if(i%2==0 && map==0)Prop.BALL else Prop.CONE;i<24->Prop.CRATE;else->Prop.BARREL }
                out+=Piece(type,x,z,mass=if(i<16)1 else if(i<24)2 else 3,needed=if(i<16)1 else 2)
            }
            for(h in 0..3) {
                val x=60f+h*3f;val z=22f;val start=out.size
                val type=if(map==2)Prop.CONTAINER else Prop.BRICK
                out+=Piece(type,x-.45f,z,mass=4,needed=3)
                out+=Piece(type,x+.45f,z,mass=4,needed=3)
                out+=Piece(type,x,z,.75f,mass=4,needed=3,support=start)
                out+=Piece(if(map==2)Prop.CONTAINER else Prop.ROOF,x,z,1.45f,mass=5,needed=4,support=start+2)
            }
            require(out.size==COUNT);return out
        }
    }
}
