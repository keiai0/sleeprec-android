package io.github.keiai0.sleeprec

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Service と Activity が同じプロセス内で共有する「録音中か」の状態。
// Go でいう「パッケージレベルの変数 + 変更通知チャネル」のようなもの。
// StateFlow は「最新値を常に持ち、変わったら購読者に通知する」入れ物。
object RecordingState {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun set(recording: Boolean) {
        _isRecording.value = recording
    }

    // 閾値調整用のデバッグ表示(Phase 3)。録音スレッドが約0.1秒ごとに更新する
    private val _currentDb = MutableStateFlow(Loudness.FLOOR_DB)
    val currentDb: StateFlow<Float> = _currentDb

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount

    fun setCurrentDb(db: Float) {
        _currentDb.value = db
    }

    fun setEventCount(count: Int) {
        _eventCount.value = count
    }
}
