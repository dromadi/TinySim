package com.dromadi.tinysim

import kotlin.math.*

/** World metres; game-X coordinates are retained for compatible arm and save data. */
object WorldMap {
    const val MIN_X=-12f;const val MAX_X=50f
    const val MIN_Z=-7f;const val MAX_Z=15f
    const val COLUMNS=62;const val ROWS=22
    const val CELL_COUNT=COLUMNS*ROWS
    const val DRIVE_MIN_X=510f;const val DRIVE_MAX_X=3420f
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
