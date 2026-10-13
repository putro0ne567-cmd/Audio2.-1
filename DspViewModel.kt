package com.stereodsp

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.stereodsp.audio.AudioEngine
import com.stereodsp.data.PresetStore
import com.stereodsp.dsp.Channel
import com.stereodsp.dsp.ChannelSettings
import com.stereodsp.dsp.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DspViewModel(app: Application) : AndroidViewModel(app) {

    val engine = AudioEngine(app)
    private val store = PresetStore(app)

    private val _left = MutableStateFlow(store.loadCurrent(Channel.LEFT) ?: ChannelSettings())
    private val _right = MutableStateFlow(store.loadCurrent(Channel.RIGHT) ?: ChannelSettings())
    private val _linked = MutableStateFlow(false)
    private val _userL = MutableStateFlow(store.loadPresets(Channel.LEFT))
    private val _userR = MutableStateFlow(store.loadPresets(Channel.RIGHT))

    val left: StateFlow<ChannelSettings> = _left.asStateFlow()
    val right: StateFlow<ChannelSettings> = _right.asStateFlow()
    val linked: StateFlow<Boolean> = _linked.asStateFlow()
    val userPresetsL: StateFlow<List<Preset>> = _userL.asStateFlow()
    val userPresetsR: StateFlow<List<Preset>> = _userR.asStateFlow()

    init {
        pushToEngine()
    }

    private fun flow(ch: Channel) = if (ch == Channel.LEFT) _left else _right
    private fun presetFlow(ch: Channel) = if (ch == Channel.LEFT) _userL else _userR

    private fun pushToEngine() {
        engine.setSettings(_left.value, _right.value)
        store.saveCurrent(Channel.LEFT, _left.value)
        store.saveCurrent(Channel.RIGHT, _right.value)
    }

    /** Ubah pengaturan satu kanal; bila Link aktif, hasilnya disalin ke kanal lain. */
    fun update(ch: Channel, transform: (ChannelSettings) -> ChannelSettings) {
        val new = transform(flow(ch).value)
        flow(ch).value = new
        if (_linked.value) flow(ch.other).value = new
        pushToEngine()
    }

    fun setLinked(on: Boolean) {
        _linked.value = on
    }

    fun applyPreset(ch: Channel, preset: Preset) = update(ch) { preset.settings }

    fun savePreset(ch: Channel, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val list = presetFlow(ch).value.filterNot { it.name == clean } + Preset(clean, flow(ch).value)
        presetFlow(ch).value = list
        store.savePresets(ch, list)
    }

    fun deletePreset(ch: Channel, name: String) {
        val list = presetFlow(ch).value.filterNot { it.name == name }
        presetFlow(ch).value = list
        store.savePresets(ch, list)
    }

    fun copyToOther(from: Channel) {
        flow(from.other).value = flow(from).value
        pushToEngine()
    }

    fun open(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment ?: "audio"
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        } catch (_: Exception) {
        }
        engine.load(uri, name)
    }

    fun togglePause() = engine.togglePause()
    fun stop() = engine.stop()
    fun seek(ms: Long) = engine.seekTo(ms)

    override fun onCleared() {
        engine.release()
    }
}
