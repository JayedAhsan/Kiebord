package io.kiebord.feature.ime.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * State of the voice typing recognition lifecycle.
 */
sealed class VoiceInputState {
    data object Idle : VoiceInputState()
    data class Listening(val statusText: String = "Speak now", val rmsDb: Float = 0f) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

/**
 * Manages Google Speech Recognizer lifecycle, continuous listening, locale mapping,
 * and input connection text streaming.
 */
class VoiceInputManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onSpeechDismissed: (() -> Unit)? = null

    private var isListeningActive = false
    private var currentLanguage = "en"
    private var currentIntent: Intent? = null

    init {
        activeManagerInstance = this
    }

    val isListening: Boolean
        get() = isListeningActive

    /**
     * Resolves the BCP-47 locale tag matching the current keyboard layout.
     */
    private fun getLocaleTagForLanguage(language: String): String {
        return when (language.lowercase(Locale.ROOT)) {
            "bn" -> "bn-BD"
            "en" -> "en-US"
            "tr" -> "tr-TR"
            "hi" -> "hi-IN"
            "ar" -> "ar-SA"
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            else -> {
                try {
                    Locale.forLanguageTag(language).toLanguageTag().ifBlank {
                        Locale.getDefault().toLanguageTag()
                    }
                } catch (_: Exception) {
                    Locale.getDefault().toLanguageTag()
                }
            }
        }
    }

    private fun isGoogleServiceAvailable(): Boolean {
        return try {
            val component = ComponentName(
                GOOGLE_VOICE_PACKAGE,
                GOOGLE_VOICE_SERVICE
            )
            val intent = Intent("android.speech.RecognitionService").setComponent(component)
            val services = context.packageManager.queryIntentServices(intent, 0)
            services.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun createSpeechRecognizer(): SpeechRecognizer {
        return try {
            if (isGoogleServiceAvailable()) {
                val googleComponent = ComponentName(
                    GOOGLE_VOICE_PACKAGE,
                    GOOGLE_VOICE_SERVICE
                )
                SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (_: Exception) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    fun start(layoutLanguage: String) {
        currentLanguage = layoutLanguage

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            isPendingStart = true
            pendingLanguage = layoutLanguage
            val intent = Intent(context, VoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceInputState.Error("Speech recognition not available")
            mainHandler.postDelayed({
                if (_state.value is VoiceInputState.Error) {
                    _state.value = VoiceInputState.Idle
                }
            }, 2500)
            return
        }

        isListeningActive = true
        _state.value = VoiceInputState.Listening("Speak now")

        val localeTag = getLocaleTagForLanguage(layoutLanguage)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        currentIntent = intent

        initAndStart(intent)
    }

    private fun initAndStart(intent: Intent) {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = createSpeechRecognizer().apply {
                setRecognitionListener(createListener())
                startListening(intent)
            }
        } catch (e: Exception) {
            _state.value = VoiceInputState.Error(e.message ?: "Recognition failed")
            stop()
        }
    }

    private fun restartListening(recreate: Boolean = false) {
        if (!isListeningActive) return
        val intent = currentIntent ?: return
        mainHandler.post {
            if (!isListeningActive) return@post
            try {
                if (recreate || speechRecognizer == null) {
                    initAndStart(intent)
                } else {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(intent)
                }
                _state.value = VoiceInputState.Listening("Speak now")
            } catch (_: Exception) {
                initAndStart(intent)
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isListeningActive) {
                    _state.value = VoiceInputState.Listening("Speak now")
                }
            }

            override fun onBeginningOfSpeech() {
                if (isListeningActive) {
                    _state.value = VoiceInputState.Listening("Listening...")
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isListeningActive && _state.value is VoiceInputState.Listening) {
                    val currentText = (_state.value as VoiceInputState.Listening).statusText
                    _state.value = VoiceInputState.Listening(currentText, rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (isListeningActive) {
                    _state.value = VoiceInputState.Listening("Transcribing...")
                }
            }

            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // User pause / silence in continuous listening: finalize any composing partial and restart smoothly
                        onSpeechDismissed?.invoke()
                        if (isListeningActive) {
                            restartListening()
                        }
                    }
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        if (isListeningActive) {
                            mainHandler.postDelayed({
                                if (isListeningActive) restartListening(recreate = true)
                            }, 150)
                        }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        stop()
                        _state.value = VoiceInputState.Error("Microphone permission required")
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        if (isListeningActive) {
                            _state.value = VoiceInputState.Listening("Reconnecting...")
                            mainHandler.postDelayed({
                                if (isListeningActive) restartListening()
                            }, 1200)
                        }
                    }
                    else -> {
                        if (isListeningActive) {
                            mainHandler.postDelayed({
                                if (isListeningActive) restartListening()
                            }, 400)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    onFinalResult?.invoke(text)
                } else {
                    onSpeechDismissed?.invoke()
                }
                // Continuous listening: keep listening for next phrase as long as voice typing is active
                if (isListeningActive) {
                    restartListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank() && isListeningActive) {
                    _state.value = VoiceInputState.Listening("Listening...")
                    onPartialResult?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun stop() {
        if (!isListeningActive && _state.value is VoiceInputState.Idle) return
        isListeningActive = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
            _state.value = VoiceInputState.Idle
            onSpeechDismissed?.invoke()
        }
    }

    fun destroy() {
        stop()
        if (activeManagerInstance === this) {
            activeManagerInstance = null
        }
    }

    companion object {
        private const val GOOGLE_VOICE_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_VOICE_SERVICE = "com.google.android.voicesearch.service.RecognitionService"

        @Volatile
        private var activeManagerInstance: VoiceInputManager? = null

        private var isPendingStart = false
        private var pendingLanguage = "en"

        fun onPermissionResult(granted: Boolean) {
            if (granted && isPendingStart) {
                isPendingStart = false
                activeManagerInstance?.start(pendingLanguage)
            } else {
                isPendingStart = false
            }
        }
    }
}
