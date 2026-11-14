package com.roadpulse.app.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spoken guidance through the device's text-to-speech engine.
 *
 * While a prompt plays it holds "transient, may duck" audio focus, so music and podcasts lower
 * their volume instead of stopping, and come back as soon as the queue of prompts is finished.
 * Nothing is spoken while [VoiceSettings.muted] is on.
 */
class VoiceGuide(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { /* prompts are short: nothing to pause or resume */ }
        .build()

    /** Prompts queued or playing; focus is held while this is above zero. */
    private val inFlight = AtomicInteger(0)
    private val ids = AtomicInteger(0)

    @Volatile private var ready = false
    private val pending = ArrayList<String>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onReady()
    }

    private fun onReady() {
        applyLanguage(VoiceSettings.effectiveLanguage.value)
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
            override fun onError(utteranceId: String?, errorCode: Int) = finished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
        })
        ready = true
        val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
        queued.forEach(::speak)
    }

    fun speak(text: String) {
        if (VoiceSettings.muted.value) return
        if (!ready) {
            synchronized(pending) { pending.add(text) }
            return
        }
        if (inFlight.getAndIncrement() == 0) audioManager.requestAudioFocus(focusRequest)
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "rp-${ids.incrementAndGet()}")
        if (result != TextToSpeech.SUCCESS) finished()
    }

    /** Switches the voice; falls back to English (and reports it) if the voice is not installed. */
    fun applyLanguage(language: GuidanceLanguage) {
        if (!ready && language != VoiceSettings.effectiveLanguage.value) return
        val result = runCatching { tts.setLanguage(language.locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val available = result >= TextToSpeech.LANG_AVAILABLE
        if (language != GuidanceLanguage.EN) VoiceSettings.reportVoice(language, available)
        if (!available) runCatching { tts.setLanguage(Locale.US) }
    }

    /** Stops the current prompt and drops queued ones (used when the user mutes). */
    fun stopSpeaking() {
        synchronized(pending) { pending.clear() }
        if (ready) tts.stop()
        releaseFocus()
    }

    fun shutdown() {
        stopSpeaking()
        tts.shutdown()
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) releaseFocus()
    }

    private fun releaseFocus() {
        inFlight.set(0)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
