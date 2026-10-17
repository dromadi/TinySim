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

/**
 * Live machine sounds: a diesel engine per vehicle whose pitch follows speed, hydraulic whine
 * while the excavator arm moves, a sand hiss for every transfer and a friendly two-note horn.
 * Parameters are written by the UI thread; synthesis runs on the audio thread.
 */
class MachineSounds {
    /** Engine effort per vehicle (excavator, truck, dozer, crane): 0 off, ~.2 idle, 1 full. */
    val engine=FloatArray(4)
    @Volatile var hydraulic=0f
    @Volatile var blockSerial=0
    private var blockSeen=0;private var blockAge=1.0
    @Volatile var pourSerial=0
    @Volatile var hornSerial=0
    @Volatile var hornVehicle=0
    private val random=Random(911)
    private val phase=DoubleArray(4);private val level=DoubleArray(4);private val rumble=DoubleArray(4)
    private var whinePhase=0.0;private var whine=0.0;private var hissLow=0.0;private var hissBand=0.0
    private var pourSeen=0;private var pourLeft=0.0
    private var hornSeen=0;private var hornAge=10.0;private var hornKind=0;private var hornPhase=0.0;private var hornPhase2=0.0
    /** Mono sample for this instant, roughly within ±0.3. */
    fun next(step:Double):Double {
        var out=0.0
        for(v in 0..3) {
            val target=engine[v].toDouble().coerceIn(0.0,1.0)
            level[v]+=(target-level[v])*step*6
            if(level[v]<1e-4)continue
            // Firing frequency: big machines chug low, the truck revs a little higher.
            val base=if(v==1)34.0 else 27.0
            phase[v]+=2*PI*(base+level[v]*(if(v==1)38.0 else 26.0))*step
            val p=phase[v]
            rumble[v]=.995*rumble[v]+.005*(random.nextDouble()*2-1)
            val chug=.55*sin(p)+.28*sin(2*p+.6)+.16*sin(3*p+1.3)+.08*sin(5*p)
            val knock=max(0.0,sin(p))
            out+=level[v]*(.06*chug*(.7+.3*knock)+.5*rumble[v])
        }
        whine+=(hydraulic.toDouble().coerceIn(0.0,1.0)-whine)*step*8
        if(whine>1e-4) {
            whinePhase+=2*PI*(480+140*whine)*step
            hissLow=.96*hissLow+.04*(random.nextDouble()*2-1)
            out+=whine*(.018*sin(whinePhase)+.012*sin(whinePhase*2.01)+.25*hissLow)
        }
        if(pourSerial!=pourSeen) { pourSeen=pourSerial;pourLeft=.75 }
        if(pourLeft>0) {
            pourLeft-=step
            val noise=random.nextDouble()*2-1
            hissBand=.7*hissBand+.3*noise
            val grains=if(random.nextDouble()<.004)(random.nextDouble()-.5)*.6 else 0.0
            out+=(.09*(noise-hissBand)+grains)*min(1.0,pourLeft/.25)*min(1.0,(.75-pourLeft)/.03)
        }
        if(hornSerial!=hornSeen) { hornSeen=hornSerial;hornAge=0.0;hornKind=hornVehicle }
        if(hornAge<.6) {
            hornAge+=step
            // Truck: beep-beep; tracked machines: one longer, lower toot.
            val on=if(hornKind==1)(hornAge<.16 || hornAge in .24..0.40) else hornAge<.38
            if(on) {
                val f=if(hornKind==1)440.0 else 330.0
                hornPhase+=2*PI*f*step;hornPhase2+=2*PI*f*1.26*step
                val square={ x:Double-> sin(x)+sin(3*x)/3+sin(5*x)/5 }
                out+=.045*(square(hornPhase)+square(hornPhase2))
            }
        }
        if(blockSerial!=blockSeen) { blockSeen=blockSerial;blockAge=0.0 }
        if(blockAge<.35) {
            blockAge+=step
            val envelope=min(1.0,blockAge/.012)*exp(-blockAge*17)
            out+=.14*envelope*(sin(2*PI*220*blockAge)+.4*sin(2*PI*355*blockAge))
        }
        return out
    }
}

/** Mixes the active area with a crossfade from the previous one and ramps the master volume. */
class SoundscapeMixer {
    val machines=MachineSounds()
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
            // Machines play at full loudness (they share the master fade), ambience stays soft.
            val machine=machines.next(step)*level/AMBIENCE_VOLUME
            out[i*2]=((l.coerceIn(-.35,.35)*level+machine).coerceIn(-.95,.95)*32767).toInt().toShort()
            out[i*2+1]=((r.coerceIn(-.35,.35)*level+machine).coerceIn(-.95,.95)*32767).toInt().toShort()
        }
        volume=level
    }
    /** Back to silence so the next stream fades in; call only while no thread is rendering. */
    fun reset() { volume=0f;current=null;previous=null }
    companion object { const val CROSSFADE_SECONDS=2.0;const val AMBIENCE_VOLUME=.22f }
}
