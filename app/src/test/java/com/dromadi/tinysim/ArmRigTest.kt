package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ArmRigTest {
    @Test fun linksKeepTheirLengthsAcrossWorkspaceAndBeyondReach() {
        for(x in listOf(-1000f,285f,365f,620f,825f,2000f))for(y in listOf(-1000f,220f,340f,440f,466f,2000f)) {
            val end=ArmRig.limit(x,y);val joint=ArmRig.elbow(end.first,end.second)
            assertEquals(ArmRig.UPPER,hypot(joint.first-ArmRig.PIVOT_X,joint.second-ArmRig.PIVOT_Y),.01f)
            assertEquals(ArmRig.LOWER,hypot(joint.first-end.first,joint.second-end.second),.01f)
            assertTrue(hypot(end.first-ArmRig.PIVOT_X,end.second-ArmRig.PIVOT_Y)<=ArmRig.REACH+.01f)
        }
    }
    @Test fun invalidInputDoesNotPoisonSimulation() {
        val g=GameState();val before=g.snapshot()
        g.moveArm(Float.NaN,Float.POSITIVE_INFINITY);g.update(.016f)
        assertEquals(before,g.snapshot());assertEquals(36,g.totalSand)
    }
}
