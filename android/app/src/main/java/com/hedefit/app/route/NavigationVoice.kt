package com.hedefit.app.route

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface NavigationVoice : AutoCloseable {
    fun speak(instruction: String)
    override fun close()
}

class AndroidTextToSpeechNavigationVoice(context: Context, locale: Locale = Locale.getDefault()) : NavigationVoice {
    private var ready = false
    private var lastSpoken = ""
    private var engine: TextToSpeech? = null
    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) engine?.language = locale
        }
    }

    override fun speak(instruction: String) {
        val clean = instruction.trim()
        if (!ready || clean.isBlank() || clean == lastSpoken) return
        lastSpoken = clean
        engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "hedefit-navigation")
    }

    override fun close() {
        engine?.stop()
        engine?.shutdown()
    }
}
