package com.dromadi.tinysim

import kotlin.math.*

/** Continuous heightfield shared by drawing, picking and vehicle support. One unit is .22m soil. */
object Terrain {
    const val INITIAL_EARTH=4
    /** Sand waiting in the quarry across the river: 6 cells × 4 units. */
    val QUARRY=listOf(62.5f,63.5f,64.5f).flatMap { x-> listOf(WorldMap.cell(x,3.5f),WorldMap.cell(x,4.5f)) }
    const val QUARRY_SAND=4
    const val INITIAL_MASS=36+WorldMap.CELL_COUNT*INITIAL_EARTH+6*QUARRY_SAND
    const val RIVER_BED=-.9f
    const val DECK=.32f
    /** Ground height; with the bridge open, the deck and its ramps carry vehicles over the river. */
    fun height(earth:IntArray,sand:IntArray,x:Float,z:Float,bridge:Boolean=false):Float {
        if(bridge && abs(z-WorldMap.BRIDGE_Z)<=WorldMap.BRIDGE_HALF && x in WorldMap.WEST_BANK_X..WorldMap.EAST_BANK_X) {
            val ramp=min(x-WorldMap.WEST_BANK_X,WorldMap.EAST_BANK_X-x).coerceIn(0f,1f)
            return ground(earth,sand,x,z)*(1-ramp)+DECK*ramp
        }
        return ground(earth,sand,x,z)
    }
    private fun ground(earth:IntArray,sand:IntArray,x:Float,z:Float):Float {
        val gx=(x-WorldMap.MIN_X-.5f).coerceIn(0f,WorldMap.COLUMNS-1f)
        val gz=(z-WorldMap.MIN_Z-.5f).coerceIn(0f,WorldMap.ROWS-1f)
        val ix=floor(gx).toInt();val iz=floor(gz).toInt()
        fun value(dx:Int,dz:Int):Float {
            val i=(iz+dz).coerceAtMost(WorldMap.ROWS-1)*WorldMap.COLUMNS+(ix+dx).coerceAtMost(WorldMap.COLUMNS-1)
            if(WorldMap.river(i))return RIVER_BED
            return .07f+(earth[i]-INITIAL_EARTH)*.22f+sand[i].coerceAtMost(24)*.13f
        }
        val u=gx-ix;val v=gz-iz
        return (value(0,0)*(1-u)+value(1,0)*u)*(1-v)+(value(0,1)*(1-u)+value(1,1)*u)*v
    }
    fun foundation(index:Int)=WorldMap.cellX(index) in 33f..39f && WorldMap.cellZ(index) in 5f..9f
    fun diggable(index:Int)=(WorldMap.cellX(index) in -9f..47f || WorldMap.cellX(index) in 56f..71f) && WorldMap.cellZ(index) in -2.8f..WorldMap.DRIVE_MAX_Z && WorldMap.roadSection(index)<0
}
