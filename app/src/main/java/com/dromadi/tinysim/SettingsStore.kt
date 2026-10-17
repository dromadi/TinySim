package com.dromadi.tinysim
import android.content.Context
class SettingsStore(context: Context) {
    private val prefs=context.getSharedPreferences("tinysim",Context.MODE_PRIVATE)
    var sound: Boolean
        get()=prefs.getBoolean("sound",true)
        set(value) { prefs.edit().putBoolean("sound",value).apply() }
    var camera: Int
        get()=prefs.getInt("camera",0).coerceIn(0,2)
        set(value) { prefs.edit().putInt("camera",value.coerceIn(0,2)).apply() }
    fun save(game: GameState) { prefs.edit().putString("world",game.snapshot()).apply() }
    fun restore(game: GameState) { game.restore(prefs.getString("world",null)) }
}
