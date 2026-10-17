package com.dromadi.tinysim

import kotlin.math.hypot

/** The next thing a child could touch. Drives the pointing hand and the gentle idle assist. */
sealed class Hint {
    data class Card(val vehicle:GameState.Vehicle):Hint()
    object Action:Hint()
    /** A spot on the ground (world metres) for the selected vehicle. */
    data class Ground(val x:Float,val z:Float):Hint()
}

/**
 * Pure rules, no Android. Play starts with the sand loop (scoop → truck → dump → dozer); after one
 * delivery the hand moves on to the house: dig the foundation, deliver the dug earth, and the
 * stages assemble themselves.
 */
object Guide {
    const val HOUSE_X=36f;const val HOUSE_Z=7f
    /** Spare dump spot beside the site, away from the foundation and the road. */
    const val SPARE_X=29f;const val SPARE_Z=10.5f
    /** Where the truck unloads bridge material (west-bank yard). */
    const val YARD_X=46f;const val YARD_Z=4.5f
    fun next(g:GameState):Hint {
        fun use(vehicle:GameState.Vehicle)=if(g.selected==vehicle)Hint.Action else Hint.Card(vehicle)
        fun driveTruck(x:Float,z:Float)=when {
            g.selected!=GameState.Vehicle.TRUCK -> Hint.Card(GameState.Vehicle.TRUCK)
            hypot(WorldMap.worldX(g.truckX)-x,g.truckZ-z)<2.5f -> Hint.Action
            else -> Hint.Ground(x,z)
        }
        val house=(g.source==0 || g.delivered>=4) && g.houseStage==0
        // With the house finished, the next project is the bridge across the river.
        val bridge=g.houseStage>=3 && g.bridgeStage==0 && g.bridgeFill<8
        val digging=house && !g.houseReady
        return when {
            g.tipping -> use(GameState.Vehicle.TRUCK)
            // A full truck while the foundation is still open empties at the spare spot.
            digging && g.load>=8 -> driveTruck(SPARE_X,SPARE_Z)
            g.bucket>0 -> use(GameState.Vehicle.EXCAVATOR)
            house && g.houseReady && g.houseFill<8 && g.load>0 -> driveTruck(HOUSE_X,HOUSE_Z)
            bridge && g.load>0 -> driveTruck(YARD_X,YARD_Z)
            !house && !bridge && g.load>=4 -> use(GameState.Vehicle.TRUCK)
            !house && !bridge && g.sand.indices.any { g.sand[it]>1 && it !in Terrain.QUARRY } -> use(GameState.Vehicle.DOZER)
            digging -> {
                val cell=g.earth.indices.firstOrNull { Terrain.foundation(it) && g.earth[it]==Terrain.INITIAL_EARTH }
                when {
                    g.selected!=GameState.Vehicle.EXCAVATOR -> Hint.Card(GameState.Vehicle.EXCAVATOR)
                    cell!=null -> Hint.Ground(WorldMap.cellX(cell),WorldMap.cellZ(cell))
                    else -> Hint.Action
                }
            }
            else -> use(GameState.Vehicle.EXCAVATOR)
        }
    }
}
