package com.dromadi.tinysim

import kotlin.math.hypot

/** The next thing a child could touch. Drives the pointing hand and the gentle idle assist. */
sealed class Hint {
    data class Card(val vehicle:GameState.Vehicle):Hint()
    object Action:Hint()
    object Blocks:Hint()
    data class Pipe(val id:Int):Hint()
    data class Garden(val house:Int):Hint()
    data class Wash(val patch:Int):Hint()
    object Rinse:Hint()
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
        if(g.wash.open) {
            if(!g.wash.ready(g))return Hint.Action
            if(g.wash.tool==WashBay.Tool.SOAP) {
                val i=(0..7).firstOrNull { g.wash.mud[it]>.005f && g.wash.foam[it]<.95f }
                return if(i!=null)Hint.Wash(i) else Hint.Rinse
            }
            val i=(0..7).firstOrNull { g.wash.mud[it]>.005f || g.wash.foam[it]>.005f }
            return if(i!=null)Hint.Wash(i) else Hint.Action
        }
        if(g.recovery.engaged) {
            if(g.recovery.complete)g.village.nextGarden(g).takeIf { it>=0 }?.let { return Hint.Garden(it) }
            val v=g.recovery.nextVehicle()
            return if(v==null || g.selected==v)Hint.Action else Hint.Card(v)
        }
        fun use(vehicle:GameState.Vehicle)=if(g.selected==vehicle)Hint.Action else Hint.Card(vehicle)
        /** Near the target the truck tips only if its bed pours onto it; parked askew, it first drives onto the spot. */
        fun driveTruck(x:Float,z:Float,fits:(Int)->Boolean={ true }):Hint {
            val distance=hypot(WorldMap.worldX(g.truckX)-x,g.truckZ-z)
            return when {
                g.selected!=GameState.Vehicle.TRUCK -> Hint.Card(GameState.Vehicle.TRUCK)
                distance<.3f || (distance<2.5f && fits(g.dumpCell)) -> Hint.Action
                else -> Hint.Ground(x,z)
            }
        }
        if(g.pipes.active && !g.pipes.complete) {
            if(g.pipes.carried>=0)return use(GameState.Vehicle.CRANE)
            if(g.bucket>0)return use(GameState.Vehicle.EXCAVATOR)
            val id=g.pipes.next(g)
            if(id>=0) {
                val v=if(id<6)GameState.Vehicle.EXCAVATOR else if(id<9)GameState.Vehicle.CRANE else GameState.Vehicle.DOZER
                return if(g.selected==v)Hint.Pipe(id) else Hint.Card(v)
            }
        }
        if(g.selected==GameState.Vehicle.MIXER && g.fleet.concrete>0)return Hint.Action
        if(g.selected==GameState.Vehicle.WRECKER && g.fleet.hits<3)return Hint.Action
        val house=(g.source==0 || g.delivered>=4) && g.houseStage==0
        // With the house finished, the next project is the bridge across the river.
        val bridge=g.houseStage>=3 && g.bridgeStage==0 && g.bridgeFill<8
        val digging=house && !g.houseReady
        return when {
            g.blocks.carried>=0 -> use(GameState.Vehicle.CRANE)
            g.bridgeOpen && !g.blocks.complete && g.bucket==0 && g.load==0 && g.craneCargo==0 -> if(g.selected!=GameState.Vehicle.CRANE)Hint.Card(GameState.Vehicle.CRANE) else if(hypot(g.craneWorldX-BlockYard.TARGET_X,g.craneZ-BlockYard.TARGET_Z)>=12f)Hint.Blocks else Hint.Action
            g.tipping -> use(GameState.Vehicle.TRUCK)
            // A full truck while the foundation is still open empties at the spare spot.
            digging && g.load>=8 -> driveTruck(SPARE_X,SPARE_Z)
            g.bucket>0 -> use(GameState.Vehicle.EXCAVATOR)
            g.craneCargo>0 -> use(GameState.Vehicle.CRANE)
            // The child's crane lifts each bridge piece once the yard is full.
            g.bridgeReady && g.bridgeClock==0f -> use(GameState.Vehicle.CRANE)
            house && g.houseReady && g.houseFill<8 && g.load>0 -> driveTruck(HOUSE_X,HOUSE_Z,Terrain::foundation)
            bridge && g.load>0 -> driveTruck(YARD_X,YARD_Z,WorldMap::bridgeYard)
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
