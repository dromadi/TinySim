package com.dromadi.tinysim
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.media.AudioManager
class MainActivity: Activity() {
    private lateinit var game: GameView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        volumeControlStream=AudioManager.STREAM_MUSIC
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        game=GameView(this); setContentView(game)
    }
    override fun onResume() { super.onResume(); game.resume() }
    override fun onPause() { game.pause(); super.onPause() }
    @Deprecated("Platform back remains available")
    override fun onBackPressed() { if(!game.closePanel()) super.onBackPressed() }
}
