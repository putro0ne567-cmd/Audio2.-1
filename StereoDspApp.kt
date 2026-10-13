package com.stereodsp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stereodsp.DspViewModel
import com.stereodsp.audio.PlayerState
import com.stereodsp.dsp.BandType
import com.stereodsp.dsp.BuiltInPresets
import com.stereodsp.dsp.Channel
import com.stereodsp.dsp.ChannelSettings
import com.stereodsp.dsp.EqBand
import com.stereodsp.dsp.FilterChain
import com.stereodsp.dsp.PassFilter
import com.stereodsp.dsp.Preset
import com.stereodsp.dsp.Slope
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StereoDspApp(vm: DspViewModel) {
    val left by vm.left.collectAsStateWithLifecycle()
    val right by vm.right.collectAsStateWithLifecycle()
    val linked by vm.linked.collectAsStateWithLifecycle()
    val userL by vm.userPresetsL.collectAsStateWithLifecycle()
    val userR by vm.userPresetsR.collectAsStateWithLifecycle()
    val player by vm.engine.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Stereo DSP") }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerCard(
                player = player,
                onOpen = { picker.launch(arrayOf("audio/*")) },
                onToggle = vm::togglePause,
                onStop = vm::stop,
                onSeek = vm::seek,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Link L/R (ubah keduanya sekaligus)", Modifier.weight(1f))
                Switch(checked = linked, onCheckedChange = vm::setLinked)
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Kiri (L)") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Kanan (R)") })
            }

            val ch = if (tab == 0) Channel.LEFT else Channel.RIGHT
            ChannelPanel(
                channel = ch,
                settings = if (tab == 0) left else right,
                userPresets = if (tab == 0) userL else userR,
                onChange = { t -> vm.update(ch, t) },
                onApplyPreset = { p -> vm.applyPreset(ch, p) },
                onSavePreset = { n -> vm.savePreset(ch, n) },
                onDeletePreset = { n -> vm.deletePreset(ch, n) },
                onCopyToOther = { vm.copyToOther(ch) },
            )
        }
    }
}

// ---------------------------------------------------------------- Pemutar

