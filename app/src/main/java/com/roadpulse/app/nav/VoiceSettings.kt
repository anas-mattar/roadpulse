package com.roadpulse.app.nav

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Voice guidance preferences, kept across app restarts. */
object VoiceSettings {
    private var prefs: SharedPreferences? = null

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** The language the user chose. */
    private val _language = MutableStateFlow(GuidanceLanguage.EN)
    val language: StateFlow<GuidanceLanguage> = _language.asStateFlow()

    /** Languages with no text-to-speech voice installed on this device. */
    private val _missingVoices = MutableStateFlow<Set<GuidanceLanguage>>(emptySet())
    val missingVoices: StateFlow<Set<GuidanceLanguage>> = _missingVoices.asStateFlow()

    /** The language actually used: the chosen one, or English if its voice is not installed. */
    private val _effective = MutableStateFlow(GuidanceLanguage.EN)
    val effectiveLanguage: StateFlow<GuidanceLanguage> = _effective.asStateFlow()

    fun narrator(): Narrator = narratorFor(_effective.value)

    /** Safe to call repeatedly (activity and service both call it). */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _muted.value = p.getBoolean(KEY_MUTED, false)
        _language.value = GuidanceLanguage.fromCode(p.getString(KEY_LANGUAGE, null))
            ?: when (Locale.getDefault().language) {
                "ms" -> GuidanceLanguage.MS
                "ar" -> GuidanceLanguage.AR
                else -> GuidanceLanguage.EN
            }
        recompute()
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        prefs?.edit()?.putBoolean(KEY_MUTED, muted)?.apply()
    }

    fun setLanguage(language: GuidanceLanguage) {
        _language.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
        recompute()
    }

    /** Reported by the text-to-speech engine after checking whether a voice exists. */
    fun reportVoice(language: GuidanceLanguage, available: Boolean) {
        _missingVoices.value = if (available) _missingVoices.value - language else _missingVoices.value + language
        recompute()
    }

    private fun recompute() {
        val chosen = _language.value
        _effective.value = if (chosen in _missingVoices.value) GuidanceLanguage.EN else chosen
    }

    private const val KEY_MUTED = "voice_muted"
    private const val KEY_LANGUAGE = "voice_language"
}

/** Checks once whether the device has a text-to-speech voice for [language]. */
object VoiceProbe {
    fun check(context: Context, language: GuidanceLanguage) {
        if (language == GuidanceLanguage.EN) {
            VoiceSettings.reportVoice(language, true)
            return
        }
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            val ok = status == TextToSpeech.SUCCESS && engine != null &&
                engine.isLanguageAvailable(language.locale) >= TextToSpeech.LANG_AVAILABLE
            VoiceSettings.reportVoice(language, ok)
            engine?.shutdown()
        }
    }
}
