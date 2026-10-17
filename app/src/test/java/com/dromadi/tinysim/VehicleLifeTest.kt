package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class VehicleLifeTest {
    @Test fun motionFollowsTravelThenSettlesWithADampedBob() {
        val m=VehicleMotion();var travel=0f
        repeat(60) { travel+=3f*.016f;m.update(travel,.016f) }
        assertTrue(m.moving);assertEquals(1f,m.effort,.05f);assertEquals(0f,m.settle,0f)
        var maxBob=0f;var crossings=0;var previous=0f
        repeat(120) { m.update(travel,.016f);val s=m.settle;maxBob=max(maxBob,abs(s));if(s*previous<0f)crossings++;previous=s }
        assertFalse(m.moving)
        assertTrue("bob $maxBob",maxBob in .01f..0.07f);assertTrue("oscillates",crossings>=2)
        repeat(60) { m.update(travel,.016f) };assertEquals(0f,m.settle,0f);assertEquals(0f,m.speed,0f)
    }
    @Test fun firstFrameAndBadInputDoNotSpike() {
        val m=VehicleMotion();m.update(500f,.016f);assertEquals(0f,m.speed,0f)
        m.update(Float.NaN,.016f);m.update(500f,Float.POSITIVE_INFINITY);assertEquals(0f,m.speed,0f)
    }
    private fun energy(sounds:MachineSounds,seconds:Double):Double { var e=0.0;val n=(48000*seconds).toInt();repeat(n) { val v=sounds.next(1/48000.0);assertTrue(v.isFinite() && abs(v)<.95);e+=v*v };return sqrt(e/n) }
    @Test fun machinesAreSilentUntilSomethingHappens() { assertEquals(0.0,energy(MachineSounds(),.5),0.0) }
    @Test fun engineGetsLouderWithEffortAndStopsWhenOff() {
        val s=MachineSounds();s.engine[1]=.18f;val idle=energy(s,1.0)
        s.engine[1]=1f;val full=energy(s,1.0)
        assertTrue("idle $idle full $full",idle>0.002 && full>idle*2)
        s.engine[1]=0f;energy(s,2.0);assertTrue(energy(s,.2)<idle*.05)
    }
    @Test fun hornPourAndHydraulicsAreShortAudibleEvents() {
        val s=MachineSounds()
        s.hornVehicle=1;s.hornSerial++;assertTrue(energy(s,.3)>.02);assertEquals(0.0,energy(MachineSounds(),.1),0.0)
        energy(s,.5);assertTrue("horn ends",energy(s,.2)<1e-9)
        s.pourSerial++;assertTrue(energy(s,.5)>.01);energy(s,.3);assertTrue("pour ends",energy(s,.2)<1e-9)
        s.hydraulic=1f;assertTrue(energy(s,.5)>.005)
    }
}
