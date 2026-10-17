package com.dromadi.tinysim

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.SystemClock

/** Live-synthesized area ambience on a streaming AudioTrack, plus gentle UI tones. */
class GameAudio(private val context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val mixer=SoundscapeMixer()
    @Volatile private var focused=false
    private var focusRequested=false
    private var running=false
    private var tone:ToneGenerator?=null
    private var last=0L
    private var thread:Thread?=null
    @Volatile private var streaming=false
    private var retryAt=0L
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener { change ->
            focused=change==AudioManager.AUDIOFOCUS_GAIN
            // Losing focus stops the stream at once; update() restarts it with a fade-in on regain.
            if(!focused) { stopStream();runCatching { tone?.stopTone() } }
        }.build()
    fun resume() { running=true;if(tone==null)tone=runCatching { ToneGenerator(AudioManager.STREAM_MUSIC,18) }.getOrNull() }
    fun update(enabled:Boolean,area:Int) {
        if(!running)return
        if(enabled && !focusRequested) { focusRequested=true;focused=manager?.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
        if(!enabled)tone?.stopTone()
        mixer.targetZone=area.coerceIn(0,2)
        mixer.targetVolume=if(enabled && focused).22f else 0f
        // A stream that died (e.g. AudioTrack unavailable) is retried after a pause, like the old player.
        if(thread?.isAlive==false) { thread=null;mixer.reset();retryAt=SystemClock.uptimeMillis()+5000 }
        if(enabled && focused && thread==null && SystemClock.uptimeMillis()>=retryAt)startStream()
        // Once muted playback has faded to silence, release the track and the focus.
        if(!enabled && mixer.volume==0f) { stopStream();abandonFocus() }
    }
    private fun startStream() {
        streaming=true
        thread=Thread({
            val frames=2048
            val minimum=AudioTrack.getMinBufferSize(Soundscape.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)
            val track=runCatching {
                AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(Soundscape.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minimum,frames*4*2)).setTransferMode(AudioTrack.MODE_STREAM).build()
            }.getOrNull() ?: return@Thread
            try {
                val pcm=ShortArray(frames*2)
                track.play()
                while(streaming) {
                    mixer.render(pcm)
                    if(track.write(pcm,0,pcm.size)<0)break
                }
                track.pause();track.flush()
            } catch(_:Exception) {
            } finally { track.release() }
        },"TinySim-ambience").apply { priority=Thread.MAX_PRIORITY;start() }
    }
    private fun stopStream() {
        streaming=false
        val worker=thread ?: return
        thread=null
        runCatching { worker.join(500) }
        if(!worker.isAlive)mixer.reset()
    }
    private fun abandonFocus() {
        if(focusRequested)manager?.abandonAudioFocusRequest(focus)
        focusRequested=false;focused=false
    }
    fun play(enabled:Boolean,reward:Boolean=false) {
        if(!enabled || !focused || SystemClock.uptimeMillis()-last<180)return
        last=SystemClock.uptimeMillis();tone?.startTone(if(reward)ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,if(reward)180 else 70)
    }
    fun pause() { running=false;stopStream();abandonFocus();tone?.stopTone();tone?.release();tone=null }
}
