package com.dromadi.tinysim
import android.content.Context
class SettingsStore(context: Context) {
    private val prefs=context.getSharedPreferences("tinysim",Context.MODE_PRIVATE)
    var sound: Boolean
        get()=prefs.getBoolean("sound",true)
        set(value) { prefs.edit().putBoolean("sound",value).apply() }
    /** Parent-enabled zoom, camera, minimap and stop buttons; the child HUD stays minimal without it. */
    var advanced: Boolean
        get()=prefs.getBoolean("advanced",false)
        set(value) { prefs.edit().putBoolean("advanced",value).apply() }
    var camera: Int
        get()=prefs.getInt("camera",0).coerceIn(0,2)
        set(value) { prefs.edit().putInt("camera",value.coerceIn(0,2)).apply() }
    var yaw: Float
        get()=finite("yaw",0f,-110f,110f)
        set(value) { prefs.edit().putFloat("yaw",value).apply() }
    var pitch: Float
        get()=finite("pitch",0f,-40f,40f)
        set(value) { prefs.edit().putFloat("pitch",value).apply() }
    var zoom: Float
        get()=finite("zoom",1f,.45f,1.8f)
        set(value) { prefs.edit().putFloat("zoom",value).apply() }
    var panX:Float
        get()=finite("panX",0f,-8f,68f)
        set(value) { prefs.edit().putFloat("panX",value).apply() }
    var panZ:Float
        get()=finite("panZ",0f,-3f,11f)
        set(value) { prefs.edit().putFloat("panZ",value).apply() }
    private fun finite(key:String,fallback:Float,min:Float,max:Float):Float {
        val value=prefs.getFloat(key,fallback)
        return if(value.isFinite())value.coerceIn(min,max) else fallback
    }
    fun save(game: GameState) { prefs.edit().putString("world",game.snapshot()).apply() }
    fun restore(game: GameState) { game.restore(prefs.getString("world",null)) }
}
