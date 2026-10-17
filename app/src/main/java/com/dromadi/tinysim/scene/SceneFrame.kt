package com.dromadi.tinysim.scene

import com.dromadi.tinysim.GameState

/** Owned copy: the GL thread never reads or modifies the UI thread's game model. */
data class SceneFrame(
    val truckX: Float, val dozerX: Float, val armX: Float, val armY: Float,
    val targetX: Float, val targetY: Float,
    val bucket: Int, val load: Int, val source: Int, val sand: IntArray,
    val bedAngle: Float, val tipping: Boolean, val bladeDown: Boolean, val dozerFacing: Int,
    val selected: GameState.Vehicle, val transferSerial: Int, val camera: Int, val seconds: Float,
    val yaw: Float=0f, val pitch: Float=0f, val zoom: Float=1f, val moving: Int=0
) {
    companion object {
        fun capture(g: GameState,camera: Int,seconds: Float,yaw: Float=0f,pitch: Float=0f,zoom: Float=1f)=SceneFrame(
            g.truckX,g.dozerX,g.armX,g.armY,g.targetX,g.targetY,g.bucket,g.load,g.source,g.sand.copyOf(),
            g.bedAngle,g.tipping,g.bladeDown,g.dozerFacing,g.selected,g.transferSerial,camera,seconds,yaw,pitch,zoom,g.direction
        )
    }
}
