package com.youngs.picview.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale

/**
 * 장소 개요를 읽어 주는 오디오 가이드.
 *
 * 스펙 §15 대체 C. 글씨가 작아 읽기 힘든 사용자를 위한 기능이라
 * 시니어 모드에서는 속도를 늦추고([SENIOR_RATE]) 문장 수를 줄입니다.
 *
 * 문장 단위로 끊어 읽는 이유는 두 가지입니다.
 *  - 지금 읽는 문장을 화면에서 강조할 수 있음
 *  - 중간에 멈췄다 다시 시작할 때 문장 처음부터 이어짐
 *
 * Lifecycle 에 붙여 두면 화면이 사라질 때 자동으로 정리됩니다.
 * (TTS 엔진을 shutdown 하지 않으면 프로세스에 남아 계속 읽습니다)
 */
class TtsController(
    context: Context,
    private val seniorMode: Boolean = false
) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    private var sentences: List<String> = emptyList()
    private var pendingText: String? = null

    private val _speaking = MutableLiveData(false)
    val speaking: LiveData<Boolean> = _speaking

    /** 지금 읽고 있는 문장 번호. 없으면 -1. 화면 하이라이트에 씁니다. */
    private val _currentSentence = MutableLiveData(-1)
    val currentSentence: LiveData<Int> = _currentSentence

    /** 엔진이 준비됐는지. 실패하면 버튼을 비활성화합니다. */
    private val _available = MutableLiveData(false)
    val available: LiveData<Boolean> = _available

    var rate: Float = if (seniorMode) SENIOR_RATE else NORMAL_RATE
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                val unsupported = result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                if (unsupported) {
                    Log.w(TAG, "한국어 음성 데이터 없음")
                    _available.postValue(false)
                    return@TextToSpeech
                }
                tts?.setSpeechRate(rate)
                tts?.setOnUtteranceProgressListener(progressListener)
                ready = true
                _available.postValue(true)

                // 준비 전에 재생 요청이 들어왔으면 여기서 처리합니다.
                pendingText?.let { text ->
                    pendingText = null
                    speak(text)
                }
            } else {
                Log.w(TAG, "TTS 초기화 실패: $status")
                _available.postValue(false)
            }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.toIntOrNull()?.let { _currentSentence.postValue(it) }
            _speaking.postValue(true)
        }

        override fun onDone(utteranceId: String?) {
            val index = utteranceId?.toIntOrNull() ?: return
            if (index >= sentences.lastIndex) {
                _speaking.postValue(false)
                _currentSentence.postValue(-1)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _speaking.postValue(false)
            _currentSentence.postValue(-1)
        }
    }

    /**
     * 읽기 시작. 이미 읽고 있으면 멈춥니다(토글).
     *
     * @return 실제로 재생을 시작했으면 true
     */
    fun toggle(text: String): Boolean {
        if (_speaking.value == true) {
            stop()
            return false
        }
        speak(text)
        return true
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        if (!ready) {
            // 엔진이 아직 준비 안 됐으면 기억해 뒀다가 준비되면 재생합니다.
            pendingText = clean
            return
        }

        sentences = splitSentences(clean)
        if (sentences.isEmpty()) return

        tts?.stop()
        sentences.forEachIndexed { index, sentence ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(sentence, mode, Bundle(), index.toString())
        }
        _speaking.value = true
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
        _currentSentence.value = -1
    }

    /** 화면에 보여 줄 문장 목록. 하이라이트 인덱스와 짝이 맞습니다. */
    fun sentencesOf(text: String): List<String> = splitSentences(text)

    /**
     * 문장 분리.
     *
     * 시니어 모드에서는 앞의 [SENIOR_SENTENCE_LIMIT] 문장만 읽습니다.
     * 관광공사 overview 는 길면 열 문장이 넘어가는데, 끝까지 듣는 사람이 없고
     * 오히려 "언제 끝나지" 하는 부담을 줍니다.
     */
    private fun splitSentences(text: String): List<String> {
        val all = text
            .replace(Regex("<[^>]*>"), " ")   // overview 에 <br> 등이 섞여 옵니다
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?。])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val limit = if (seniorMode) SENIOR_SENTENCE_LIMIT else NORMAL_SENTENCE_LIMIT
        return all.take(limit)
    }

    override fun onStop(owner: LifecycleOwner) {
        stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "TTS"

        const val NORMAL_RATE = 1.0f

        /** 시니어는 조금 천천히 (스펙 §17-6). */
        const val SENIOR_RATE = 0.85f

        const val SLOW_RATE = 0.8f
        const val FAST_RATE = 1.2f

        private const val NORMAL_SENTENCE_LIMIT = 8
        private const val SENIOR_SENTENCE_LIMIT = 2
    }
}
