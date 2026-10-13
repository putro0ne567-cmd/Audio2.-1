package com.stereodsp.dsp

import kotlin.math.pow

/**
 * Prosesor satu kanal: polaritas -> gain -> HPF -> LPF -> EQ.
 * [update] aman dipanggil dari thread UI saat [process] berjalan di thread audio.
 */
class ChannelProcessor(private val fs: Double) {
    private val lock = Any()
    private val sections = ArrayList<Biquad>()
    private var gain = 1.0

    fun update(s: ChannelSettings) {
        val coeffs = FilterChain.build(s, fs)
        val linear = 10.0.pow(s.gainDb / 20.0)
        val sign = if (s.polarityInverted) -1.0 else 1.0
        synchronized(lock) {
            while (sections.size < coeffs.size) sections.add(Biquad())
            while (sections.size > coeffs.size) sections.removeAt(sections.lastIndex)
            coeffs.forEachIndexed { i, c -> sections[i].set(c) }
            gain = linear * sign
        }
    }

    fun reset() {
        synchronized(lock) { sections.forEach { it.reset() } }
    }

    /** Memproses [count] sampel pertama dari [buf] di tempat. */
    fun process(buf: FloatArray, count: Int) {
        synchronized(lock) {
            val g = gain
            for (i in 0 until count) {
                var x = buf[i] * g
                for (s in sections) x = s.process(x)
                buf[i] = x.toFloat()
            }
        }
    }
}
