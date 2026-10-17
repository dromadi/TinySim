package com.dromadi.tinysim

import kotlin.math.*

/** World metres; game-X coordinates are retained for compatible arm and save data. */
object WorldMap {
    const val MIN_X=-12f;const val MAX_X=74f
    const val MIN_Z=-7f;const val MAX_Z=15f
    const val COLUMNS=86;const val ROWS=22
    /** Width of the map before the river and east bank existed (alpha.7–11 saves). */
    const val LEGACY_COLUMNS=62
    const val CELL_COUNT=COLUMNS*ROWS
    const val DRIVE_MIN_X=510f;const val DRIVE_MAX_X=3420f
    /** East bank limit, reachable only over the finished bridge. */
    const val EAST_MAX_X=4860f
    // River crossing the map north-south; the bridge spans it at BRIDGE_Z.
    const val RIVER_MIN_X=50f;const val RIVER_MAX_X=54f
    const val BRIDGE_Z=4.5f;const val BRIDGE_HALF=1.6f
    const val WEST_BANK_X=48.6f;const val EAST_BANK_X=55.4f
    fun east(worldX:Float)=worldX>(RIVER_MIN_X+RIVER_MAX_X)/2
    fun river(index:Int)=cellX(index) in RIVER_MIN_X..RIVER_MAX_X
    /** Material yard on the west bank where the truck unloads bridge material. */
    fun bridgeYard(index:Int)=cellX(index) in 44f..48f && cellZ(index) in 2f..7f
    const val DRIVE_MIN_Z=-2.8f;const val DRIVE_MAX_Z=11.5f
    fun worldX(gameX:Float)=(gameX-600f)/60f
    fun gameX(worldX:Float)=600f+worldX*60f
    fun cell(x:Float,z:Float):Int {
        val column=floor(x-MIN_X).toInt().coerceIn(0,COLUMNS-1)
        val row=floor(z-MIN_Z).toInt().coerceIn(0,ROWS-1)
        return row*COLUMNS+column
    }
    fun cellX(index:Int)=MIN_X+index%COLUMNS+.5f
    fun cellZ(index:Int)=MIN_Z+index/COLUMNS+.5f
    /** Four 2m road sections, with two rows of sand accepted by each section. */
    fun roadSection(index:Int):Int {
        val x=cellX(index);val z=cellZ(index)
        return if(x in 16f..24f && z in 7f..9f)((x-16f)/2f).toInt().coerceIn(0,3) else -1
    }
    fun neighbor(index:Int,dx:Float,dz:Float):Int {
        val x=index%COLUMNS;val z=index/COLUMNS
        return if(abs(dx)>=abs(dz))z*COLUMNS+(x+if(dx>=0)1 else -1).coerceIn(0,COLUMNS-1)
        else (z+if(dz>=0)1 else -1).coerceIn(0,ROWS-1)*COLUMNS+x
    }
}
data class DriveGoal(val x:Float,val z:Float)
