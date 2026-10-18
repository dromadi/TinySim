package com.dromadi.tinysim

import kotlin.math.*

/** One conserved set of structural parts, shared by weather, machines, depot and rebuilt houses. */
class RecoverySite {
    // 0 assembled, 1 airborne/falling, 2 rubble, 3 loading pile, 4 truck, 5 depot, 6 crane.
    val state=IntArray(COUNT)
    val x=FloatArray(COUNT) { homeX(it) };val z=FloatArray(COUNT) { homeZ(it) };val y=FloatArray(COUNT) { homeY(it) }
    private val age=FloatArray(COUNT)
    val pads=IntArray(4)
    var concrete=16;private set
    var damaged=0;private set
    var cycles=0;private set
    var engaged=false
    private var job:GameState.Vehicle?=null
    private var stage=0;private var piece=-1;private var clock=0f;private var issuing=false
    var pouring=-1;private set
    val held get()=state.indexOfFirst { it==6 }
    val cargo get()=state.count { it==4 }
    val busy get()=job!=null || state.any { it==1 }
    val level get()=1+listOf(3,8,12).count { Integer.bitCount(damaged)>=it }
    val size get()=.5f+level*.25f
    val complete get()=state.all { it==0 }
    fun enter(g:GameState) { g.stopMovement();g.pipes.active=false;engaged=true;g.wind.place(12f,28f) }
    fun cancel(g:GameState) {
        if(piece>=0 && job==GameState.Vehicle.DOZER && stage==1) { state[piece]=2;y[piece]=0f }
        if(job==GameState.Vehicle.TRUCK)g.tipping=false
        job=null;stage=0;piece=-1;clock=0f;pouring=-1
        for(i in state.indices)if(state[i]==1) { state[i]=2;y[i]=0f }
    }
    fun manualDrive(g:GameState,v:GameState.Vehicle) { if(!issuing && job==v)cancel(g) }
    private fun route(g:GameState,v:GameState.Vehicle,px:Float,pz:Float) {
        issuing=true;try { g.driveTo(v,WorldMap.gameX(px),pz) } finally { issuing=false }
    }
    private fun goal(g:GameState,v:GameState.Vehicle)=when(v) {
        GameState.Vehicle.DOZER->g.dozerGoal;GameState.Vehicle.TRUCK->g.truckGoal
        GameState.Vehicle.MIXER->g.fleet.mixer.goal;else->g.craneGoal
    }
    private fun at(g:GameState,v:GameState.Vehicle,px:Float,pz:Float)=g.vehicleBody(v).point.distance(VehicleNavigation.Point(px,pz))<.7f
    fun nextVehicle():GameState.Vehicle?=when {
        damaged==0->null
        held>=0->GameState.Vehicle.CRANE
        state.any { it==2 }->GameState.Vehicle.DOZER
        state.any { it==3 || it==4 }->GameState.Vehicle.TRUCK
        state.indices.any { state[it]==5 && pads[it/4]<4 }->GameState.Vehicle.MIXER
        state.any { it==5 }->GameState.Vehicle.CRANE
        else->null
    }
    fun action(g:GameState,v:GameState.Vehicle):Boolean {
        if(!engaged)return false
        if(job!=null || g.craneBusy || g.craneGoal!=null)return true
        if(damaged==0) { g.wind.toggle(g);return true }
        when(v) {
            GameState.Vehicle.DOZER->{
                val id=state.indices.filter { state[it]==2 }.minByOrNull { hypot(x[it]-WorldMap.worldX(g.dozerX),z[it]-g.dozerZ) } ?: return true
                piece=id;job=v;stage=0;g.bladeDown=true;route(g,v,x[id]-2.6f,z[id])
            }
            GameState.Vehicle.TRUCK->{
                if(g.load>0)return false
                if(cargo==0 && state.none { it==3 })return true
                job=v;stage=if(cargo>0)2 else 0;clock=0f
                route(g,v,if(cargo>0)DUMP_X else LOAD_X,if(cargo>0)DEPOT_Z else PILE_Z)
            }
            GameState.Vehicle.MIXER->{
                val h=(0..3).firstOrNull { pads[it]<4 && (it*4 until it*4+4).all { id->state[id] in listOf(0,5,6) } && (it*4 until it*4+4).any { id->state[id]==5 } } ?: return true
                piece=h;job=v;stage=0;clock=0f;route(g,v,houseX(h),houseZ(h)-4.5f)
            }
            GameState.Vehicle.CRANE->{
                if(g.craneCargo>0 || g.blocks.carried>=0 || g.pipes.carried>=0)return false
                val id=if(held>=0)held else state.indices.firstOrNull { state[it]==5 && pads[it/4]==4 } ?: return true
                g.recoveryLift(id,held>=0)
            }
            else->return false
        }
        return true
    }
    fun craneTransfer(g:GameState,id:Int,drop:Boolean) {
        if(id !in state.indices)return
        if(!drop && held<0 && state[id]==5)state[id]=6
        else if(drop && held==id && pads[id/4]==4) {
            state[id]=0;x[id]=homeX(id);z[id]=homeZ(id);y[id]=homeY(id)
            g.rewardSerial++;g.celebrating=.7f
            if(complete && damaged!=0) { cycles++;damaged=0;g.celebrating=3f }
        } else return
        g.transferKind=6;g.transferSerial++
    }
    fun update(g:GameState,dt:Float) {
        val w=g.wind;val radius=3f+level*.6f
        for(i in state.indices) {
            if(w.active && w.strength>.4f && job==null && state[i] in 0..2 && hypot(x[i]-w.x,z[i]-w.z)<radius) {
                if(state[i]!=1) { state[i]=1;age[i]=0f;damaged=damaged or (1 shl i) }
                age[i]+=dt;val angle=w.phase+i*1.7f
                x[i]+=(w.x+cos(angle)*(1.3f+i%3*.25f)-x[i])*min(1f,dt*2.2f)
                z[i]+=(w.z+sin(angle)*(1.3f+i%3*.25f)-z[i])*min(1f,dt*2.2f)
                x[i]=x[i].coerceIn(5f,44f);z[i]=z[i].coerceIn(25.5f,38f)
                y[i]+=(1.4f+level*.35f+i%4*.25f-y[i])*min(1f,dt*2f)
            } else if(state[i]==1) { y[i]=max(0f,y[i]-dt*2f);if(y[i]==0f)state[i]=2 }
        }
        val v=job ?: return
        if(goal(g,v)!=null)return
        when(v) {
            GameState.Vehicle.DOZER->{
                val id=piece
                if(stage==0) {
                    if(!at(g,v,x[id]-2.6f,z[id])) { cancel(g);return }
                    stage=1;route(g,v,PILE_X-2.6f,PILE_Z);return
                }
                finishPush(g,id)
            }
            GameState.Vehicle.TRUCK->{
                if(stage==0) { if(!at(g,v,LOAD_X,PILE_Z)) { cancel(g);return };g.truckHeading=0f;stage=1 }
                if(stage==1) {
                    clock+=dt
                    if(clock>=.55f) {
                        clock=0f;val id=state.indexOfFirst { it==3 }
                        if(id>=0 && cargo<8) { state[id]=4;g.transferKind=6;g.transferSerial++ }
                        if(cargo==8 || state.none { it==3 }) { stage=2;route(g,v,DUMP_X,DEPOT_Z) }
                    }
                } else if(stage==2) {
                    if(!at(g,v,DUMP_X,DEPOT_Z)) { cancel(g);return }
                    g.truckHeading=0f;g.tipping=true;stage=3;clock=0f
                } else if(stage==3) {
                    clock+=dt
                    if(clock>.65f && g.bedAngle< -28f) {
                        clock=.3f;val id=state.indexOfFirst { it==4 }
                        if(id>=0) { state[id]=5;x[id]=depotX(id);z[id]=depotZ(id);y[id]=0f;g.transferKind=6;g.transferSerial++ }
                        if(cargo==0) { g.tipping=false;job=null;route(g,v,44f,36f) }
                    }
                }
            }
            GameState.Vehicle.MIXER->{
                if(stage==0) {
                    if(!at(g,v,houseX(piece),houseZ(piece)-4.5f)) { cancel(g);return }
                    g.fleet.mixer.heading=90f;stage=1;pouring=piece;clock=0f
                }
                clock+=dt
                if(clock>.65f) {
                    clock=0f
                    if(pads[piece]<4 && concrete>0) { concrete--;pads[piece]++;g.transferKind=9;g.transferSerial++ }
                    if(pads[piece]==4) { pouring=-1;job=null;route(g,v,4f,27f);g.rewardSerial++;g.celebrating=.8f }
                }
            }
            else->Unit
        }
    }
    /** Called after travel, so the part stays on the real blade rather than teleporting to a pile. */
    fun followBlade(g:GameState) {
        if(job!=GameState.Vehicle.DOZER || stage!=1 || piece<0)return
        val a=g.dozerHeading*PI.toFloat()/180f
        x[piece]=WorldMap.worldX(g.dozerX)+cos(a)*2.6f;z[piece]=g.dozerZ-sin(a)*2.6f;y[piece]=0f
    }
    private fun finishPush(g:GameState,id:Int) {
        followBlade(g)
        if(!at(g,GameState.Vehicle.DOZER,PILE_X-2.6f,PILE_Z)) { cancel(g);return }
        state[id]=3;x[id]=PILE_X+(id%4-.5f)*.35f;z[id]=PILE_Z+(id/4-1.5f)*.3f;y[id]=0f
        job=null;piece=-1;g.transferKind=6;g.transferSerial++
        if(state.none { it==2 })route(g,GameState.Vehicle.DOZER,5f,36f)
    }
    fun reset() { state.fill(0);for(i in state.indices) { x[i]=homeX(i);z[i]=homeZ(i);y[i]=homeY(i) };age.fill(0f);pads.fill(0);concrete=16;damaged=0;cycles=0;engaged=false;job=null;piece=-1;stage=0;pouring=-1;clock=0f }
    fun snapshot()=listOf(damaged,cycles,concrete,if(engaged)1 else 0).joinToString(",")+"#"+pads.joinToString(",")+"#"+state.indices.joinToString(":") { i->listOf(state[i],x[i],z[i],y[i]).joinToString(",") }
    fun restore(s:String) {
        val p=s.split('#');require(p.size==3);val h=p[0].split(',');require(h.size==4)
        damaged=h[0].toInt();cycles=h[1].toInt();concrete=h[2].toInt();require(damaged in 0..65535 && cycles in 0..1000000 && h[3] in listOf("0","1"));engaged=h[3]=="1"
        val pp=p[1].split(',').map { it.toInt() };require(pp.size==4 && pp.all { it in 0..4 } && concrete in 0..16 && concrete+pp.sum()==16);pp.toIntArray().copyInto(pads)
        val cells=p[2].split(':');require(cells.size==COUNT)
        for(i in cells.indices) {
            val a=cells[i].split(',');require(a.size==4);val st=a[0].toInt();require(st in 0..6)
            x[i]=a[1].toFloat();z[i]=a[2].toFloat();y[i]=a[3].toFloat()
            require(x[i].isFinite() && z[i].isFinite() && y[i].isFinite() && x[i] in WorldMap.MIN_X..(WorldMap.WEST_BANK_X+1f) && z[i] in WorldMap.MIN_Z..WorldMap.MAX_Z && y[i] in 0f..6f)
            state[i]=if(st==1)2 else st;if(st==1)y[i]=0f
            if(state[i]==0)require(x[i]==homeX(i) && z[i]==homeZ(i) && y[i]==homeY(i))
            else require(damaged and (1 shl i)!=0)
        }
        require(cargo<=8 && state.count { it==6 }<=1)
    }
    companion object {
        const val COUNT=16;const val PILE_X=33f;const val PILE_Z=36f;const val LOAD_X=39.5f
        const val DEPOT_X=38f;const val DEPOT_Z=26f;const val DUMP_X=42f
        fun houseX(h:Int)=if(h%2==0)12f else 27f
        fun houseZ(h:Int)=if(h<2)28f else 36f
        fun homeX(i:Int)=houseX(i/4)+when(i%4) { 0->-.8f;1->.8f;else->0f }
        fun homeZ(i:Int)=houseZ(i/4)+if(i%4==2).85f else 0f
        fun homeY(i:Int)=if(i%4==3)1.8f else 0f
        fun depotX(i:Int)=DEPOT_X+(i%4-1.5f)*.65f
        fun depotZ(i:Int)=DEPOT_Z+(i/4-1.5f)*.65f
    }
}
