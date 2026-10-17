package com.dromadi.tinysim

import kotlin.math.*

/** Deterministic, bounded simulation. Sand units are conserved, including the source. */
class GameState {
    enum class Vehicle { EXCAVATOR, TRUCK, DOZER, CRANE }
    var selected = Vehicle.EXCAVATOR
    // Arm coordinates stay local to the excavator; its pose moves/rotates the rig in 3D.
    var excavatorX=200f
    var excavatorZ=0f
    var excavatorHeading=0f
    var excavatorTravel=0f
    var excavatorGoal:DriveGoal?=null
        private set
    private var digTarget:Pair<Float,Float>?=null
    val earth=IntArray(WorldMap.CELL_COUNT) { Terrain.INITIAL_EARTH }
    var houseFill=0
        private set
    var houseStage=0
        private set
    var buildClock=0f
        private set
    val foundationProgress get()=earth.indices.count { Terrain.foundation(it) && earth[it]<Terrain.INITIAL_EARTH }.coerceAtMost(4)
    val houseReady get()=foundationProgress>=4
    val totalMaterial get()=totalSand+earth.sum()
    fun surface(x:Float,z:Float)=Terrain.height(earth,sand,x,z,bridgeOpen)
    // Bridge project: 8 units delivered to the west-bank yard, then the crane builds 3 stages.
    var bridgeFill=0
        private set
    var bridgeStage=0
        private set
    var bridgeClock=0f
        private set
    val bridgeOpen get()=bridgeStage>=3
    private val driveMaxX get()=if(bridgeOpen)WorldMap.EAST_MAX_X else WorldMap.DRIVE_MAX_X
    // Mobile crane with a clamshell grab. Angle is the boom's world direction in degrees
    // (atan2 of the z/x offset), reach is the horizontal hook distance, hookY its height above ground.
    var craneX=WorldMap.gameX(CRANE_SPOT_X)
    var craneZ=CRANE_SPOT_Z
    var craneHeading=0f
    var craneTravel=0f
    var craneGoal:DriveGoal?=null
        private set
    var craneAngle=-90f
    var craneReach=4.7f
    var hookY=HOOK_REST
        private set
    var craneCargo=0
    private class CraneTask(val x:Float,val z:Float,val drop:Boolean) { var lowered=false }
    private var craneTask:CraneTask?=null
    private var pendingCrane:Pair<Float,Float>?=null
    private var pendingBridge=false
    val craneBusy get()=craneTask!=null
    /** Where the hook currently hangs over the ground (world metres). */
    val hookPoint get()=craneWorldX+cos(craneAngle*PI.toFloat()/180f)*craneReach to craneZ+sin(craneAngle*PI.toFloat()/180f)*craneReach
    val craneWorldX get()=WorldMap.worldX(craneX)
    private val craneAtSpot get()=hypot(craneWorldX-CRANE_SPOT_X,craneZ-CRANE_SPOT_Z)<1f
    /**
     * A touch on the ground for the crane: within reach the boom swings there and the grab picks
     * up sand (or drops its cargo); further away the crane first drives within reach.
     */
    fun craneTap(x:Float,z:Float):Boolean {
        if(!x.isFinite() || !z.isFinite() || bridgeClock>0f)return false
        val d=hypot(x-craneWorldX,z-craneZ)
        if(d in CRANE_MIN_REACH..CRANE_MAX_REACH) { craneGoal=null;pendingCrane=null;craneTask=CraneTask(x,z,craneCargo>0);return true }
        // Park on the line toward the target, comfortably inside the reach.
        val ux=if(d<1e-3f)1f else (craneWorldX-x)/d;val uz=if(d<1e-3f)0f else (craneZ-z)/d
        driveTo(Vehicle.CRANE,WorldMap.gameX(x+ux*6f),z+uz*6f)
        pendingCrane=x to z;return true
    }
    /** The crane lifts the next bridge piece from the yard; it drives to its work spot first. */
    private fun craneBuildsBridge():Boolean {
        if(!bridgeReady || bridgeClock>0f)return false
        if(!craneAtSpot) { driveTo(Vehicle.CRANE,WorldMap.gameX(CRANE_SPOT_X),CRANE_SPOT_Z);pendingBridge=true;return true }
        return buildBridge()
    }
    private fun craneAction() {
        if(craneTask!=null || craneGoal!=null)return
        if(craneBuildsBridge())return
        if(craneCargo>0) {
            val truck=WorldMap.worldX(truckX) to truckZ
            if(hypot(truck.first-craneWorldX,truck.second-craneZ)<=CRANE_MAX_REACH)craneTap(truck.first,truck.second)
            else { val (hx,hz)=hookPoint;craneTap(hx,hz) }
            return
        }
        val reachable=sand.indices.filter { sand[it]>0 && !WorldMap.river(it) && (bridgeOpen || !WorldMap.east(WorldMap.cellX(it))) }
        val pile=reachable.minByOrNull { hypot(WorldMap.cellX(it)-craneWorldX,WorldMap.cellZ(it)-craneZ) }
        when {
            pile!=null -> craneTap(WorldMap.cellX(pile),WorldMap.cellZ(pile))
            source>0 -> craneTap(WorldMap.worldX(365f),0f)
        }
    }
    private fun advanceCrane(dt:Float) {
        val goal=craneGoal
        if(goal!=null) {
            val dx=(goal.x-craneX)/60f;val dz=goal.z-craneZ;val distance=hypot(dx,dz)
            if(distance<.025f) {
                craneX=goal.x;craneZ=goal.z;craneGoal=nextLeg(Vehicle.CRANE)
                if(craneGoal==null) {
                    pendingCrane?.let { (x,z)->pendingCrane=null;if(hypot(x-craneWorldX,z-craneZ) in CRANE_MIN_REACH..CRANE_MAX_REACH)craneTask=CraneTask(x,z,craneCargo>0) }
                    if(pendingBridge) { pendingBridge=false;buildBridge() }
                }
                return
            }
            craneHeading=heading(craneHeading,dx,dz,dt)
            val step=min(distance,2.6f*dt)
            craneX+=dx/distance*step*60f;craneZ+=dz/distance*step;craneTravel+=step
            return
        }
        val task=craneTask
        if(task==null) { hookY+=(HOOK_REST-hookY)*min(1f,dt*3f);return }
        val dx=task.x-craneWorldX;val dz=task.z-craneZ
        val wantAngle=atan2(dz,dx)*180f/PI.toFloat()
        val wantReach=hypot(dx,dz).coerceIn(CRANE_MIN_REACH,CRANE_MAX_REACH)
        val turn=((wantAngle-craneAngle+540f)%360f)-180f
        if(abs(turn)>.5f || abs(wantReach-craneReach)>.03f) {
            craneAngle+=turn.coerceIn(-75f*dt,75f*dt);craneReach+=(wantReach-craneReach).coerceIn(-3f*dt,3f*dt)
            hookY+=(HOOK_SWING-hookY)*min(1f,dt*4f);return
        }
        craneAngle=wantAngle;craneReach=wantReach
        val ground=surface(task.x,task.z)+.45f
        if(!task.lowered) {
            hookY=max(ground,hookY-3f*dt)
            if(hookY<=ground+1e-3f) { task.lowered=true;craneTransfer(task) }
        } else {
            hookY=min(HOOK_REST,hookY+3f*dt)
            if(hookY>=HOOK_REST-1e-3f)craneTask=null
        }
    }
    /** At the bottom of the drop: the grab closes on sand, or opens over the truck/yard/ground. */
    private fun craneTransfer(task:CraneTask) {
        if(!task.drop) {
            val pile=nearbyPile(task.x,task.z)
            when {
                pile!=null -> { craneCargo=min(2,sand[pile]);sand[pile]-=craneCargo }
                source>0 && hypot(task.x-WorldMap.worldX(365f),task.z)<1.8f -> { craneCargo=min(2,source);source-=craneCargo }
                else -> return
            }
        } else {
            if(craneCargo==0)return
            if(hypot(task.x-WorldMap.worldX(truckX),task.z-truckZ)<1.8f && load<8 && !tipping) {
                val n=min(craneCargo,8-load);load+=n;craneCargo-=n
            }
            val cell=WorldMap.cell(task.x,task.z)
            while(craneCargo>0) {
                craneCargo--
                if(Terrain.foundation(cell) && houseReady && houseFill<8)houseFill++
                else if(WorldMap.bridgeYard(cell) && bridgeStage==0 && bridgeFill<8)bridgeFill++
                else sand[if(WorldMap.river(cell))WorldMap.cell(if(WorldMap.east(task.x))WorldMap.EAST_BANK_X+.6f else WorldMap.WEST_BANK_X-.6f,task.z) else cell]++
            }
        }
        transferKind=5;transferSerial++;checkMission()
    }
    val bridgeReady get()=bridgeFill>=8 && bridgeStage<3
    private fun insideBridgeSite(x:Float,z:Float)=WorldMap.worldX(x) in 43.5f..55.5f && z in 1.5f..7.5f
    private val bridgeSiteOccupied get()=insideBridgeSite(truckX,truckZ) || insideBridgeSite(excavatorX,excavatorZ) || insideBridgeSite(dozerX,dozerZ)
    /** Starts the crane on the next bridge stage, after moving machines out of the work area. */
    fun buildBridge():Boolean {
        if(!bridgeReady || bridgeClock>0f || !craneAtSpot)return false
        craneTask=null;pendingCrane=null
        if(insideBridgeSite(truckX,truckZ))driveTo(Vehicle.TRUCK,WorldMap.gameX(41f),9.5f)
        if(insideBridgeSite(dozerX,dozerZ))driveTo(Vehicle.DOZER,WorldMap.gameX(41f),0f)
        if(insideBridgeSite(excavatorX,excavatorZ)) { moveArm(365f,340f);driveTo(Vehicle.EXCAVATOR,WorldMap.gameX(40f),3f) }
        bridgeClock=BRIDGE_STAGE_SECONDS;return true
    }
    val excavatorHeight get()=surface(WorldMap.worldX(excavatorX),excavatorZ)-.07f
    private fun insideHouse(x:Float,z:Float)=WorldMap.worldX(x) in 31f..41f && z in 3f..11f
    private val siteOccupied get()=insideHouse(truckX,truckZ) || insideHouse(excavatorX,excavatorZ) || insideHouse(dozerX,dozerZ) || insideHouse(craneX,craneZ)
    fun buildHouse():Boolean {
        if(!houseReady || houseFill<8 || houseStage>=3 || buildClock>0)return false
        // Empty the working footprint before any structure appears.
        if(insideHouse(truckX,truckZ))driveTo(Vehicle.TRUCK,WorldMap.gameX(44f),11.5f)
        if(insideHouse(dozerX,dozerZ))driveTo(Vehicle.DOZER,WorldMap.gameX(44f),3f)
        if(insideHouse(excavatorX,excavatorZ)) { moveArm(365f,340f);driveTo(Vehicle.EXCAVATOR,WorldMap.gameX(29f),3f) }
        if(insideHouse(craneX,craneZ))driveTo(Vehicle.CRANE,WorldMap.gameX(43f),10f)
        buildClock=1.4f;return true
    }
    val road=IntArray(4)
    val dug=IntArray(WorldMap.CELL_COUNT)
    val roadProgress get()=road.count { it>0 }
    fun armWorld(localX:Float=armX):Pair<Float,Float> {
        val distance=(localX-200f)/60f;val a=excavatorHeading*PI.toFloat()/180f
        return WorldMap.worldX(excavatorX)+cos(a)*distance to excavatorZ-sin(a)*distance
    }
    val bucketPosition get()=armWorld()
    /** Aim the turret at a ground point. Driving pauses so touch manipulation is stable. */
    fun aimAt(x:Float,z:Float,y:Float=440f) {
        if(!x.isFinite() || !z.isFinite() || !y.isFinite())return
        digTarget=null;excavatorGoal=null
        val dx=x-WorldMap.worldX(excavatorX);val dz=z-excavatorZ
        excavatorHeading=(atan2(-dz,dx)*180f/PI.toFloat()+360f)%360f
        moveArm(200f+hypot(dx,dz)*60f,y)
    }
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
    init { Terrain.QUARRY.forEach { sand[it]=Terrain.QUARRY_SAND } }
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
    /** Remaining waypoints after the current goal, per vehicle (excavator, truck, dozer). */
    private val routes=Array(4) { ArrayDeque<DriveGoal>() }
    /**
     * Drives toward (x, z). The river can only be crossed over the finished bridge: routes to the
     * other bank go via both bridge ends; before it opens, goals stay on the west bank.
     */
    fun driveTo(vehicle:Vehicle,x:Float,z:Float) {
        if(!x.isFinite() || !z.isFinite())return
        if(vehicle==Vehicle.EXCAVATOR)digTarget=null
        val currentX=WorldMap.worldX(when(vehicle) { Vehicle.EXCAVATOR->excavatorX;Vehicle.TRUCK->truckX;Vehicle.DOZER->dozerX;Vehicle.CRANE->craneX })
        var gx=x.coerceIn(if(vehicle==Vehicle.EXCAVATOR)90f else WorldMap.DRIVE_MIN_X,if(bridgeOpen)WorldMap.EAST_MAX_X else WorldMap.DRIVE_MAX_X)
        val gz=z.coerceIn(WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z)
        // Off the deck, the water is never a destination: stop at the nearer bank.
        val wx=WorldMap.worldX(gx)
        val onDeck=bridgeOpen && kotlin.math.abs(gz-WorldMap.BRIDGE_Z)<WorldMap.BRIDGE_HALF-.4f
        if(wx>WorldMap.WEST_BANK_X-.2f && wx<WorldMap.EAST_BANK_X+.2f && !onDeck)
            gx=WorldMap.gameX(if(WorldMap.east(wx))WorldMap.EAST_BANK_X+.6f else WorldMap.WEST_BANK_X-.6f)
        val route=routes[vehicle.ordinal];route.clear()
        val legs=ArrayList<DriveGoal>()
        if(bridgeOpen && WorldMap.east(currentX)!=WorldMap.east(WorldMap.worldX(gx))) {
            val entry=if(WorldMap.east(currentX))WorldMap.EAST_BANK_X+.8f else WorldMap.WEST_BANK_X-.8f
            val exit=if(WorldMap.east(currentX))WorldMap.WEST_BANK_X-.8f else WorldMap.EAST_BANK_X+.8f
            legs.add(DriveGoal(WorldMap.gameX(entry),WorldMap.BRIDGE_Z));legs.add(DriveGoal(WorldMap.gameX(exit),WorldMap.BRIDGE_Z))
        }
        legs.add(DriveGoal(gx,gz))
        val goal=legs.removeAt(0);route.addAll(legs)
        when(vehicle) {
            Vehicle.TRUCK -> { truckGoal=goal;tipping=false }
            Vehicle.DOZER -> dozerGoal=goal
            Vehicle.EXCAVATOR -> excavatorGoal=goal
            Vehicle.CRANE -> { craneGoal=goal;craneTask=null }
        }
    }
    /** The next waypoint once the current one is reached, or null at the destination. */
    private fun nextLeg(vehicle:Vehicle)=routes[vehicle.ordinal].removeFirstOrNull()
    fun stopMovement() { truckGoal=null;dozerGoal=null;excavatorGoal=null;craneGoal=null;craneTask=null;pendingCrane=null;pendingBridge=false;digTarget=null;buildClock=0f;bridgeClock=0f;routes.forEach { it.clear() } }
    val driving get()=truckGoal!=null || dozerGoal!=null || excavatorGoal!=null || craneGoal!=null || craneTask!=null
    var bladeDown = true
    var dozerFacing = 1
    var rewardSerial = 0
    /** Transient (not saved): a vehicle was picked, so its driver waves and it honks. */
    var greetSerial=0
        private set
    var greetVehicle=Vehicle.EXCAVATOR
        private set
    fun greet(vehicle:Vehicle) { greetVehicle=vehicle;greetSerial++ }
    var transferSerial = 0
    var transferKind=0
    private var tipClock = 0f
    private var pendingReward = false
    val totalSand get() = source + bucket + load + sand.sum() + road.sum() + houseFill + bridgeFill + craneCargo
    val missionProgress get() = when(mission) { 0 -> load.coerceAtMost(4); 1 -> delivered.coerceAtMost(4); 2 -> flattened.coerceAtMost(4); 3 -> roadProgress; else -> houseStage.coerceAtMost(3)+if(houseReady)1 else 0 }
    fun moveArm(x: Float, y: Float) {
        if(!x.isFinite() || !y.isFinite())return
        val point=ArmRig.limit(x,y)
        targetX=point.first;targetY=point.second
    }
    private fun nearbyPile(x:Float,z:Float):Int?=sand.indices.filter { sand[it]>0 && hypot(WorldMap.cellX(it)-x,WorldMap.cellZ(it)-z)<1.15f }.minByOrNull { hypot(WorldMap.cellX(it)-x,WorldMap.cellZ(it)-z) }
    fun looseAt(x:Float,z:Float)=nearbyPile(x,z)!=null || (source>0 && hypot(x-WorldMap.worldX(365f),z)<1.8f)
    fun scoop(): Boolean {
        if(bucket>0 || armY<=380f || excavatorGoal!=null)return false
        val (x,z)=bucketPosition
        val index=nearbyPile(x,z)
        if(index!=null) {
            bucket=min(2,sand[index]);sand[index]-=bucket;dug[index]=(dug[index]+bucket).coerceAtMost(3)
        } else if(source>0 && hypot(x-WorldMap.worldX(365f),z)<1.8f) {
            bucket=min(2,source);source-=bucket
        } else {
            val cell=WorldMap.cell(x,z)
            if(!Terrain.diggable(cell) || earth[cell]==0 || (houseStage>0 && Terrain.foundation(cell)))return false
            val tipY=(480f-armY)/60f+excavatorHeight
            if(tipY>surface(x,z)+1.4f)return false
            bucket=min(2,earth[cell]);earth[cell]-=bucket;dug[cell]=(dug[cell]+bucket).coerceAtMost(3)
            checkMission()
        }
        transferKind=1;transferSerial++;return true
    }
    fun pour(): Boolean {
        val (x,z)=bucketPosition
        if(bucket>0 && hypot(x-WorldMap.worldX(truckX),z-truckZ)<1.8f && armY<390 && load<8 && !tipping && excavatorGoal==null && truckGoal==null) {
            val n=min(bucket,8-load);load+=n;bucket-=n;transferKind=2;transferSerial++;checkMission();return true
        }
        return false
    }
    /** Reachable sand can be selected directly; a more distant tap drives the machine. */
    fun digAt(x:Float,z:Float):Boolean {
        if(!x.isFinite() || !z.isFinite() || bucket>0)return false
        val pile=nearbyPile(x,z)
        val sourceHere=source>0 && hypot(x-WorldMap.worldX(365f),z)<1.8f
        if(pile==null && !sourceHere && (!Terrain.diggable(WorldMap.cell(x,z)) || earth[WorldMap.cell(x,z)]==0 || (houseStage>0 && Terrain.foundation(WorldMap.cell(x,z)))))return false
        val px=if(pile!=null)WorldMap.cellX(pile) else if(sourceHere)WorldMap.worldX(365f) else WorldMap.cellX(WorldMap.cell(x,z))
        val pz=if(pile!=null)WorldMap.cellZ(pile) else if(sourceHere)0f else WorldMap.cellZ(WorldMap.cell(x,z))
        val distance=hypot(px-WorldMap.worldX(excavatorX),pz-excavatorZ)
        if(distance !in 1.4f..7.4f) {
            val approach=(px-3f).coerceIn(-8.5f,WorldMap.worldX(driveMaxX))
            driveTo(Vehicle.EXCAVATOR,WorldMap.gameX(approach),pz)
            digTarget=px to pz
        } else aimAt(px,pz,digY(px,pz))
        return true
    }
    private fun digY(x:Float,z:Float)=(480f-(surface(x,z)+.6f-excavatorHeight)*60f).coerceIn(390f,550f)
    fun action() {
        when(selected) {
            Vehicle.EXCAVATOR -> if(bucket==0) {
                if(!scoop()) {
                    val nearby=sand.indices.filter { sand[it]>0 && (bridgeOpen || !WorldMap.east(WorldMap.cellX(it))) }.sortedBy { hypot(WorldMap.cellX(it)-WorldMap.worldX(excavatorX),WorldMap.cellZ(it)-excavatorZ) }.firstOrNull()
                    if(nearby!=null)digAt(WorldMap.cellX(nearby),WorldMap.cellZ(nearby))
                    else digAt(WorldMap.worldX(365f),0f)
                }
            } else if(!pour()) {
                // Park the truck at the local pouring point, even at the far work sites.
                var desired=armWorld(620f)
                if(desired.first !in WorldMap.worldX(WorldMap.DRIVE_MIN_X)..WorldMap.worldX(driveMaxX) || desired.second !in WorldMap.DRIVE_MIN_Z..WorldMap.DRIVE_MAX_Z) {
                    excavatorHeading=(excavatorHeading+180f)%360f;desired=armWorld(620f)
                }
                val point=WorldMap.worldX(WorldMap.gameX(desired.first).coerceIn(WorldMap.DRIVE_MIN_X,driveMaxX)) to desired.second.coerceIn(WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z)
                driveTo(Vehicle.TRUCK,WorldMap.gameX(point.first),point.second)
                aimAt(point.first,point.second,300f)
            }
            Vehicle.TRUCK -> { truckGoal=null;tipping=!tipping }
            Vehicle.DOZER -> if(!pushNearestPile())bladeDown=!bladeDown
            Vehicle.CRANE -> craneAction()
        }
    }
    /** One-touch bulldozing: lower the blade and drive straight through the nearest tall pile. */
    fun pushNearestPile():Boolean {
        if(dozerGoal!=null)return false
        val x=WorldMap.worldX(dozerX)
        val pile=sand.indices.filter { sand[it]>1 }.minByOrNull { hypot(WorldMap.cellX(it)-x,WorldMap.cellZ(it)-dozerZ) } ?: return false
        var dx=WorldMap.cellX(pile)-x;var dz=WorldMap.cellZ(pile)-dozerZ;val distance=hypot(dx,dz)
        if(distance<.5f) { dx=1f;dz=0f } else { dx/=distance;dz/=distance }
        bladeDown=true
        driveTo(Vehicle.DOZER,WorldMap.gameX(WorldMap.cellX(pile)+dx*3f),WorldMap.cellZ(pile)+dz*3f)
        return true
    }
    fun update(rawDt: Float) {
        if(!rawDt.isFinite())return
        val dt = rawDt.coerceIn(0f, .04f)
        celebrating = max(0f, celebrating-dt)
        if(buildClock>0f && !siteOccupied) {
            buildClock=max(0f,buildClock-dt)
            if(buildClock==0f) { houseStage++;if(houseStage==3) { celebrating=2.5f;rewardSerial++ };checkMission() }
        }
        if(bridgeClock>0f && !bridgeSiteOccupied) {
            bridgeClock=max(0f,bridgeClock-dt)
            if(bridgeClock==0f) {
                bridgeStage++;if(bridgeStage==3) { celebrating=2.5f;rewardSerial++ }
                craneAngle=atan2(WorldMap.BRIDGE_Z-craneZ,52f-craneWorldX)*180f/PI.toFloat();craneReach=hypot(52f-craneWorldX,WorldMap.BRIDGE_Z-craneZ).coerceIn(CRANE_MIN_REACH,CRANE_MAX_REACH)
            }
        }
        val smoothing = 1f-exp(-8f*dt)
        armX += (targetX-armX)*smoothing; armY += (targetY-armY)*smoothing
        advanceExcavator(dt)
        advanceTruck(dt)
        advanceDozer(dt)
        advanceCrane(dt)
        bedAngle += ((if(tipping) -42f else 0f)-bedAngle)*smoothing
        if(tipping && bedAngle < -28 && load > 0) {
            tipClock += dt
            if(tipClock >= .18f) {
                tipClock=0f; load--; delivered++
                val a=truckHeading*PI.toFloat()/180f
                val cell=WorldMap.cell(WorldMap.worldX(truckX)-cos(a)*1.8f,truckZ+sin(a)*1.8f)
                if(Terrain.foundation(cell) && houseReady && houseFill<8)houseFill++
                else if(WorldMap.bridgeYard(cell) && bridgeStage==0 && bridgeFill<8)bridgeFill++
                else sand[cell]++
                transferKind=3;transferSerial++; checkMission()
            }
        }
    }
    private fun heading(current:Float,dx:Float,dz:Float,dt:Float):Float {
        val desired=atan2(-dz,dx)*180f/PI.toFloat()
        val delta=((desired-current+540f)%360f)-180f
        return (current+delta.coerceIn(-240f*dt,240f*dt)+360f)%360f
    }
    private fun advanceExcavator(dt:Float) {
        val goal=excavatorGoal ?: return
        val dx=(goal.x-excavatorX)/60f;val dz=goal.z-excavatorZ;val distance=hypot(dx,dz)
        if(distance<.025f) { excavatorX=goal.x;excavatorZ=goal.z;excavatorGoal=nextLeg(Vehicle.EXCAVATOR)
            if(excavatorGoal!=null)return
            val target=digTarget;digTarget=null
            if(target!=null)aimAt(target.first,target.second,digY(target.first,target.second))
            return }
        excavatorHeading=heading(excavatorHeading,dx,dz,dt)
        val step=min(distance,2.4f*dt)
        excavatorX+=dx/distance*step*60f;excavatorZ+=dz/distance*step;excavatorTravel+=step
    }
    private fun compactRoad(index:Int):Boolean {
        val section=WorldMap.roadSection(index)
        if(section<0 || road[section]>0 || sand[index]==0)return false
        sand[index]--;road[section]=1;flattened++;transferKind=4;transferSerial++
        if(roadProgress==4 && !(missionMode && mission==3)) { celebrating=2.5f;rewardSerial++ }
        checkMission();return true
    }
    private fun advanceTruck(dt:Float) {
        val goal=truckGoal ?: return
        if(tipping || bedAngle < -8f)return
        val dx=(goal.x-truckX)/60f;val dz=goal.z-truckZ;val distance=hypot(dx,dz)
        if(distance<.025f) { truckX=goal.x;truckZ=goal.z;truckGoal=nextLeg(Vehicle.TRUCK);return }
        truckHeading=heading(truckHeading,dx,dz,dt)
        val step=min(distance,3.5f*dt)
        truckX+=dx/distance*step*60f;truckZ+=dz/distance*step;truckTravel+=step
    }
    private fun advanceDozer(dt:Float) {
        val goal=dozerGoal ?: return
        val dx=(goal.x-dozerX)/60f;val dz=goal.z-dozerZ;val distance=hypot(dx,dz)
        if(distance<.025f) { dozerX=goal.x;dozerZ=goal.z;dozerGoal=nextLeg(Vehicle.DOZER);return }
        dozerHeading=heading(dozerHeading,dx,dz,dt);dozerFacing=if(dx<0)-1 else 1
        val step=min(distance,2.8f*dt);if(step<=0f)return
        dozerX+=dx/distance*step*60f;dozerZ+=dz/distance*step;dozerTravel+=step
        if(bladeDown) {
            val ux=dx/distance;val uz=dz/distance
            val touched=HashSet<Int>()
            for(side in floatArrayOf(-.65f,0f,.65f)) {
                val index=WorldMap.cell(WorldMap.worldX(dozerX)+ux*1.8f-uz*side,dozerZ+uz*1.8f+ux*side)
                if(!touched.add(index))continue
                if(earth[index]<Terrain.INITIAL_EARTH && sand[index]>0 && !(houseStage>0 && Terrain.foundation(index))) {
                    earth[index]++;sand[index]--;flattened++;checkMission();continue
                }
                if(compactRoad(index))continue
                val next=WorldMap.neighbor(index,ux,uz)
                val section=WorldMap.roadSection(index)
                val roadFlow=section>=0 && road[section]>0 && WorldMap.roadSection(next)>=0 && sand[index]>0
                if(next!=index && (sand[index]>sand[next]+1 || roadFlow)) {
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
        if (pendingReward && celebrating <= 0f) { mission=(mission+1)%5; pendingReward=false
            if(mission==0) { delivered=0; flattened=0 }
            checkMission()
        }
    }
    fun toggleMode() { missionMode=!missionMode; mission=0; delivered=0; flattened=0; pendingReward=false; celebrating=0f; checkMission() }
    fun reset() { earth.fill(Terrain.INITIAL_EARTH);houseFill=0;houseStage=0;buildClock=0f;bridgeFill=0;bridgeStage=0;bridgeClock=0f;craneX=WorldMap.gameX(CRANE_SPOT_X);craneZ=CRANE_SPOT_Z;craneHeading=0f;craneTravel=0f;craneAngle=-90f;craneReach=4.7f;hookY=HOOK_REST;craneCargo=0; excavatorX=200f;excavatorZ=0f;excavatorHeading=0f;excavatorTravel=0f;road.fill(0);dug.fill(0);truckX=620f; dozerX=1000f; armX=365f; armY=340f; targetX=armX; targetY=armY
        bucket=0; load=0; source=36; sand.fill(0); Terrain.QUARRY.forEach { sand[it]=Terrain.QUARRY_SAND }; delivered=0; flattened=0; mission=0
        tipping=false;stopMovement();truckZ=0f;dozerZ=2.25f;truckHeading=0f;dozerHeading=0f;truckTravel=0f;dozerTravel=0f; bladeDown=true; dozerFacing=1; bedAngle=0f; celebrating=0f; tipClock=0f; pendingReward=false }
    fun snapshot():String="9|"+listOf(truckX,dozerX,armX,armY,bucket,load,source,delivered,flattened,mission,if(missionMode)1 else 0,selected.ordinal,if(bladeDown)1 else 0,if(tipping)1 else 0,dozerFacing,bedAngle,truckZ,dozerZ,truckHeading,dozerHeading,excavatorX,excavatorZ,excavatorHeading).joinToString(",")+";"+sand.joinToString(",")+";"+road.joinToString(",")+";"+dug.joinToString(",")+";"+earth.joinToString(",")+";"+listOf(houseFill,houseStage).joinToString(",")+";"+listOf(bridgeFill,bridgeStage).joinToString(",")+";"+listOf(craneX,craneZ,craneHeading,craneAngle,craneReach,craneCargo).joinToString(",")
    fun restore(value:String?) {
        if(value==null)return
        runCatching {
            val alpha9=value.startsWith("9|")
            val alpha8=alpha9 || value.startsWith("8|")
            val alpha7=alpha8 || value.startsWith("7|")
            val alpha6=alpha7 || value.startsWith("6|")
            val modern=alpha6 || value.startsWith("5|")
            val parts=value.removePrefix("9|").removePrefix("8|").removePrefix("7|").removePrefix("6|").removePrefix("5|").split(';');require(parts.size==if(alpha9)8 else if(alpha8)7 else if(alpha7)6 else if(alpha6)4 else 2)
            // Saves before alpha.12 hold the 62-column map; widen them, east-bank cells start fresh.
            val cells=if(alpha8)WorldMap.CELL_COUNT else WorldMap.LEGACY_COLUMNS*WorldMap.ROWS
            fun widen(index:Int)=if(alpha8)index else index/WorldMap.LEGACY_COLUMNS*WorldMap.COLUMNS+index%WorldMap.LEGACY_COLUMNS
            val v=parts[0].split(',');val piles=parts[1].split(',').map { it.toInt().also { n->require(n in 0..(if(alpha7)Terrain.INITIAL_MASS else 36)) } }
            require(if(modern)v.size==(if(alpha6)23 else 20) && piles.size==cells else v.size in listOf(12,16) && piles.size==24)
            fun finite(index:Int,min:Float,max:Float)=v[index].toFloat().also { require(it.isFinite()) }.coerceIn(min,max)
            reset()
            truckX=finite(0,WorldMap.DRIVE_MIN_X,WorldMap.EAST_MAX_X);dozerX=finite(1,WorldMap.DRIVE_MIN_X,WorldMap.EAST_MAX_X)
            val end=ArmRig.limit(finite(2,285f,825f),finite(3,220f,550f));armX=end.first;armY=end.second;targetX=armX;targetY=armY
            bucket=v[4].toInt().coerceIn(0,2);load=v[5].toInt().coerceIn(0,8);source=v[6].toInt().coerceIn(0,36)
            delivered=v[7].toInt().coerceAtLeast(0);flattened=v[8].toInt().coerceAtLeast(0);mission=v[9].toInt().coerceIn(0,if(alpha7)4 else if(alpha6)3 else 2)
            missionMode=v[10]=="1";selected=Vehicle.entries[v[11].toInt().coerceIn(0,if(alpha9)3 else 2)]
            if(v.size>=16) { bladeDown=v[12]=="1";tipping=v[13]=="1";dozerFacing=if(v[14].toInt()<0)-1 else 1;bedAngle=finite(15,-42f,0f) }
            if(modern) {
                truckZ=finite(16,WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z);dozerZ=finite(17,WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z)
                truckHeading=finite(18,0f,360f);dozerHeading=finite(19,0f,360f)
                sand.fill(0);piles.forEachIndexed { i,n->sand[widen(i)]=n }
            } else {
                dozerHeading=if(dozerFacing<0)180f else 0f
                piles.forEachIndexed { i,n->sand[WorldMap.cell(WorldMap.worldX(495f+i*30),1.35f)]+=n }
            }
            if(alpha6) {
                excavatorX=finite(20,90f,WorldMap.EAST_MAX_X);excavatorZ=finite(21,WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z);excavatorHeading=finite(22,0f,360f)
                val installed=parts[2].split(',').map { it.toInt().also { n->require(n in 0..1) } };require(installed.size==4)
                val marks=parts[3].split(',').map { it.toInt().also { n->require(n in 0..3) } };require(marks.size==cells)
                installed.forEachIndexed { i,n->road[i]=n };marks.forEachIndexed { i,n->dug[widen(i)]=n }
            }
            if(alpha7) {
                val ground=parts[4].split(',').map { it.toInt().also { n->require(n in 0..Terrain.INITIAL_EARTH) } };require(ground.size==cells)
                ground.forEachIndexed { i,n->earth[widen(i)]=n }
                val house=parts[5].split(',');require(house.size==2)
                houseFill=house[0].toInt();houseStage=house[1].toInt();require(houseFill in 0..8 && houseStage in 0..3)
                require(houseStage==0 || (houseFill==8 && houseReady))
            }
            if(alpha8) {
                val bridge=parts[6].split(',');require(bridge.size==2)
                bridgeFill=bridge[0].toInt();bridgeStage=bridge[1].toInt();require(bridgeFill in 0..8 && bridgeStage in 0..3 && (bridgeStage==0 || bridgeFill==8))
                // Vehicles may only be on the east bank when the bridge is open.
                if(alpha9) {
                    val c=parts[7].split(',');require(c.size==6)
                    fun cf(i:Int)=c[i].toFloat().also { require(it.isFinite()) }
                    craneX=cf(0).coerceIn(WorldMap.DRIVE_MIN_X,WorldMap.EAST_MAX_X);craneZ=cf(1).coerceIn(WorldMap.DRIVE_MIN_Z,WorldMap.DRIVE_MAX_Z)
                    craneHeading=cf(2).coerceIn(0f,360f);craneAngle=cf(3).coerceIn(-360f,360f);craneReach=cf(4).coerceIn(CRANE_MIN_REACH,CRANE_MAX_REACH)
                    craneCargo=c[5].toInt();require(craneCargo in 0..2)
                }
                if(!bridgeOpen)require(listOf(truckX,dozerX,excavatorX,craneX).all { it<=WorldMap.DRIVE_MAX_X })
            } else Terrain.QUARRY.forEach { sand[it]=Terrain.QUARRY_SAND }
            require(totalMaterial==Terrain.INITIAL_MASS);checkMission()
        }.onFailure { reset() }
    }
    companion object {
        const val BRIDGE_STAGE_SECONDS=3.2f
        /** Crane work spot beside the bridge yard, and its reach and hook heights in metres. */
        const val CRANE_SPOT_X=46.5f;const val CRANE_SPOT_Z=9.2f
        const val CRANE_MIN_REACH=2.5f;const val CRANE_MAX_REACH=9f
        const val HOOK_REST=2.6f;const val HOOK_SWING=3.1f
        /** Loose sand in a new world: the 36-unit source plus the east-bank quarry. */
        const val START_SAND=36+6*Terrain.QUARRY_SAND
    }
}
