package ai.pairo.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Pengenal suara Android, disambung ulang otomatis.
 * WebView tidak punya Web Speech API, jadi bagian ini wajib native.
 */
class Speech(private val ctx: Context) {

    interface Sink {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: String)
        fun onStopped()
    }

    var sink: Sink? = null
    var lang: String = "id-ID"

    private var rec: SpeechRecognizer? = null
    private var running = false
    private var paused = false
    private val handler = Handler(Looper.getMainLooper())
    private val restartTask = Runnable { begin() }

    fun start() {
        if (running) return
        running = true
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(ctx)
            rec?.setRecognitionListener(listener)
        }
        begin()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(restartTask)
        try { rec?.cancel() } catch (_: Exception) {}
        sink?.onStopped()
    }

    /** Berhenti mendengar sementara tanpa menutup sesi atau notifikasi. */
    fun pause() {
        if (!running) return
        paused = true
        running = false
        handler.removeCallbacks(restartTask)
        try { rec?.cancel() } catch (_: Exception) {}
    }

    /** Menyambung lagi setelah jeda (dipakai seusai saya selesai bicara). */
    fun resume() {
        paused = false
        if (running) return
        running = true
        begin()
    }

    fun destroy() {
        running = false
        handler.removeCallbacks(restartTask)
        try { rec?.destroy() } catch (_: Exception) {}
        rec = null
    }

    private fun begin() {
        if (!running) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        try {
            rec?.startListening(intent)
        } catch (e: Exception) {
            sink?.onError("start:" + (e.message ?: "gagal"))
            scheduleRestart(900)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!running) return
        handler.removeCallbacks(restartTask)
        handler.postDelayed(restartTask, delayMs)
    }

    private fun best(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { sink?.onReady() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val code = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                SpeechRecognizer.ERROR_CLIENT -> "aborted"
                else -> "error-$error"
            }
            sink?.onError(code)
            if (code == "not-allowed") { running = false; sink?.onStopped(); return }
            scheduleRestart(if (code == "busy") 900L else 260L)
        }

        override fun onResults(results: Bundle?) {
            best(results)?.let { sink?.onFinal(it) }
            scheduleRestart(220L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let { sink?.onPartial(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
