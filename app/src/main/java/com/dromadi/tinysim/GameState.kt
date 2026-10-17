package com.dromadi.tinysim

import kotlin.math.*

/** Deterministic, bounded simulation. Sand units are conserved, including the source. */
class GameState {
    enum class Vehicle { EXCAVATOR, TRUCK, DOZER }
    var selected = Vehicle.EXCAVATOR
    var truckX = 620f
    var dozerX = 1000f
    var armX = 365f
    var armY = 340f
    var targetX = armX
    var targetY = armY
    var bucket = 0
    var load = 0
    var source = 36
    val sand = IntArray(24)
    var delivered = 0
    var flattened = 0
    var missionMode = false
    var mission = 0
    var celebrating = 0f
    var bedAngle = 0f
    var tipping = false
    var direction = 0
    var bladeDown = true
    var dozerFacing = 1
    var rewardSerial = 0
    var transferSerial = 0
    private var tipClock = 0f
    private var pendingReward = false
    val totalSand get() = source + bucket + load + sand.sum()
    val missionProgress get() = when(mission) { 0 -> load.coerceAtMost(4); 1 -> delivered.coerceAtMost(4); else -> flattened.coerceAtMost(4) }
    fun moveArm(x: Float, y: Float) {
        targetX = x.coerceIn(285f, 825f)
        targetY = y.coerceIn(220f, 466f)
    }
    fun scoop(): Boolean {
        if (bucket == 0 && source > 0 && armX < 460 && armY > 380) {
            bucket = min(2, source); source -= bucket; transferSerial++; return true
        }
        return false
    }
    fun pour(): Boolean {
        if (bucket > 0 && abs(armX - truckX) < 110 && armY < 390 && load < 8 && !tipping) {
            val n = min(bucket, 8-load); load += n; bucket -= n; transferSerial++; checkMission(); return true
        }
        return false
    }
    fun action() {
        when(selected) {
            Vehicle.EXCAVATOR -> if (bucket == 0) {
                if (!scoop()) moveArm(365f, 440f)
            } else {
                if (!pour()) moveArm(truckX, 300f)
            }
            Vehicle.TRUCK -> { tipping = !tipping }
            Vehicle.DOZER -> { bladeDown = !bladeDown }
        }
    }
    fun update(rawDt: Float) {
        val dt = rawDt.coerceIn(0f, .04f)
        celebrating = max(0f, celebrating-dt)
        val smoothing = 1f-exp(-8f*dt)
        armX += (targetX-armX)*smoothing; armY += (targetY-armY)*smoothing
        if (selected == Vehicle.TRUCK && !tipping) truckX = (truckX+direction*180*dt).coerceIn(510f, 1110f)
        if (selected == Vehicle.DOZER) {
            if(direction != 0) dozerFacing=direction
            val oldX=dozerX
            dozerX=(dozerX+direction*150*dt).coerceIn(520f, 1110f)
            if (bladeDown && abs(dozerX-oldX) > .001f) {
                val index=((dozerX+direction*72-480)/30).toInt().coerceIn(0,23)
                if(sand[index]>0) {
                    val next=(index+direction).coerceIn(0,23)
                    if(next != index && sand[index] > sand[next]+1) {
                        sand[index]--; sand[next]++; flattened++; checkMission()
                    }
                }
            }
        }
        bedAngle += ((if(tipping) -42f else 0f)-bedAngle)*smoothing
        if(tipping && bedAngle < -28 && load > 0) {
            tipClock += dt
            if(tipClock >= .18f) {
                tipClock=0f; load--; delivered++
                sand[((truckX-90-480)/30).toInt().coerceIn(0,23)]++
                transferSerial++; checkMission()
            }
        }
    }
    private fun checkMission() {
        if (missionMode && missionProgress >= 4 && !pendingReward) {
            celebrating=2.5f; rewardSerial++; pendingReward=true
        }
    }
    fun nextMission() {
        if (pendingReward && celebrating <= 0f) { mission=(mission+1)%3; pendingReward=false
            if(mission==0) { delivered=0; flattened=0 }
            checkMission()
        }
    }
    fun toggleMode() { missionMode=!missionMode; mission=0; delivered=0; flattened=0; pendingReward=false; celebrating=0f; checkMission() }
    fun reset() { truckX=620f; dozerX=1000f; armX=365f; armY=340f; targetX=armX; targetY=armY
        bucket=0; load=0; source=36; sand.fill(0); delivered=0; flattened=0; mission=0
        tipping=false; direction=0; bladeDown=true; dozerFacing=1; bedAngle=0f; celebrating=0f; tipClock=0f; pendingReward=false }
    fun snapshot(): String = listOf(truckX,dozerX,armX,armY,bucket,load,source,delivered,flattened,mission,if(missionMode)1 else 0,selected.ordinal,if(bladeDown)1 else 0,if(tipping)1 else 0,dozerFacing,bedAngle).joinToString(",")+";"+sand.joinToString(",")
    fun restore(value: String?) {
        if(value==null) return
        runCatching {
            val parts=value.split(';'); val v=parts[0].split(','); val piles=parts[1].split(',').map { it.toInt().coerceIn(0,36) }
            require(v.size in listOf(12,16) && piles.size==24)
            truckX=v[0].toFloat().coerceIn(510f,1110f); dozerX=v[1].toFloat().coerceIn(520f,1110f)
            armX=v[2].toFloat().coerceIn(285f,825f); armY=v[3].toFloat().coerceIn(220f,466f)
            require(listOf(truckX,dozerX,armX,armY).all { it.isFinite() })
            targetX=armX; targetY=armY; bucket=v[4].toInt().coerceIn(0,2); load=v[5].toInt().coerceIn(0,8)
            source=v[6].toInt().coerceIn(0,36); delivered=v[7].toInt().coerceAtLeast(0); flattened=v[8].toInt().coerceAtLeast(0)
            mission=v[9].toInt().coerceIn(0,2); missionMode=v[10]=="1"; selected=Vehicle.entries[v[11].toInt().coerceIn(0,2)]
            if(v.size==16) {
                bladeDown=v[12]=="1";tipping=v[13]=="1";dozerFacing=if(v[14].toInt()<0)-1 else 1
                bedAngle=v[15].toFloat().coerceIn(-42f,0f);require(bedAngle.isFinite())
            }
            piles.forEachIndexed { i,n -> sand[i]=n }; require(totalSand==36); checkMission()
        }.onFailure { reset() }
    }
}
