package com.dromadi.tinysim

import kotlin.math.*

/** Continuous heightfield shared by drawing, picking and vehicle support. One unit is .22m soil. */
object Terrain {
    const val INITIAL_EARTH=4
    const val INITIAL_MASS=36+WorldMap.CELL_COUNT*INITIAL_EARTH
    fun height(earth:IntArray,sand:IntArray,x:Float,z:Float):Float {
        val gx=(x-WorldMap.MIN_X-.5f).coerceIn(0f,WorldMap.COLUMNS-1f)
        val gz=(z-WorldMap.MIN_Z-.5f).coerceIn(0f,WorldMap.ROWS-1f)
        val ix=floor(gx).toInt();val iz=floor(gz).toInt()
        fun value(dx:Int,dz:Int):Float {
            val i=(iz+dz).coerceAtMost(WorldMap.ROWS-1)*WorldMap.COLUMNS+(ix+dx).coerceAtMost(WorldMap.COLUMNS-1)
            return .07f+(earth[i]-INITIAL_EARTH)*.22f+sand[i].coerceAtMost(24)*.13f
        }
        val u=gx-ix;val v=gz-iz
        return (value(0,0)*(1-u)+value(1,0)*u)*(1-v)+(value(0,1)*(1-u)+value(1,1)*u)*v
    }
    fun foundation(index:Int)=WorldMap.cellX(index) in 33f..39f && WorldMap.cellZ(index) in 5f..9f
    fun diggable(index:Int)=WorldMap.cellX(index) in -9f..47f && WorldMap.cellZ(index) in -2.8f..11.5f && WorldMap.roadSection(index)<0
}
