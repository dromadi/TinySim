package com.dromadi.tinysim

import kotlin.math.*

/** Two rigid links in game coordinates; the elbow stays above the work surface. */
object ArmRig {
    const val PIVOT_X=243f
    const val PIVOT_Y=412f
    const val UPPER=198f
    const val LOWER=228f
    const val REACH=423f
    fun limit(x:Float,y:Float):Pair<Float,Float> {
        val dx=x.coerceIn(285f,825f)-PIVOT_X
        val dy=y.coerceIn(220f,550f)-PIVOT_Y
        val distance=hypot(dx,dy)
        val ratio=min(1f,REACH/distance)
        return PIVOT_X+dx*ratio to PIVOT_Y+dy*ratio
    }
    fun elbow(x:Float,y:Float):Pair<Float,Float> {
        val end=limit(x,y);val dx=end.first-PIVOT_X;val dy=end.second-PIVOT_Y
        val distance=hypot(dx,dy)
        val along=(UPPER*UPPER-LOWER*LOWER+distance*distance)/(2*distance)
        val height=sqrt((UPPER*UPPER-along*along).coerceAtLeast(0f))
        return PIVOT_X+dx*along/distance+dy*height/distance to
            PIVOT_Y+dy*along/distance-dx*height/distance
    }
}
