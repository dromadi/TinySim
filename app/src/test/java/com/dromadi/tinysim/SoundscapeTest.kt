package com.dromadi.tinysim

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SoundscapeTest {
    private fun rms(zone:Int,seconds:Int):Double {
        val s=Soundscape(zone);val out=DoubleArray(2);var energy=0.0
        val n=Soundscape.RATE*seconds
        repeat(n) { s.next(out);energy+=out[0]*out[0]+out[1]*out[1];assertTrue(out[0].isFinite() && abs(out[0])<.35 && abs(out[1])<.35) }
        return sqrt(energy/(2.0*n))
    }
    @Test fun everyAreaIsAudibleQuietAndBounded() {
        for(zone in 0..2) { val level=rms(zone,4);assertTrue("zone $zone rms $level",level in .001..0.05) }
    }
    @Test fun areasSoundDifferentAndAreDeterministic() {
        fun take(zone:Int)=Soundscape(zone).let { s-> val o=DoubleArray(2);DoubleArray(4800) { s.next(o);o[0] } }
        assertArrayEquals(take(1),take(1),0.0)
        assertFalse(take(0).contentEquals(take(1)));assertFalse(take(1).contentEquals(take(2)))
    }
    @Test fun birdsSingInTheirWindow() {
        // Quarry birds call 2.0–3.4 s into every 21.3 s cycle, on top of the wind.
        val s=Soundscape(0);val o=DoubleArray(2);var quiet=0.0;var song=0.0
        for(i in 0 until Soundscape.RATE*3) { s.next(o);val t=i/Soundscape.RATE.toDouble()
            if(t in 1.0..1.6)quiet=max(quiet,abs(o[0]-o[1]));if(t in 2.5..2.9)song=max(song,abs(o[0]-o[1])) }
        assertTrue("song=$song quiet=$quiet",song>quiet)
    }
    @Test fun mixerFadesInOutAndCrossfadesAreasWithoutClicks() {
        val mixer=SoundscapeMixer();val pcm=ShortArray(2048*2)
        mixer.render(pcm);assertTrue(pcm.all { it.toInt()==0 })
        mixer.targetVolume=.22f
        repeat(48000*3/2048) { mixer.render(pcm) }
        assertEquals(.22f,mixer.volume,1e-4f)
        var previous=pcm[pcm.size-2].toInt();var jump=0
        mixer.targetZone=2
        repeat(48000*3/2048) { mixer.render(pcm);for(i in pcm.indices step 2) { jump=max(jump,abs(pcm[i]-previous));previous=pcm[i].toInt() } }
        assertTrue("largest step $jump",jump<1200)
        assertTrue(pcm.any { it.toInt()!=0 })
        mixer.targetVolume=0f
        repeat(48000*2/2048) { mixer.render(pcm) }
        assertEquals(0f,mixer.volume,0f);assertTrue(pcm.takeLast(200).all { it.toInt()==0 })
    }
}
