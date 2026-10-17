package com.dromadi.tinysim.scene

import com.dromadi.tinysim.GameState
import com.dromadi.tinysim.WorldMap

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
    val greetSerial:Int=0,val greetVehicle:GameState.Vehicle=GameState.Vehicle.EXCAVATOR
) {
    companion object {
        fun capture(g: GameState,camera: Int,seconds: Float,yaw: Float=0f,pitch: Float=0f,zoom: Float=1f,panX:Float=0f,panZ:Float=0f)=SceneFrame(
            g.truckX,g.dozerX,g.armX,g.armY,g.targetX,g.targetY,g.bucket,g.load,g.source,g.sand.copyOf(),
            g.bedAngle,g.tipping,g.bladeDown,g.dozerFacing,g.selected,g.transferSerial,camera,seconds,yaw,pitch,zoom,panX,panZ,g.truckZ,g.dozerZ,g.truckHeading,g.dozerHeading,g.truckTravel,g.dozerTravel,g.truckGoal,g.dozerGoal,g.transferKind,g.excavatorX,g.excavatorZ,g.excavatorHeading,g.excavatorTravel,g.excavatorGoal,g.road.copyOf(),g.dug.copyOf(),g.earth.copyOf(),g.houseFill,g.houseStage,g.foundationProgress,g.buildClock,g.bridgeFill,g.bridgeStage,g.bridgeClock,g.craneX,g.craneZ,g.craneHeading,g.craneTravel,g.craneAngle,g.craneReach,g.hookY,g.craneCargo,g.craneGoal,g.greetSerial,g.greetVehicle
        )
    }
}