@Composable
private fun PlayerCard(
    player: PlayerState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(player.fileName ?: "Belum ada file audio", style = MaterialTheme.typography.titleSmall)
            player.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = player.durationMs.coerceAtLeast(1)
            val shown = dragging ?: (player.positionMs.toFloat() / dur)
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek((it * dur).toLong()) }
                    dragging = null
                },
                enabled = player.durationMs > 0,
            )
            Text(
                "${fmtTime(player.positionMs)} / ${fmtTime(player.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Buka") }
                OutlinedButton(onClick = onToggle, enabled = player.fileName != null) {
                    Text(if (player.isPlaying) "Jeda" else "Putar")
                }
                OutlinedButton(onClick = onStop, enabled = player.fileName != null) { Text("Stop") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("L", Modifier.padding(end = 4.dp))
                LinearProgressIndicator(progress = { player.peakL.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("R", Modifier.padding(end = 4.dp))
                LinearProgressIndicator(progress = { player.peakR.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- Panel kanal

@Composable
private fun ChannelPanel(
    channel: Channel,
    settings: ChannelSettings,
    userPresets: List<Preset>,
    onChange: ((ChannelSettings) -> ChannelSettings) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onCopyToOther: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PresetBar(channel, userPresets, onApplyPreset, onSavePreset, onDeletePreset, onCopyToOther)

        ResponseGraph(settings)

        SectionCard("Gain & polaritas") {
            ParamSlider(
                label = "Gain",
                valueText = "%+.1f dB".format(settings.gainDb),
                value = settings.gainDb,
                range = -24f..12f,
                onChange = { v -> onChange { it.copy(gainDb = snap(v, 0.5f)) } },
            )
            SwitchRow("Polaritas dibalik (180°)", settings.polarityInverted) { on ->
                onChange { it.copy(polarityInverted = on) }
            }
        }

        SectionCard("HPF (high-pass)") {
            PassFilterEditor(settings.hpf, 10f, 2000f) { f -> onChange { it.copy(hpf = f(it.hpf)) } }
        }

        SectionCard("LPF (low-pass)") {
            PassFilterEditor(settings.lpf, 200f, 20000f) { f -> onChange { it.copy(lpf = f(it.lpf)) } }
        }

        SectionCard("EQ parametrik 10 band") {
            EqEditor(settings, onChange)
        }
    }
}

@Composable
private fun PassFilterEditor(
    pf: PassFilter,
    minFreq: Float,
    maxFreq: Float,
    onChange: ((PassFilter) -> PassFilter) -> Unit,
) {
    SwitchRow("Aktif", pf.enabled) { on -> onChange { it.copy(enabled = on) } }
    ParamLogSlider(
        label = "Frekuensi",
        valueText = fmtFreq(pf.freq),
        value = pf.freq.coerceIn(minFreq, maxFreq),
        min = minFreq,
        max = maxFreq,
        onChange = { v -> onChange { it.copy(freq = v) } },
    )
    Text("Slope", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Slope.entries.forEach { s ->
            FilterChip(
                selected = pf.slope == s,
                onClick = { onChange { it.copy(slope = s) } },
                label = { Text("${s.db} dB/okt") },
            )
        }
    }
}

@Composable
private fun EqEditor(settings: ChannelSettings, onChange: ((ChannelSettings) -> ChannelSettings) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    SwitchRow("EQ aktif", settings.eqEnabled) { on -> onChange { it.copy(eqEnabled = on) } }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        settings.bands.forEachIndexed { i, b ->
            FilterChip(
                selected = selected == i,
                onClick = { selected = i },
                label = { Text("${i + 1} · ${fmtFreqShort(b.freq)}") },
            )
        }
    }

    val band = settings.bands[selected]
    val update = { f: (EqBand) -> EqBand -> onChange { s -> s.withBand(selected) { f(it) } } }

    SwitchRow("Band ${selected + 1} aktif", band.enabled) { on -> update { it.copy(enabled = on) } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BandType.entries.forEach { t ->
            FilterChip(
                selected = band.type == t,
                onClick = { update { it.copy(type = t) } },
                label = { Text(t.label) },
            )
        }
    }
    ParamLogSlider("Frekuensi", fmtFreq(band.freq), band.freq, 20f, 20000f) { v -> update { it.copy(freq = v) } }
    ParamSlider(
        label = "Gain",
        valueText = "%+.1f dB".format(band.gainDb),
        value = band.gainDb,
        range = -18f..18f,
        onChange = { v -> update { it.copy(gainDb = snap(v, 0.5f)) } },
    )
    ParamLogSlider("Q", "%.2f".format(band.q), band.q, 0.2f, 10f) { v -> update { it.copy(q = v) } }
}

// ---------------------------------------------------------------- Preset

@Composable
private fun PresetBar(
    channel: Channel,
    userPresets: List<Preset>,
    onApply: (Preset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { menu = true }) { Text("Preset ${channel.label}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                BuiltInPresets.all.forEach { p ->
                    DropdownMenuItem(text = { Text(p.name) }, onClick = { menu = false; onApply(p) })
                }
                if (userPresets.isNotEmpty()) {
                    HorizontalDivider()
                    userPresets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { menu = false; onApply(p) },
                            trailingIcon = {
                                TextButton(onClick = { onDelete(p.name) }) { Text("Hapus") }
                            },
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = { name = ""; dialog = true }) { Text("Simpan") }
        OutlinedButton(onClick = onCopy) { Text("Salin ke ${channel.other.label}") }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Simpan preset ${channel.label}") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama preset") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onSave(name); dialog = false }, enabled = name.isNotBlank()) {
                    Text("Simpan")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Batal") } },
        )
    }
}

// ---------------------------------------------------------------- Grafik respons

@Composable
private fun ResponseGraph(settings: ChannelSettings) {
    val freqs = remember { DoubleArray(240) { 20.0 * 1000.0.pow(it / 239.0) } }
    val db = remember(settings) { FilterChain.responseCurve(settings, freqs, 48000.0) }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val zeroColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val range = 18.0

    Card(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(8.dp),
        ) {
            fun x(f: Double) = (ln(f / 20.0) / ln(1000.0)).toFloat() * size.width
            fun y(d: Double) = size.height * (0.5f - (d.coerceIn(-range, range) / (2 * range)).toFloat())

            for (f in listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)) {
                drawLine(gridColor, Offset(x(f), 0f), Offset(x(f), size.height))
            }
            for (d in listOf(-12.0, -6.0, 6.0, 12.0)) {
                drawLine(gridColor, Offset(0f, y(d)), Offset(size.width, y(d)))
            }
            drawLine(zeroColor, Offset(0f, y(0.0)), Offset(size.width, y(0.0)))

            val path = Path()
            freqs.indices.forEach { i ->
                val px = x(freqs[i])
                val py = y(db[i])
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lineColor, style = Stroke(width = 3f))
        }
    }
}

// ---------------------------------------------------------------- Komponen umum

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ParamSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ParamLogSlider(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val pos = (ln(value / min) / ln(max / min)).coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = pos,
            onValueChange = { onChange((min * (max / min).pow(it)).coerceIn(min, max)) },
        )
    }
}

// ---------------------------------------------------------------- Format

private fun snap(v: Float, step: Float) = (v / step).roundToInt() * step

private fun fmtFreq(f: Float): String =
    if (f >= 1000f) "%.2f kHz".format(f / 1000f) else "%.0f Hz".format(f)

private fun fmtFreqShort(f: Float): String =
    if (f >= 1000f) "%.1fk".format(f / 1000f).replace(".0k", "k") else "%.0f".format(f)

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
