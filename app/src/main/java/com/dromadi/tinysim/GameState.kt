package com.dromadi.tinysim

import kotlin.math.*

/** Deterministic, bounded simulation. Sand units are conserved, including the source. */
class GameState {
    enum class Vehicle { EXCAVATOR, TRUCK, DOZER, CRANE, MIXER, WRECKER }
    /** Collision-free paths around the other vehicles, per leg of the river-aware route. */
    private val paths=VehicleRoutes()
    val recovery=RecoverySite()
    val village=VillageLife()
    val wash=WashBay()
    val wind=WindPlay()
    val fleet=WorkFleet()
    val keeper=BridgeKeeper()
    fun guardTap() { keeper.request(bridgeOpen);transferKind=7;transferSerial++ }
    fun vehicleBody(unit:Vehicle)=VehicleNavigation.Body(unit,when(unit) {
        Vehicle.EXCAVATOR->VehicleNavigation.Point(WorldMap.worldX(excavatorX),excavatorZ)
        Vehicle.TRUCK->VehicleNavigation.Point(WorldMap.worldX(truckX),truckZ)
        Vehicle.DOZER->VehicleNavigation.Point(WorldMap.worldX(dozerX),dozerZ)
        Vehicle.CRANE->VehicleNavigation.Point(WorldMap.worldX(craneX),craneZ)
        Vehicle.MIXER,Vehicle.WRECKER->fleet.machine(unit).let { VehicleNavigation.Point(WorldMap.worldX(it.x),it.z) }
    })
    private fun setPose(unit:Vehicle,p:VehicleNavigation.Point) { when(unit) {
        Vehicle.EXCAVATOR->{excavatorX=WorldMap.gameX(p.x);excavatorZ=p.z}
        Vehicle.TRUCK->{truckX=WorldMap.gameX(p.x);truckZ=p.z}
        Vehicle.DOZER->{dozerX=WorldMap.gameX(p.x);dozerZ=p.z}
        Vehicle.CRANE->{craneX=WorldMap.gameX(p.x);craneZ=p.z}
        Vehicle.MIXER,Vehicle.WRECKER->{fleet.machine(unit).let { it.x=WorldMap.gameX(p.x);it.z=p.z }}
    } }
    private fun others(unit:Vehicle)=Vehicle.entries.filter { it!=unit }.map { vehicleBody(it) }
    /** Old saves may already contain overlaps. Keep inventory/heading; separate only the poses. */
    private fun separateVehicles() {
        val placed=mutableListOf<VehicleNavigation.Body>()
        for(unit in Vehicle.entries) {
            val body=vehicleBody(unit)
            if(!VehicleNavigation.free(body.point,unit,placed)) {
                val p=VehicleNavigation.nearestFree(body.point,body.point,unit,placed,bridgeOpen) ?: continue
                setPose(unit,p);paths.clear(unit)
            }
            placed+=vehicleBody(unit)
        }
    }
    private val blocked=FloatArray(Vehicle.entries.size)
    private fun goalOf(unit:Vehicle)=when(unit) { Vehicle.EXCAVATOR->excavatorGoal;Vehicle.TRUCK->truckGoal;Vehicle.DOZER->dozerGoal;Vehicle.CRANE->craneGoal;Vehicle.MIXER,Vehicle.WRECKER->fleet.machine(unit).goal }
    private sealed class Travel {
        object Arrived:Travel()
        object Stuck:Travel()
        class Moved(val dx:Float,val dz:Float,val step:Float):Travel()
    }
    /**
     * One frame along the current leg, around parked or moving vehicles. Arrived snaps to the
     * (possibly nudged) end point; Stuck means no safe path exists; a zero step waits for a
     * moving vehicle to pass.
     */
    private fun travel(unit:Vehicle,goal:DriveGoal,speed:Float,dt:Float):Travel {
        if(goal.deck==1)when(claimDeck(unit)) {
            false -> return Travel.Moved(0f,0f,0f)
            null -> return Travel.Stuck
            true -> {}
        }
        return when(val result=step(unit,goal,speed,dt)) {
            Travel.Arrived -> { if(goal.deck==2 && deckOwner==unit)deckOwner=null;result }
            Travel.Stuck -> { if(deckOwner==unit)deckOwner=null;result }
            is Travel.Moved -> result
        }
    }
    /** The vehicle holding the one-lane bridge deck; transient, like the routes. */
    private var deckOwner:Vehicle?=null
    private fun onDeck(p:VehicleNavigation.Point)=p.x>WorldMap.WEST_BANK_X-.2f && p.x<WorldMap.EAST_BANK_X+.2f
    /** True once [unit] holds the deck, false while it waits its turn, null if a parked machine blocks it. */
    private fun claimDeck(unit:Vehicle):Boolean? {
        if(wind.nearBridge)return false
        keeper.request(bridgeOpen)
        if(!keeper.open)return false
        deckOwner?.let { if(it!=unit && goalOf(it)==null)deckOwner=null }
        if(deckOwner!=null && deckOwner!=unit)return false
        val onIt=Vehicle.entries.filter { it!=unit && onDeck(vehicleBody(it).point) }
        if(onIt.any { goalOf(it)==null })return null
        if(onIt.isNotEmpty())return false
        deckOwner=unit;return true
    }
    private fun step(unit:Vehicle,goal:DriveGoal,speed:Float,dt:Float):Travel {
        val here=vehicleBody(unit).point
        val target=paths.next(unit,here,VehicleNavigation.Point(WorldMap.worldX(goal.x),goal.z),others(unit),bridgeOpen)
        if(target==null) {
            // A machine that is still driving may clear the way; a parked blockade ends the trip.
            blocked[unit.ordinal]+=dt
            val moving=Vehicle.entries.any { it!=unit && goalOf(it)!=null }
            if(moving && blocked[unit.ordinal]<BLOCKED_SECONDS)return Travel.Moved(0f,0f,0f)
            blocked[unit.ordinal]=0f;return Travel.Stuck
        }
        blocked[unit.ordinal]=0f
        val dx=target.x-here.x;val dz=target.z-here.z;val distance=hypot(dx,dz)
        if(distance<.025f && paths.last(unit)) { setPose(unit,target);paths.clear(unit);return Travel.Arrived }
        if(distance<1e-6f)return Travel.Moved(0f,0f,0f)
        val step=min(distance,speed*dt);val next=VehicleNavigation.Point(here.x+dx/distance*step,here.z+dz/distance*step)
        if(!VehicleNavigation.clear(here,next,unit,others(unit),bridgeOpen)) { paths.clear(unit);return Travel.Moved(0f,0f,0f) }
        setPose(unit,next);return Travel.Moved(dx,dz,step)
    }
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
    /** The cell the tipped bed pours into, behind the truck. */
    val dumpCell get()=(truckHeading*PI.toFloat()/180f).let { a->WorldMap.cell(WorldMap.worldX(truckX)-cos(a)*1.8f,truckZ+sin(a)*1.8f) }
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
    val blocks=BlockYard()
    val pipes=PipeSite()
    fun startPipes() { recovery.engaged=false; stopMovement();pipes.active=!pipes.complete;moveArm(365f,300f) }
    fun leavePipes() { stopMovement();pipes.active=false;moveArm(armX,300f) }
    fun pipeDigY(x:Float,z:Float)=digY(x,z)
    val canAutoScoop get()=!pipes.active && abs(armY-targetY)<2f
    fun installPipe(i:Int):Boolean {
        if(i !in 0..2 || !pipes.active || !pipes.open(this) || pipes.hasPipe(i) || bucket>0 || craneCargo>0 || blocks.carried>=0 || (pipes.carried>=0 && pipes.carried!=i))return false
        if(pipes.carried>=0)return requestCrane(CraneTask(PipeSite.pipeX(i),PipeSite.Z,true,pipe=i))
        return requestCrane(CraneTask(PipeSite.pipeX(i),11.5f,false,pipe=i))
    }
    private class CraneTask(val x:Float,val z:Float,val drop:Boolean,val block:Int=-1,val pipe:Int=-1,val recovery:Int=-1) { var lowered=false }
    private var craneTask:CraneTask?=null
    private var pendingCrane:CraneTask?=null
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
        if(pipes.carried>=0)return installPipe(pipes.carried)
        if(blocks.carried>=0) {
            if(!BlockYard.validGround(x,z) || (!bridgeOpen && WorldMap.east(x)))return false
            val target=if(BlockYard.atTarget(x,z))BlockYard.TARGET_X to BlockYard.TARGET_Z else x to z
            return requestCrane(CraneTask(target.first,target.second,true,blocks.carried))
        }
        // A broad ground target also works when the child touches beside a block.
        val id=(0 until BlockYard.COUNT).firstOrNull { blocks.level[it]<0 && hypot(blocks.x[it]-x,blocks.z[it]-z)<1.1f }
        if(id!=null && craneCargo==0)return blockTap(id)
        return requestCrane(CraneTask(x,z,craneCargo>0))
    }
    fun blockTap(id:Int):Boolean {
        if(id !in 0 until BlockYard.COUNT || blocks.carried>=0 || blocks.level[id]>=0 || craneCargo>0 || craneBusy || bridgeClock>0f)return false
        return requestCrane(CraneTask(blocks.x[id],blocks.z[id],false,id))
    }
    /** Existing action button can lead the entire block activity, without precise world touches. */
    fun blockAction():Boolean {
        if(craneBusy || craneGoal!=null || bridgeClock>0f || craneCargo>0)return false
        if(blocks.carried>=0)return craneTap(BlockYard.TARGET_X,BlockYard.TARGET_Z)
        val id=(0 until BlockYard.COUNT).firstOrNull { blocks.level[it]<0 } ?: return false
        return blockTap(id)
    }
    fun recoveryLift(id:Int,drop:Boolean):Boolean {
        if(id !in 0 until RecoverySite.COUNT)return false
        return requestCrane(CraneTask(if(drop)RecoverySite.homeX(id) else recovery.x[id],if(drop)RecoverySite.homeZ(id) else recovery.z[id],drop,recovery=id))
    }
    private fun requestCrane(task:CraneTask):Boolean {
        if(craneBusy || bridgeClock>0f)return false
        val d=hypot(task.x-craneWorldX,task.z-craneZ)
        if(d in CRANE_MIN_REACH..CRANE_MAX_REACH) { craneGoal=null;pendingCrane=null;craneTask=task;return true }
        // Park on the line toward the target, comfortably inside the reach.
        val ux=if(d<1e-3f)1f else (craneWorldX-task.x)/d;val uz=if(d<1e-3f)0f else (craneZ-task.z)/d
        driveTo(Vehicle.CRANE,WorldMap.gameX(task.x+ux*6f),task.z+uz*6f)
        pendingCrane=task;return true
    }
    /** The crane lifts the next bridge piece from the yard; it drives to its work spot first. */
    private fun craneBuildsBridge():Boolean {
        if(!bridgeReady || bridgeClock>0f)return false
        if(!craneAtSpot) { driveTo(Vehicle.CRANE,WorldMap.gameX(CRANE_SPOT_X),CRANE_SPOT_Z);pendingBridge=true;return true }
        return buildBridge()
    }
    private fun craneAction() {
        if(pipes.active) { if(pipes.carried>=0)installPipe(pipes.carried) else pipes.tap(this,pipes.next(this));return }
        if(craneTask!=null || craneGoal!=null)return
        if(blocks.carried>=0) { blockAction();return }
        if(craneBuildsBridge())return
        if(!blocks.complete && hypot(craneWorldX-BlockYard.TARGET_X,craneZ-BlockYard.TARGET_Z)<12f) { blockAction();return }
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
            when(val move=travel(Vehicle.CRANE,goal,3.6f,dt)) {
                Travel.Arrived -> {
                    craneGoal=nextLeg(Vehicle.CRANE)
                    if(craneGoal==null) {
                        pendingCrane?.let { task->pendingCrane=null;if(hypot(task.x-craneWorldX,task.z-craneZ) in CRANE_MIN_REACH..CRANE_MAX_REACH)craneTask=task }
                        if(pendingBridge) { pendingBridge=false;buildBridge() }
                    }
                }
                Travel.Stuck -> { craneGoal=null;routes[Vehicle.CRANE.ordinal].clear();pendingCrane=null;pendingBridge=false }
                is Travel.Moved -> if(move.step>0f) { craneHeading=heading(craneHeading,move.dx,move.dz,dt);craneTravel+=move.step }
            }
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
        val blockLevel=if(task.block<0)0 else if(task.drop && BlockYard.atTarget(task.x,task.z))blocks.placed else max(0,blocks.level[task.block])
        val ground=if(task.recovery>=0)surface(task.x,task.z)+(if(task.drop)RecoverySite.homeY(task.recovery) else 0f)+(if(task.recovery%4==3).9f else 1.9f) else if(task.pipe>=0) (if(task.drop).25f else surface(task.x,task.z)+.68f) else surface(task.x,task.z)+if(task.block>=0).9f+blockLevel*BlockYard.HEIGHT else .45f
        val rest=max(HOOK_REST,ground+.8f)
        if(!task.lowered) {
            hookY=max(ground,hookY-3f*dt)
            if(hookY<=ground+1e-3f) { task.lowered=true;craneTransfer(task) }
        } else {
            hookY=min(rest,hookY+3f*dt)
            if(hookY>=rest-1e-3f)craneTask=null
        }
    }
    /** At the bottom of the drop: the grab closes on sand, or opens over the truck/yard/ground. */
    private fun craneTransfer(task:CraneTask) {
        if(task.recovery>=0) { recovery.craneTransfer(this,task.recovery,task.drop);return }
        if(task.pipe>=0) {
            val changed=if(task.drop)pipes.place(this,task.pipe) else pipes.pick(task.pipe)
            if(changed) { transferKind=6;transferSerial++;if(task.drop) { rewardSerial++;celebrating=.8f } }
            return
        }
        if(task.block>=0) {
            val before=blocks.placed
            val changed=if(task.drop)blocks.put(task.x,task.z) else blocks.pick(task.block)
            if(changed) {
                transferKind=6;transferSerial++
                if(blocks.placed>before) { rewardSerial++;celebrating=if(blocks.complete)2.5f else 1f }
            }
            return
        }
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
    private val bridgeSiteOccupied get()=insideBridgeSite(truckX,truckZ) || insideBridgeSite(excavatorX,excavatorZ) || insideBridgeSite(dozerX,dozerZ) || listOf(fleet.mixer,fleet.wrecker).any { insideBridgeSite(it.x,it.z) }
    /** Starts the crane on the next bridge stage, after moving machines out of the work area. */
    fun buildBridge():Boolean {
        if(!bridgeReady || bridgeClock>0f || !craneAtSpot || blocks.carried>=0)return false
        craneTask=null;pendingCrane=null
        if(insideBridgeSite(truckX,truckZ))driveTo(Vehicle.TRUCK,WorldMap.gameX(41f),9.5f)
        if(insideBridgeSite(dozerX,dozerZ))driveTo(Vehicle.DOZER,WorldMap.gameX(41f),0f)
        if(insideBridgeSite(excavatorX,excavatorZ)) { moveArm(365f,340f);driveTo(Vehicle.EXCAVATOR,WorldMap.gameX(40f),3f) }
        for(v in listOf(Vehicle.MIXER,Vehicle.WRECKER))if(insideBridgeSite(fleet.machine(v).x,fleet.machine(v).z))driveTo(v,WorldMap.gameX(41f),if(v==Vehicle.MIXER)16f else 21.5f)
        bridgeClock=BRIDGE_STAGE_SECONDS;return true
    }
    val excavatorHeight get()=surface(WorldMap.worldX(excavatorX),excavatorZ)-.07f
    private fun insideHouse(x:Float,z:Float)=WorldMap.worldX(x) in 31f..41f && z in 3f..11f
    private val siteOccupied get()=insideHouse(truckX,truckZ) || insideHouse(excavatorX,excavatorZ) || insideHouse(dozerX,dozerZ) || insideHouse(craneX,craneZ) || listOf(fleet.mixer,fleet.wrecker).any { insideHouse(it.x,it.z) }
    fun buildHouse():Boolean {
        if(!houseReady || houseFill<8 || houseStage>=3 || buildClock>0)return false
        // Empty the working footprint before any structure appears.
        if(insideHouse(truckX,truckZ))driveTo(Vehicle.TRUCK,WorldMap.gameX(44f),11.5f)
        if(insideHouse(dozerX,dozerZ))driveTo(Vehicle.DOZER,WorldMap.gameX(44f),3f)
        if(insideHouse(excavatorX,excavatorZ)) { moveArm(365f,340f);driveTo(Vehicle.EXCAVATOR,WorldMap.gameX(29f),3f) }
        if(insideHouse(craneX,craneZ))driveTo(Vehicle.CRANE,WorldMap.gameX(43f),10f)
        for(v in listOf(Vehicle.MIXER,Vehicle.WRECKER))if(insideHouse(fleet.machine(v).x,fleet.machine(v).z))driveTo(v,WorldMap.gameX(44f),if(v==Vehicle.MIXER)16f else 21.5f)
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
    private val routes=Array(Vehicle.entries.size) { ArrayDeque<DriveGoal>() }
    /**
     * Drives toward (x, z). The river can only be crossed over the finished bridge: routes to the
     * other bank go via both bridge ends; before it opens, goals stay on the west bank.
     */
    fun driveTo(vehicle:Vehicle,x:Float,z:Float) {
        recovery.manualDrive(this,vehicle)
        if(!x.isFinite() || !z.isFinite())return
        if(vehicle==Vehicle.EXCAVATOR || vehicle==Vehicle.DOZER)pipes.cancel()
        separateVehicles();paths.clear(vehicle);if(deckOwner==vehicle)deckOwner=null
        if(vehicle==Vehicle.EXCAVATOR)digTarget=null
        val currentX=vehicleBody(vehicle).point.x
        if(vehicle==Vehicle.MIXER || vehicle==Vehicle.WRECKER)fleet.cancelWork(vehicle)
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
            // The deck is one lane: a far-off vehicle first queues where an oncoming one can pass it.
            val queue=entry+if(WorldMap.east(currentX))BRIDGE_QUEUE else -BRIDGE_QUEUE
            if(abs(currentX-entry)>BRIDGE_QUEUE)legs.add(DriveGoal(WorldMap.gameX(queue),WorldMap.BRIDGE_Z))
            legs.add(DriveGoal(WorldMap.gameX(entry),WorldMap.BRIDGE_Z,1));legs.add(DriveGoal(WorldMap.gameX(exit),WorldMap.BRIDGE_Z,2))
        }
        legs.add(DriveGoal(gx,gz))
        val goal=legs.removeAt(0);route.addAll(legs)
        when(vehicle) {
            Vehicle.TRUCK -> { truckGoal=goal;tipping=false }
            Vehicle.DOZER -> dozerGoal=goal
            Vehicle.EXCAVATOR -> excavatorGoal=goal
            Vehicle.CRANE -> { craneGoal=goal;craneTask=null }
            Vehicle.MIXER,Vehicle.WRECKER -> { fleet.machine(vehicle).goal=goal }
        }
    }
    /** The next waypoint once the current one is reached, or null at the destination. */
    private fun nextLeg(vehicle:Vehicle)=routes[vehicle.ordinal].removeFirstOrNull()
    fun stopMovement() { wash.stop(); recovery.cancel(this);wind.halt();fleet.cancel();pipes.cancel();truckGoal=null;dozerGoal=null;excavatorGoal=null;craneGoal=null;craneTask=null;pendingCrane=null;pendingBridge=false;digTarget=null;buildClock=0f;bridgeClock=0f;routes.forEach { it.clear() };paths.clear();deckOwner=null }
    val driving get()=recovery.busy || fleet.busy || fleet.mixer.goal!=null || fleet.wrecker.goal!=null || pipes.busy || truckGoal!=null || dozerGoal!=null || excavatorGoal!=null || craneGoal!=null || craneTask!=null
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
        val siteCell=pipes.diggingCell
        if(pipes.active && siteCell==null)return false
        if(siteCell!=null) {
            if(hypot(x-WorldMap.cellX(siteCell),z-WorldMap.cellZ(siteCell))>.6f)return false
            val n=if(sand[siteCell]>0) min(2,sand[siteCell]) else min(2,(earth[siteCell]-2).coerceAtLeast(0))
            if(n==0)return false
            if(sand[siteCell]>0)sand[siteCell]-=n else earth[siteCell]-=n
            bucket=n;dug[siteCell]=(dug[siteCell]+n).coerceAtMost(3);wash.soil();transferKind=1;transferSerial++;return true
        }
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
        wash.soil();transferKind=1;transferSerial++;return true
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
    /** Tapping a service truck while it works must not restart the drum or the swing. */
    fun tapSelectedVehicle() {
        if(!recovery.engaged && selected!=Vehicle.MIXER && selected!=Vehicle.WRECKER)stopMovement()
        action()
    }
    fun action() {
        if(recovery.action(this,selected))return
        if(pipes.busy)return
        if(pipes.active && !pipes.busy && bucket==0) {
            if(pipes.carried>=0 && selected==Vehicle.CRANE) { if(!craneBusy && craneGoal==null)installPipe(pipes.carried);return }
            val id=pipes.next(this)
            val expected=if(id<6)Vehicle.EXCAVATOR else if(id<9)Vehicle.CRANE else Vehicle.DOZER
            if(id>=0 && selected==expected) { pipes.tap(this,id);return }
        }
        separateVehicles()
        when(selected) {
            Vehicle.EXCAVATOR -> if(bucket==0) {
                if(!scoop()) {
                    val nearby=sand.indices.filter { sand[it]>0 && (bridgeOpen || !WorldMap.east(WorldMap.cellX(it))) }.sortedBy { hypot(WorldMap.cellX(it)-WorldMap.worldX(excavatorX),WorldMap.cellZ(it)-excavatorZ) }.firstOrNull()
                    if(nearby!=null)digAt(WorldMap.cellX(nearby),WorldMap.cellZ(nearby))
                    else digAt(WorldMap.worldX(365f),0f)
                }
            } else if(!pour()) {
                // Choose a reachable, unoccupied loading bay on land; clamp before checking map edges.
                val desired=VehicleNavigation.Point(armWorld(620f).first,armWorld(620f).second)
                val origin=vehicleBody(Vehicle.EXCAVATOR).point;val truck=vehicleBody(Vehicle.TRUCK).point
                val candidates=mutableListOf(VehicleNavigation.bounded(desired,Vehicle.TRUCK,bridgeOpen))
                for(i in 0 until 32) {
                    val angle=i*2*PI/32
                    candidates+=VehicleNavigation.bounded(VehicleNavigation.Point(origin.x+cos(angle).toFloat()*7f,origin.z+sin(angle).toFloat()*7f),Vehicle.TRUCK,bridgeOpen)
                }
                // Stay on the truck's own bank: the route planner handles the bridge, not the bay choice.
                val point=candidates.filter { it.distance(origin) in 4.5f..7.4f && WorldMap.east(it.x)==WorldMap.east(truck.x) && VehicleNavigation.land(it,bridgeOpen) && VehicleNavigation.free(it,Vehicle.TRUCK,others(Vehicle.TRUCK)) }
                    .sortedBy { it.distance(desired)+.01f*it.distance(truck) }
                    .firstOrNull { VehicleNavigation.plan(truck,it,Vehicle.TRUCK,others(Vehicle.TRUCK),bridgeOpen).isNotEmpty() } ?: return
                driveTo(Vehicle.TRUCK,WorldMap.gameX(point.x),point.z)
                aimAt(point.x,point.z,300f)
            }
            Vehicle.TRUCK -> { truckGoal=null;tipping=!tipping }
            Vehicle.DOZER -> if(!pushNearestPile())bladeDown=!bladeDown
            Vehicle.CRANE -> craneAction()
            Vehicle.MIXER,Vehicle.WRECKER -> fleet.action(this,selected)
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
        if(dt==0f)return
        if(wind.arena.open) { wind.arena.update(dt);return }
        wind.update(this,dt)
        separateVehicles()
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
        for(v in listOf(Vehicle.MIXER,Vehicle.WRECKER)) {
            val m=fleet.machine(v);val goal=m.goal
            if(goal!=null)when(val move=travel(v,goal,3f,dt)) {
                Travel.Arrived -> { m.goal=nextLeg(v);if(m.goal==null && m.requested) { m.requested=false;val bay=if(v==Vehicle.MIXER)WorkFleet.MIXER_X else WorkFleet.WRECKER_X;if(hypot(WorldMap.worldX(m.x)-bay,m.z-WorkFleet.YARD_Z)<.4f)fleet.action(this,v) } }
                Travel.Stuck -> { m.goal=null;m.requested=false;routes[v.ordinal].clear() }
                is Travel.Moved -> if(move.step>0f) { m.heading=heading(m.heading,move.dx,move.dz,dt);m.travel+=move.step }
            }
        }
        recovery.followBlade(this)
        recovery.update(this,dt)
        village.update(this,dt)
        wash.update(this,dt)
        fleet.update(this,dt)
        keeper.update(dt,bridgeOpen,Vehicle.entries.any { onDeck(vehicleBody(it).point) },Vehicle.entries.any { goalOf(it)?.deck in listOf(1,2) },wind.nearBridge)
        pipes.update(this,dt)
        bedAngle += ((if(tipping) -42f else 0f)-bedAngle)*smoothing
        if(tipping && bedAngle < -28 && load > 0) {
            tipClock += dt
            if(tipClock >= .18f) {
                tipClock=0f; load--; delivered++
                val cell=dumpCell
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
        when(val move=travel(Vehicle.EXCAVATOR,goal,2.4f,dt)) {
            Travel.Arrived -> {
                excavatorGoal=nextLeg(Vehicle.EXCAVATOR)
                if(excavatorGoal!=null)return
                val target=digTarget;digTarget=null
                if(target!=null)aimAt(target.first,target.second,digY(target.first,target.second))
            }
            Travel.Stuck -> { excavatorGoal=null;digTarget=null;routes[Vehicle.EXCAVATOR.ordinal].clear() }
            is Travel.Moved -> if(move.step>0f) { excavatorHeading=heading(excavatorHeading,move.dx,move.dz,dt);excavatorTravel+=move.step }
        }
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
        when(val move=travel(Vehicle.TRUCK,goal,3.5f,dt)) {
            Travel.Arrived -> truckGoal=nextLeg(Vehicle.TRUCK)
            Travel.Stuck -> { truckGoal=null;routes[Vehicle.TRUCK.ordinal].clear() }
            is Travel.Moved -> if(move.step>0f) { truckHeading=heading(truckHeading,move.dx,move.dz,dt);truckTravel+=move.step }
        }
    }
    private fun advanceDozer(dt:Float) {
        val goal=dozerGoal ?: return
        val move=when(val result=travel(Vehicle.DOZER,goal,2.8f,dt)) {
            Travel.Arrived -> { dozerGoal=nextLeg(Vehicle.DOZER);return }
            Travel.Stuck -> { dozerGoal=null;routes[Vehicle.DOZER.ordinal].clear();return }
            is Travel.Moved -> result
        }
        if(move.step<=0f)return
        val dx=move.dx;val dz=move.dz;val distance=hypot(dx,dz)
        dozerHeading=heading(dozerHeading,dx,dz,dt);dozerFacing=if(dx<0)-1 else 1
        dozerTravel+=move.step
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
    fun toggleMode() { leavePipes();missionMode=!missionMode; mission=0; delivered=0; flattened=0; pendingReward=false; celebrating=0f; checkMission() }
    fun reset() { wash.reset();village.reset();recovery.reset();wind.reset();fleet.reset();keeper.reset();pipes.reset();blocks.reset();earth.fill(Terrain.INITIAL_EARTH);houseFill=0;houseStage=0;buildClock=0f;bridgeFill=0;bridgeStage=0;bridgeClock=0f;craneX=WorldMap.gameX(CRANE_SPOT_X);craneZ=CRANE_SPOT_Z;craneHeading=0f;craneTravel=0f;craneAngle=-90f;craneReach=4.7f;hookY=HOOK_REST;craneCargo=0; excavatorX=200f;excavatorZ=0f;excavatorHeading=0f;excavatorTravel=0f;road.fill(0);dug.fill(0);truckX=620f; dozerX=1000f; armX=365f; armY=340f; targetX=armX; targetY=armY
        bucket=0; load=0; source=36; sand.fill(0); Terrain.QUARRY.forEach { sand[it]=Terrain.QUARRY_SAND }; delivered=0; flattened=0; mission=0
        tipping=false;stopMovement();truckZ=0f;dozerZ=2.25f;truckHeading=0f;dozerHeading=0f;truckTravel=0f;dozerTravel=0f; bladeDown=true; dozerFacing=1; bedAngle=0f; celebrating=0f; tipClock=0f; pendingReward=false }
    fun snapshot():String="19|"+listOf(truckX,dozerX,armX,armY,bucket,load,source,delivered,flattened,mission,if(missionMode)1 else 0,selected.ordinal,if(bladeDown)1 else 0,if(tipping)1 else 0,dozerFacing,bedAngle,truckZ,dozerZ,truckHeading,dozerHeading,excavatorX,excavatorZ,excavatorHeading).joinToString(",")+";"+sand.joinToString(",")+";"+road.joinToString(",")+";"+dug.joinToString(",")+";"+earth.joinToString(",")+";"+listOf(houseFill,houseStage).joinToString(",")+";"+listOf(bridgeFill,bridgeStage).joinToString(",")+";"+listOf(craneX,craneZ,craneHeading,craneAngle,craneReach,craneCargo).joinToString(",")+";"+blocks.snapshot()+";"+pipes.snapshot()+";"+fleet.snapshot()+";"+wind.snapshot()+";"+wind.arena.snapshot()+";"+recovery.snapshot()+";"+village.snapshot()+";"+wash.snapshot()
    fun restore(value:String?) {
        if(value==null)return
        runCatching {
            val alpha19=value.startsWith("19|")
            val alpha18=alpha19 || value.startsWith("18|")
            val alpha17=alpha18 || value.startsWith("17|")
            val alpha16=alpha17 || value.startsWith("16|")
            val alpha15=alpha16 || value.startsWith("15|")
            val alpha14=alpha15 || value.startsWith("14|")
            val alpha13=alpha14 || value.startsWith("13|")
            val alpha12=alpha13 || value.startsWith("12|")
            val alpha11=value.startsWith("11|")
            val alpha10=alpha12 || alpha11 || value.startsWith("10|")
            val alpha9=alpha10 || value.startsWith("9|")
            val alpha8=alpha9 || value.startsWith("8|")
            val alpha7=alpha8 || value.startsWith("7|")
            val alpha6=alpha7 || value.startsWith("6|")
            val modern=alpha6 || value.startsWith("5|")
            val parts=value.removePrefix("19|").removePrefix("18|").removePrefix("17|").removePrefix("16|").removePrefix("15|").removePrefix("14|").removePrefix("13|").removePrefix("12|").removePrefix("11|").removePrefix("10|").removePrefix("9|").removePrefix("8|").removePrefix("7|").removePrefix("6|").removePrefix("5|").split(';');require(parts.size==if(alpha18)16 else if(alpha17)15 else if(alpha16)14 else if(alpha15)13 else if(alpha14)12 else if(alpha13)11 else if(alpha12 || alpha11)10 else if(alpha10)9 else if(alpha9)8 else if(alpha8)7 else if(alpha7)6 else if(alpha6)4 else 2)
            // Saves before alpha.12 hold the 62-column map; widen them, east-bank cells start fresh.
            val cells=if(alpha16)WorldMap.CELL_COUNT else if(alpha13)WorldMap.COLUMNS*WorldMap.ALPHA20_ROWS else if(alpha8)WorldMap.COLUMNS*WorldMap.LEGACY_ROWS else WorldMap.LEGACY_COLUMNS*WorldMap.LEGACY_ROWS
            fun widen(index:Int)=if(alpha8)index else index/WorldMap.LEGACY_COLUMNS*WorldMap.COLUMNS+index%WorldMap.LEGACY_COLUMNS
            val v=parts[0].split(',');val piles=parts[1].split(',').map { it.toInt().also { n->require(n in 0..(if(alpha7)Terrain.INITIAL_MASS else 36)) } }
            require(if(modern)v.size==(if(alpha6)23 else 20) && piles.size==cells else v.size in listOf(12,16) && piles.size==24)
            fun finite(index:Int,min:Float,max:Float)=v[index].toFloat().also { require(it.isFinite()) }.coerceIn(min,max)
            reset()
            truckX=finite(0,WorldMap.DRIVE_MIN_X,WorldMap.EAST_MAX_X);dozerX=finite(1,WorldMap.DRIVE_MIN_X,WorldMap.EAST_MAX_X)
            val end=ArmRig.limit(finite(2,285f,825f),finite(3,220f,550f));armX=end.first;armY=end.second;targetX=armX;targetY=armY
            bucket=v[4].toInt().coerceIn(0,2);load=v[5].toInt().coerceIn(0,8);source=v[6].toInt().coerceIn(0,36)
            delivered=v[7].toInt().coerceAtLeast(0);flattened=v[8].toInt().coerceAtLeast(0);mission=v[9].toInt().coerceIn(0,if(alpha7)4 else if(alpha6)3 else 2)
            missionMode=v[10]=="1";selected=Vehicle.entries[v[11].toInt().coerceIn(0,if(alpha13)5 else if(alpha9)3 else 2)]
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
            if(alpha10) { blocks.restore(parts[8]);require(blocks.carried<0 || craneCargo==0) }
            if(alpha12) {
                pipes.restore(parts[9]);require(pipes.carried<0 || (craneCargo==0 && blocks.carried<0))
                for(i in 0..2)if(pipes.covered(i))require((i*2..i*2+1).all { j->PipeSite.cells(j).all { earth[it]==4 && sand[it]==0 } })
            } else if(alpha11) { require(parts[9] in listOf("0","1"));pipes.active=parts[9]=="1" }
            if(alpha13)fleet.restore(parts[10])
            if(alpha14)wind.restore(parts[11])
            if(alpha15)wind.arena.restore(parts[12])
            if(alpha16)recovery.restore(parts[13])
            if(alpha17)village.restore(parts[14],this) else village.migrate(this)
            if(alpha19)wash.restore(parts[15]) else if(alpha18)wash.restoreLegacy(parts[15])
            require(recovery.held<0 || (blocks.carried<0 && pipes.carried<0 && craneCargo==0))
            if(!bridgeOpen)require(listOf(fleet.mixer,fleet.wrecker).all { it.x<=WorldMap.DRIVE_MAX_X })
            require(listOf(fleet.mixer,fleet.wrecker).all { VehicleNavigation.land(VehicleNavigation.Point(WorldMap.worldX(it.x),it.z),bridgeOpen) })
            if(pipes.active)moveArm(armX,300f)
            require(totalMaterial==Terrain.INITIAL_MASS);separateVehicles();checkMission()
        }.onFailure { reset();selected=Vehicle.EXCAVATOR;missionMode=false }
    }
    companion object {
        const val BRIDGE_STAGE_SECONDS=3.2f
        /** Crane work spot beside the bridge yard, and its reach and hook heights in metres. */
        const val CRANE_SPOT_X=46.5f;const val CRANE_SPOT_Z=9.2f
        const val CRANE_MIN_REACH=2.5f;const val CRANE_MAX_REACH=9f
        const val HOOK_REST=2.6f;const val HOOK_SWING=3.1f
        /** How long a vehicle with no free path waits for a moving machine before giving up. */
        const val BLOCKED_SECONDS=3f
        /** Metres before the bridge end where a crossing vehicle waits for the deck to clear. */
        const val BRIDGE_QUEUE=6f
        /** Loose sand in a new world: the 36-unit source plus the east-bank quarry. */
        const val START_SAND=36+6*Terrain.QUARRY_SAND
    }
}
