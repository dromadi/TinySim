package com.dromadi.tinysim
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
/** Quiet, short built-in synthesized tones; no files, microphone or network. */
class GameAudio {
    private var tone: ToneGenerator? = null
    private var last=0L
    fun resume() { if(tone==null) tone=runCatching { ToneGenerator(AudioManager.STREAM_MUSIC,18) }.getOrNull() }
    fun play(enabled: Boolean, reward: Boolean=false) {
        if(!enabled || SystemClock.uptimeMillis()-last<180) return
        last=SystemClock.uptimeMillis()
        tone?.startTone(if(reward) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,if(reward)180 else 70)
    }
    fun pause() { tone?.stopTone(); tone?.release(); tone=null }
}
