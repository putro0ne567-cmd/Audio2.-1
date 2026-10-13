package com.stereodsp.data

import android.content.Context
import com.stereodsp.dsp.Channel
import com.stereodsp.dsp.ChannelSettings
import com.stereodsp.dsp.Preset
import com.stereodsp.dsp.channelSettingsFromJson
import com.stereodsp.dsp.toJson
import org.json.JSONArray
import org.json.JSONObject

/** Menyimpan preset pengguna dan pengaturan terakhir, terpisah untuk kanal L dan R. */
class PresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("stereo_dsp", Context.MODE_PRIVATE)

    fun loadCurrent(ch: Channel): ChannelSettings? {
        val raw = prefs.getString("current_${ch.label}", null) ?: return null
        return try {
            channelSettingsFromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun saveCurrent(ch: Channel, s: ChannelSettings) {
        prefs.edit().putString("current_${ch.label}", s.toJson().toString()).apply()
    }

    fun loadPresets(ch: Channel): List<Preset> {
        val raw = prefs.getString("presets_${ch.label}", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Preset(o.getString("name"), channelSettingsFromJson(o.getJSONObject("settings")))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePresets(ch: Channel, presets: List<Preset>) {
        val arr = JSONArray()
        presets.forEach {
            arr.put(JSONObject().put("name", it.name).put("settings", it.settings.toJson()))
        }
        prefs.edit().putString("presets_${ch.label}", arr.toString()).apply()
    }
}
