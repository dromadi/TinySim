package com.dromadi.tinysim

/** Loose sand on the west bank, i.e. everything except the untouched east-bank quarry. */
fun GameState.westSand()=sand.indices.sumOf { if(WorldMap.east(WorldMap.cellX(it)))0 else sand[it] }
