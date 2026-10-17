package com.dromadi.tinysim

import kotlin.math.*

/**
 * Smoothed speed and a damped "settle" bounce for one vehicle, derived only from its travel
 * distance. The renderer uses it for body bounce and exhaust; the audio uses it for engine sound.
 */
class VehicleMotion {
    /** Metres per second, eased so engine pitch and effects never jump. */
    var speed=0f
        private set
    private var lastTravel=Float.NaN
    private var sinceStop=10f
    private var peak=0f
    fun update(travel:Float,dt:Float) {
        if(!travel.isFinite() || !dt.isFinite())return
        val step=dt.coerceIn(0f,.1f)
        val raw=if(lastTravel.isNaN() || step<=0f)0f else (abs(travel-lastTravel)/step).coerceAtMost(8f)
        lastTravel=travel
        val wasMoving=moving
        speed+=(raw-speed)*(1f-exp(-10f*step))
        if(wasMoving && !moving) { sinceStop=0f;peak=min(1f,max(speed,1.2f)/3f) } else sinceStop+=step
        if(raw<.05f && speed<.05f)speed=0f
    }
    val moving get()=speed>.25f
    /** Vertical body offset in metres: a short damped bob right after stopping. */
    val settle get()=if(sinceStop>1.2f)0f else .06f*peak*exp(-4.5f*sinceStop)*sin(sinceStop*17f)
    /** Engine effort 0..1 for sound and smoke. */
    val effort get()=(speed/3f).coerceIn(0f,1f)
}
