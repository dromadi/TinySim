package com.dromadi.tinysim.scene

import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.WorldMap

/** Immutable extension; no service-machine objects or arrays cross to the GL thread. */
data class WorkFrame(
    val mixerX:Float=WorldMap.gameX(10f),val mixerZ:Float=19f,val mixerHeading:Float=0f,val mixerTravel:Float=0f,
    val wreckX:Float=WorldMap.gameX(40f),val wreckZ:Float=19f,val wreckHeading:Float=0f,val wreckTravel:Float=0f,
    val mixerGoal:com.dromadi.tinysim.DriveGoal?=null,val wreckGoal:com.dromadi.tinysim.DriveGoal?=null,
    val concrete:Int=8,val pad:Int=0,val hits:Int=0,val drum:Float=0f,val pouring:Boolean=false,val swing:Float=0f,val ball:Float=0f,val debris:Float=0f,
    val gate:Float=0f,val keeperWave:Float=0f
)

data class WindFrame(val active:Boolean=false,val strength:Float=0f,val x:Float=24f,val z:Float=0f,val phase:Float=0f,
    val lift:FloatArray=FloatArray(3),val trailX:Float=24f,val trailZ:Float=0f,val trailAge:Float=1f)

data class ArenaFrame(val open:Boolean=false,val map:Int=0,val kind:Int=0,val running:Boolean=false,
    val strength:Float=0f,val x:Float=64.5f,val z:Float=17f,val phase:Float=0f,val scale:Float=.75f,val level:Int=1,
    val buddy:Boolean=false,val buddyX:Float=60f,val buddyZ:Float=12f,val puller:IntArray=IntArray(48),
    val state:IntArray=IntArray(48),val px:FloatArray=FloatArray(48),val pz:FloatArray=FloatArray(48),val py:FloatArray=FloatArray(48),val frost:FloatArray=FloatArray(48)) {
    companion object {
        fun capture(a:com.dromadi.tinysim.TornadoArena)=ArenaFrame(a.open,a.map,a.kind.ordinal,a.running,a.strength,a.x,a.z,a.phase,a.scale,a.board.level,a.buddy,a.buddyX,a.buddyZ,a.board.puller.copyOf(),a.board.state.copyOf(),a.board.x.copyOf(),a.board.z.copyOf(),a.board.y.copyOf(),a.board.frost.copyOf())
    }
}

data class RecoveryFrame(val engaged:Boolean=false,val state:IntArray=IntArray(16),val x:FloatArray=FloatArray(16),val z:FloatArray=FloatArray(16),val y:FloatArray=FloatArray(16),val pads:IntArray=IntArray(4),val held:Int=-1,val pouring:Int=-1,val level:Int=1) {
    companion object { fun capture(r:com.dromadi.tinysim.RecoverySite)=RecoveryFrame(r.engaged,r.state.copyOf(),r.x.copyOf(),r.z.copyOf(),r.y.copyOf(),r.pads.copyOf(),r.held,r.pouring,r.level) }
}

data class VillageFrame(val welcomed:Int=0,val planted:Int=0,val outside:BooleanArray=BooleanArray(4),
    val arrival:FloatArray=FloatArray(4),val growth:FloatArray=FloatArray(4),val wave:FloatArray=FloatArray(4)) {
    companion object { fun capture(g:GameState)=VillageFrame(g.village.welcomed,g.village.planted,
        BooleanArray(4) { g.village.outside(g,it) },g.village.arrival.copyOf(),g.village.growth.copyOf(),g.village.wave.copyOf()) }
}

data class WashFrame(val open:Boolean=false,val ready:Boolean=false,val mud:FloatArray=FloatArray(8)) {
    companion object { fun capture(g:GameState)=WashFrame(g.wash.open,g.wash.ready(g),g.wash.mud.copyOf()) }
}

