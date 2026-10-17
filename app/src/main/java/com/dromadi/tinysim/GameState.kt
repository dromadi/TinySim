package com.dromadi.tinysim

import kotlin.math.*

/** Deterministic, bounded simulation. Sand units are conserved, including the source. */
class GameState {
    enum class Vehicle { EXCAVATOR, TRUCK, DOZER }
    var selected = Vehicle.EXCAVATOR
    var truckX = 620f
    var dozerX = 1000f
    var armX = 365f
    var armY = 340f
    var targetX = armX
    var targetY = armY
    var bucket = 0
    var load = 0
    var source = 36
    val sand = IntArray(WorldMap.CELL_COUNT)
    var delivered = 0
    var flattened = 0
    var missionMode = false
    var mission = 0
    var celebrating = 0f
    var bedAngle = 0f
    var tipping = false
    var truckZ=0f
    var dozerZ=2.25f
    var truckHeading=0f
    var dozerHeading=0f
    var truckTravel=0f
    var dozerTravel=0f
    var truckGoal:DriveGoal?=null
        private set
    var dozerGoal:DriveGoal?=null
        private set
    fun driveTo(vehicle:Vehicle,x:Float,z:Float) {
        if(!x.isFinite() || !z.isFinite())return
        val goal=DriveGoal(x.coerceIn(WorldMap.DRIVE_MIN_X,WorldMap.DRIVE_MAX_X),z.coerceIn(WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z))
        when(vehicle) {
            Vehicle.TRUCK -> { truckGoal=goal;tipping=false }
            Vehicle.DOZER -> dozerGoal=goal
            else -> Unit
        }
    }
    fun stopMovement() { truckGoal=null;dozerGoal=null }
    val driving get()=truckGoal!=null || dozerGoal!=null
    var bladeDown = true
    var dozerFacing = 1
    var rewardSerial = 0
    var transferSerial = 0
    var transferKind=0
    private var tipClock = 0f
    private var pendingReward = false
    val totalSand get() = source + bucket + load + sand.sum()
    val missionProgress get() = when(mission) { 0 -> load.coerceAtMost(4); 1 -> delivered.coerceAtMost(4); else -> flattened.coerceAtMost(4) }
    fun moveArm(x: Float, y: Float) {
        if(!x.isFinite() || !y.isFinite())return
        val point=ArmRig.limit(x,y)
        targetX=point.first;targetY=point.second
    }
    fun scoop(): Boolean {
        if (bucket == 0 && source > 0 && armX < 460 && armY > 380) {
            bucket = min(2, source); source -= bucket; transferKind=1;transferSerial++; return true
        }
        return false
    }
    fun pour(): Boolean {
        if (bucket > 0 && abs(armX - truckX) < 110 && abs(truckZ)<1.35f && armY < 390 && load < 8 && !tipping) {
            val n = min(bucket, 8-load); load += n; bucket -= n;transferKind=2;transferSerial++; checkMission(); return true
        }
        return false
    }
    fun action() {
        when(selected) {
            Vehicle.EXCAVATOR -> if (bucket == 0) {
                if (!scoop()) moveArm(365f, 440f)
            } else {
                if (!pour()) {
                    if(abs(truckX-620f)>80f || abs(truckZ)>1.3f)driveTo(Vehicle.TRUCK,620f,0f)
                    moveArm(620f,300f)
                }
            }
            Vehicle.TRUCK -> { truckGoal=null;tipping = !tipping }
            Vehicle.DOZER -> { bladeDown = !bladeDown }
        }
    }
    fun update(rawDt: Float) {
        if(!rawDt.isFinite())return
        val dt = rawDt.coerceIn(0f, .04f)
        celebrating = max(0f, celebrating-dt)
        val smoothing = 1f-exp(-8f*dt)
        armX += (targetX-armX)*smoothing; armY += (targetY-armY)*smoothing
        advanceTruck(dt)
        advanceDozer(dt)
        bedAngle += ((if(tipping) -42f else 0f)-bedAngle)*smoothing
        if(tipping && bedAngle < -28 && load > 0) {
            tipClock += dt
            if(tipClock >= .18f) {
                tipClock=0f; load--; delivered++
                val a=truckHeading*PI.toFloat()/180f
                sand[WorldMap.cell(WorldMap.worldX(truckX)-cos(a)*1.8f,truckZ+sin(a)*1.8f)]++
                transferKind=3;transferSerial++; checkMission()
            }
        }
    }
    private fun heading(current:Float,dx:Float,dz:Float,dt:Float):Float {
        val desired=atan2(-dz,dx)*180f/PI.toFloat()
        val delta=((desired-current+540f)%360f)-180f
        return (current+delta.coerceIn(-240f*dt,240f*dt)+360f)%360f
    }
    private fun advanceTruck(dt:Float) {
        val goal=truckGoal ?: return
        if(tipping || bedAngle < -8f)return
        val dx=(goal.x-truckX)/60f;val dz=goal.z-truckZ;val distance=hypot(dx,dz)
        if(distance<.025f) { truckX=goal.x;truckZ=goal.z;truckGoal=null;return }
        truckHeading=heading(truckHeading,dx,dz,dt)
        val step=min(distance,3.5f*dt)
        truckX+=dx/distance*step*60f;truckZ+=dz/distance*step;truckTravel+=step
    }
    private fun advanceDozer(dt:Float) {
        val goal=dozerGoal ?: return
        val dx=(goal.x-dozerX)/60f;val dz=goal.z-dozerZ;val distance=hypot(dx,dz)
        if(distance<.025f) { dozerX=goal.x;dozerZ=goal.z;dozerGoal=null;return }
        dozerHeading=heading(dozerHeading,dx,dz,dt);dozerFacing=if(dx<0)-1 else 1
        val step=min(distance,2.8f*dt);if(step<=0f)return
        dozerX+=dx/distance*step*60f;dozerZ+=dz/distance*step;dozerTravel+=step
        if(bladeDown) {
            val ux=dx/distance;val uz=dz/distance
            val touched=HashSet<Int>()
            for(side in floatArrayOf(-.65f,0f,.65f)) {
                val index=WorldMap.cell(WorldMap.worldX(dozerX)+ux*1.8f-uz*side,dozerZ+uz*1.8f+ux*side)
                if(!touched.add(index))continue
                val next=WorldMap.neighbor(index,ux,uz)
                if(next!=index && sand[index]>sand[next]+1) {
                    sand[index]--;sand[next]++;flattened++;checkMission()
                }
            }
        }
    }
    private fun checkMission() {
        if (missionMode && missionProgress >= 4 && !pendingReward) {
            celebrating=2.5f; rewardSerial++; pendingReward=true
        }
    }
    fun nextMission() {
        if (pendingReward && celebrating <= 0f) { mission=(mission+1)%3; pendingReward=false
            if(mission==0) { delivered=0; flattened=0 }
            checkMission()
        }
    }
    fun toggleMode() { missionMode=!missionMode; mission=0; delivered=0; flattened=0; pendingReward=false; celebrating=0f; checkMission() }
    fun reset() { truckX=620f; dozerX=1000f; armX=365f; armY=340f; targetX=armX; targetY=armY
        bucket=0; load=0; source=36; sand.fill(0); delivered=0; flattened=0; mission=0
        tipping=false;stopMovement();truckZ=0f;dozerZ=2.25f;truckHeading=0f;dozerHeading=0f;truckTravel=0f;dozerTravel=0f; bladeDown=true; dozerFacing=1; bedAngle=0f; celebrating=0f; tipClock=0f; pendingReward=false }
    fun snapshot():String="5|"+listOf(truckX,dozerX,armX,armY,bucket,load,source,delivered,flattened,mission,if(missionMode)1 else 0,selected.ordinal,if(bladeDown)1 else 0,if(tipping)1 else 0,dozerFacing,bedAngle,truckZ,dozerZ,truckHeading,dozerHeading).joinToString(",")+";"+sand.joinToString(",")
    fun restore(value:String?) {
        if(value==null)return
        runCatching {
            val modern=value.startsWith("5|")
            val parts=value.removePrefix("5|").split(';');require(parts.size==2)
            val v=parts[0].split(',');val piles=parts[1].split(',').map { it.toInt().also { n->require(n in 0..36) } }
            require(if(modern)v.size==20 && piles.size==WorldMap.CELL_COUNT else v.size in listOf(12,16) && piles.size==24)
            fun finite(index:Int,min:Float,max:Float)=v[index].toFloat().also { require(it.isFinite()) }.coerceIn(min,max)
            reset()
            truckX=finite(0,WorldMap.DRIVE_MIN_X,WorldMap.DRIVE_MAX_X);dozerX=finite(1,WorldMap.DRIVE_MIN_X,WorldMap.DRIVE_MAX_X)
            val end=ArmRig.limit(finite(2,285f,825f),finite(3,220f,466f));armX=end.first;armY=end.second;targetX=armX;targetY=armY
            bucket=v[4].toInt().coerceIn(0,2);load=v[5].toInt().coerceIn(0,8);source=v[6].toInt().coerceIn(0,36)
            delivered=v[7].toInt().coerceAtLeast(0);flattened=v[8].toInt().coerceAtLeast(0);mission=v[9].toInt().coerceIn(0,2)
            missionMode=v[10]=="1";selected=Vehicle.entries[v[11].toInt().coerceIn(0,2)]
            if(v.size>=16) { bladeDown=v[12]=="1";tipping=v[13]=="1";dozerFacing=if(v[14].toInt()<0)-1 else 1;bedAngle=finite(15,-42f,0f) }
            if(modern) {
                truckZ=finite(16,WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z);dozerZ=finite(17,WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z)
                truckHeading=finite(18,0f,360f);dozerHeading=finite(19,0f,360f)
                piles.forEachIndexed { i,n->sand[i]=n }
            } else {
                dozerHeading=if(dozerFacing<0)180f else 0f
                piles.forEachIndexed { i,n->sand[WorldMap.cell(WorldMap.worldX(495f+i*30),1.35f)]+=n }
            }
            require(totalSand==36);checkMission()
        }.onFailure { reset() }
    }
}
