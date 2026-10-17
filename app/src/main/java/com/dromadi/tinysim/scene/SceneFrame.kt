package com.dromadi.tinysim.scene

import com.dromadi.tinysim.GameState

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
    val excavatorGoal:com.dromadi.tinysim.DriveGoal?=null,val road:IntArray=IntArray(4),val dug:IntArray=IntArray(com.dromadi.tinysim.WorldMap.CELL_COUNT)
) {
    companion object {
        fun capture(g: GameState,camera: Int,seconds: Float,yaw: Float=0f,pitch: Float=0f,zoom: Float=1f,panX:Float=0f,panZ:Float=0f)=SceneFrame(
            g.truckX,g.dozerX,g.armX,g.armY,g.targetX,g.targetY,g.bucket,g.load,g.source,g.sand.copyOf(),
            g.bedAngle,g.tipping,g.bladeDown,g.dozerFacing,g.selected,g.transferSerial,camera,seconds,yaw,pitch,zoom,panX,panZ,g.truckZ,g.dozerZ,g.truckHeading,g.dozerHeading,g.truckTravel,g.dozerTravel,g.truckGoal,g.dozerGoal,g.transferKind,g.excavatorX,g.excavatorZ,g.excavatorHeading,g.excavatorTravel,g.excavatorGoal,g.road.copyOf(),g.dug.copyOf()
        )
    }
}