/** Owned copy: the GL thread never reads or modifies the UI thread's game model. */
data class SceneFrame(
    val truckX: Float, val dozerX: Float, val armX: Float, val armY: Float,
    val targetX: Float, val targetY: Float,
    val bucket: Int, val load: Int, val source: Int, val sand: IntArray,
    val bedAngle: Float, val tipping: Boolean, val bladeDown: Boolean, val dozerFacing: Int,
    val selected: GameState.Vehicle, val transferSerial: Int, val camera: Int, val seconds: Float,
    val yaw: Float=0f, val pitch: Float=0f, val zoom: Float=1f,
    val panX:Float=0f,val panZ:Float=0f,
    val truckZ:Float=0f,val dozerZ:Float=2.25f,val truckHeading:Float=0f,val dozerHeading:Float=0f,
    val truckTravel:Float=0f,val dozerTravel:Float=0f,
    val truckGoal:com.dromadi.tinysim.DriveGoal?=null,val dozerGoal:com.dromadi.tinysim.DriveGoal?=null,val transferKind:Int=0,
    val excavatorX:Float=200f,val excavatorZ:Float=0f,val excavatorHeading:Float=0f,val excavatorTravel:Float=0f,
    val excavatorGoal:com.dromadi.tinysim.DriveGoal?=null,val road:IntArray=IntArray(4),val dug:IntArray=IntArray(com.dromadi.tinysim.WorldMap.CELL_COUNT),
    val earth:IntArray=IntArray(com.dromadi.tinysim.WorldMap.CELL_COUNT){com.dromadi.tinysim.Terrain.INITIAL_EARTH},
    val houseFill:Int=0,val houseStage:Int=0,val foundationProgress:Int=0,val buildClock:Float=0f,
    val bridgeFill:Int=0,val bridgeStage:Int=0,val bridgeClock:Float=0f,
    val craneX:Float=WorldMap.gameX(com.dromadi.tinysim.GameState.CRANE_SPOT_X),val craneZ:Float=com.dromadi.tinysim.GameState.CRANE_SPOT_Z,val craneHeading:Float=0f,val craneTravel:Float=0f,
    val craneAngle:Float=-90f,val craneReach:Float=4.7f,val hookY:Float=com.dromadi.tinysim.GameState.HOOK_REST,val craneCargo:Int=0,val craneGoal:com.dromadi.tinysim.DriveGoal?=null,
    val blockX:FloatArray=FloatArray(3) { 24f },val blockZ:FloatArray=floatArrayOf(-1f,1f,3f),val blockLevel:IntArray=IntArray(3) { -1 },val blockCarried:Int=-1,
    val pipeActive:Boolean=false,val pipeInstalled:Int=0,val pipeFilled:Int=0,val pipeCarried:Int=-1,
    val work:WorkFrame=WorkFrame(),
    val wind:WindFrame=WindFrame(),
    val arena:ArenaFrame=ArenaFrame(),
    val recovery:RecoveryFrame=RecoveryFrame(),
    val greetSerial:Int=0,val greetVehicle:GameState.Vehicle=GameState.Vehicle.EXCAVATOR,
    val village:VillageFrame=VillageFrame(),val wash:WashFrame=WashFrame()
) {
    companion object {
        fun capture(g: GameState,camera: Int,seconds: Float,yaw: Float=0f,pitch: Float=0f,zoom: Float=1f,panX:Float=0f,panZ:Float=0f)=SceneFrame(
            g.truckX,g.dozerX,g.armX,g.armY,g.targetX,g.targetY,g.bucket,g.load,g.source,g.sand.copyOf(),
            g.bedAngle,g.tipping,g.bladeDown,g.dozerFacing,g.selected,g.transferSerial,camera,seconds,yaw,pitch,zoom,panX,panZ,g.truckZ,g.dozerZ,g.truckHeading,g.dozerHeading,g.truckTravel,g.dozerTravel,g.truckGoal,g.dozerGoal,g.transferKind,g.excavatorX,g.excavatorZ,g.excavatorHeading,g.excavatorTravel,g.excavatorGoal,g.road.copyOf(),g.dug.copyOf(),g.earth.copyOf(),g.houseFill,g.houseStage,g.foundationProgress,g.buildClock,g.bridgeFill,g.bridgeStage,g.bridgeClock,g.craneX,g.craneZ,g.craneHeading,g.craneTravel,g.craneAngle,g.craneReach,g.hookY,g.craneCargo,g.craneGoal,g.blocks.x.copyOf(),g.blocks.z.copyOf(),g.blocks.level.copyOf(),g.blocks.carried,g.pipes.active,g.pipes.installed,g.pipes.filled,g.pipes.carried,WorkFrame(g.fleet.mixer.x,g.fleet.mixer.z,g.fleet.mixer.heading,g.fleet.mixer.travel,g.fleet.wrecker.x,g.fleet.wrecker.z,g.fleet.wrecker.heading,g.fleet.wrecker.travel,g.fleet.mixer.goal,g.fleet.wrecker.goal,g.fleet.concrete,g.fleet.pad,g.fleet.hits,g.fleet.drumAngle,g.fleet.pouring,g.fleet.swing,g.fleet.ballOffset,g.fleet.debris,g.keeper.lift,g.keeper.wave),WindFrame(g.wind.active,g.wind.strength,g.wind.x,g.wind.z,g.wind.phase,g.wind.lift.copyOf(),g.wind.trailX,g.wind.trailZ,g.wind.trailAge),ArenaFrame.capture(g.wind.arena),RecoveryFrame.capture(g.recovery),g.greetSerial,g.greetVehicle,VillageFrame.capture(g),WashFrame.capture(g)
        )
    }
}
