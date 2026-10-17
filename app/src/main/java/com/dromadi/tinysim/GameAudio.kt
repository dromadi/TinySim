package com.dromadi.tinysim

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.SystemClock

/** Long original offline soundscapes; asynchronous preparation and gentle area crossfades. */
class GameAudio(private val context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private var focused=false
    private var focusRequested=false
    private var enabledWanted=false
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener { change ->
            focused=change==AudioManager.AUDIOFOCUS_GAIN
            runCatching {
                if(focused && running && enabledWanted && ready) { volume=0f;player?.setVolume(0f,0f);player?.start() }
                else { if(ready)player?.pause();outgoing?.pause();tone?.stopTone() }
            }
        }.build()
    private var tone:ToneGenerator?=null
    private var player:MediaPlayer?=null
    private var outgoing:MediaPlayer?=null
    private var ready=false
    private var running=false
    private var zone=-1
    private var generation=0
    private var volume=0f
    private var oldVolume=0f
    private var lastUpdate=0L
    private var last=0L
    private var retryAt=0L
    fun resume() { running=true;lastUpdate=0L;if(tone==null)tone=runCatching { ToneGenerator(AudioManager.STREAM_MUSIC,18) }.getOrNull() }
    fun update(enabled:Boolean,area:Int) {
        if(!running)return
        enabledWanted=enabled
        if(enabled && !focusRequested) { focusRequested=true;focused=manager?.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
        val now=SystemClock.uptimeMillis();val dt=if(lastUpdate==0L)0f else ((now-lastUpdate)/1000f).coerceIn(0f,.1f);lastUpdate=now
        if(!enabled)tone?.stopTone()
        if(enabled && focused && now>=retryAt && (player==null || zone!=area))open(area.coerceIn(0,2))
        val target=if(enabled && focused && ready).22f else 0f
        volume=if(volume<target)minOf(target,volume+dt*.12f) else maxOf(target,volume-dt*.22f)
        runCatching { player?.setVolume(volume,volume) }
        oldVolume=maxOf(0f,oldVolume-dt*.22f)
        runCatching { outgoing?.setVolume(oldVolume,oldVolume) }
        if(oldVolume==0f) { outgoing?.release();outgoing=null }
        if(!enabled && volume==0f) { generation++;player?.release();player=null;ready=false;zone=-1;if(focusRequested)manager?.abandonAudioFocusRequest(focus);focusRequested=false;focused=false }
    }
    private fun open(area:Int) {
        outgoing?.release();outgoing=player;oldVolume=volume;player=null;volume=0f;ready=false;zone=area
        val token=++generation
        val next=MediaPlayer();player=next
        try {
            next.setAudioAttributes(attributes)
            context.assets.openFd("soundscapes/"+listOf("quarry","workshop","garden")[area]+".wav").use { next.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
            next.isLooping=true;next.setVolume(0f,0f)
            next.setOnPreparedListener { if(running && token==generation) { ready=true;if(focused && enabledWanted)it.start() } }
            next.setOnErrorListener { _,_,_-> if(token==generation) { ready=false;player?.release();player=null;retryAt=SystemClock.uptimeMillis()+5000 };true }
            next.prepareAsync()
        } catch(_:Exception) { next.release();player=null;ready=false;retryAt=SystemClock.uptimeMillis()+5000 }
    }
    fun play(enabled:Boolean,reward:Boolean=false) {
        if(!enabled || !focused || SystemClock.uptimeMillis()-last<180)return
        last=SystemClock.uptimeMillis();tone?.startTone(if(reward)ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,if(reward)180 else 70)
    }
    fun pause() { running=false;enabledWanted=false;if(focusRequested)manager?.abandonAudioFocusRequest(focus);focusRequested=false;focused=false;generation++;player?.release();outgoing?.release();player=null;outgoing=null;ready=false;zone=-1;volume=0f;oldVolume=0f;tone?.stopTone();tone?.release();tone=null }
}
