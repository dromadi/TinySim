package com.dromadi.tinysim

import java.util.Random
import kotlin.math.*

/**
 * Original procedural ambience, synthesized live instead of shipped as WAV files: quarry wind and
 * birds, workshop with a distant motor, and garden with running water. Same recipe as the former
 * build-time pack, but endless, so there is no five-minute loop seam.
 */
class Soundscape(val zone:Int) {
    private val random=Random(7301L+zone)
    private var left=0.0;private var right=0.0;private var low=0.0;private var phase=0.0
    private var sample=0L
    /** Writes one stereo frame into out[0] (left) and out[1] (right). */
    fun next(out:DoubleArray) {
        val t=sample++/RATE.toDouble()
        left=.975*left+.025*(random.nextDouble()*2-1);right=.975*right+.025*(random.nextDouble()*2-1)
        low=.9993*low+.0007*(random.nextDouble()*2-1)
        val gust=.55+.25*sin(t*.071+zone)+.13*sin(t*.193)
        var bird=0.0
        val cycle=t%(if(zone==2)13.7 else 21.3)
        if(cycle>2 && cycle<3.4) {
            val b=cycle-2;val envelope=sin(PI*b/1.4).pow(2)
            phase+=2*PI*(1550+550*sin(b*16)+zone*180)/RATE
            bird=.045*envelope*sin(phase)*(.65+.35*sin(b*50))
        }
        val motor=if(zone==1).018*sin(2*PI*82*t)*(1+.2*sin(t*.4)) else 0.0
        val stream=if(zone==2).025*(random.nextDouble()*2-1)*(.5+.5*sin(t*.37)) else 0.0
        out[0]=left*gust*.6+low*1.1+bird+motor+stream
        out[1]=right*gust*.6+low*1.1+bird*.7+motor+stream*.65
    }
    companion object { const val RATE=48000 }
}

/** Mixes the active area with a crossfade from the previous one and ramps the master volume. */
class SoundscapeMixer {
    @Volatile var targetZone=0
    @Volatile var targetVolume=0f
    /** Current master volume, readable from the UI thread to know when playback is silent. */
    @Volatile var volume=0f
        private set
    private var current:Soundscape?=null
    private var previous:Soundscape?=null
    private var fade=1.0
    private val a=DoubleArray(2);private val b=DoubleArray(2)
    /** Fills interleaved 16-bit stereo PCM; frames=out.size/2. */
    fun render(out:ShortArray) {
        val step=1.0/Soundscape.RATE
        var level=volume
        for(i in 0 until out.size/2) {
            val zone=targetZone.coerceIn(0,2)
            if(current?.zone!=zone) { previous=current;current=Soundscape(zone);fade=if(previous==null)1.0 else 0.0 }
            // Same ramps as before: fade in at 0.12/s, fade out at 0.22/s.
            val target=targetVolume
            level=if(level<target)min(target,level+(step*.12).toFloat()) else max(target,level-(step*.22).toFloat())
            current!!.next(a)
            var l=a[0];var r=a[1]
            val old=previous
            if(old!=null) {
                fade=min(1.0,fade+step/CROSSFADE_SECONDS)
                old.next(b);l=l*fade+b[0]*(1-fade);r=r*fade+b[1]*(1-fade)
                if(fade>=1.0)previous=null
            }
            out[i*2]=(l.coerceIn(-.35,.35)*32767*level).toInt().toShort()
            out[i*2+1]=(r.coerceIn(-.35,.35)*32767*level).toInt().toShort()
        }
        volume=level
    }
    /** Back to silence so the next stream fades in; call only while no thread is rendering. */
    fun reset() { volume=0f;current=null;previous=null }
    companion object { const val CROSSFADE_SECONDS=2.0 }
}
