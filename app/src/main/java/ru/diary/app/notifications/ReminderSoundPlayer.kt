package ru.diary.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.min
import ru.diary.app.data.settings.ReminderSounds
import ru.diary.app.data.settings.SettingsManager

/**
 * Проигрывает сильный звук напоминания напрямую через MediaPlayer.
 *
 * В отличие от «звука уведомлений» канала (короткий чин, полагается на громкость
 * уведомлений и может быть заглушен полноэкранным окном), этот звук:
 *  - играет на стриме БУДИЛЬНИКА (STREAM_ALARM/USAGE_ALARM) — громко и отдельно;
 *  - звучит не дольше [MAX_PLAY_MS]: перед окончанием громкость плавно спадает
 *    ([FADE_MS]), поэтому мелодия не обрывается резко посреди цикла;
 *  - останавливается по таймеру или кнопкам, но уведомление при этом не трогает;
 *  - держит CPU (PARTIAL_WAKE_LOCK), чтобы не уснул в Doze.
 *
 * Два режима:
 *  - [start] — фоновое напоминание (экран выключен/заблокирован): зацикливает
 *    мелодию с самого начала.
 *  - [startFromOffset] — напоминание при работающем телефоне: зацикливает мелодию
 *    от участка, который уже отыграл штатный звук канала, чтобы не повторять его
 *    и уложиться в тот же трёхминутный лимит.
 */
object ReminderSoundPlayer {

    /** Максимальная длительность звучания — 3 минуты. */
    const val MAX_PLAY_MS = 3 * 60 * 1000L

    /** Длительность плавного затухания в конце, чтобы цикл не обрывался резко. */
    const val FADE_MS = 3_000L

    /** Шаг регулировки громкости во время затухания. */
    private const val FADE_STEP_MS = 100L

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var playingForId: Int = -1

    /** Момент (elapsedRealtime), к которому звук должен полностью затихнуть. */
    private var fadeEndsAtMs = 0L

    /** Плавно снижает громкость до нуля и останавливает воспроизведение. */
    private val fadeTick = object : Runnable {
        override fun run() {
            val current = player ?: return
            val remaining = fadeEndsAtMs - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                stopInternal()
                return
            }
            val volume = (remaining / FADE_MS.toFloat()).coerceIn(0f, 1f)
            try {
                current.setVolume(volume, volume)
            } catch (_: Exception) {
                stopInternal()
                return
            }
            handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    /** Начинает громкий звук напоминания для конкретной задачи (с начала мелодии). */
    fun start(context: Context, taskId: Int) = startInternal(context, taskId, startOffsetMs = 0L)

    /**
     * Как [start], но воспроизведение начинается с участка [startOffsetMs] —
     * используется, когда штатный звук канала уже отыграл этот отрезок.
     */
    fun startFromOffset(context: Context, taskId: Int, startOffsetMs: Long) =
        startInternal(context, taskId, startOffsetMs)

    /** Выбран ли в настройках режим «без звука». */
    fun isSilent(context: Context): Boolean =
        ReminderSounds.kindOf(SettingsManager(context).currentReminderSound()) ==
            ReminderSounds.Kind.SILENT

    /** Мелодия из настроек; null — звук отключён или на устройстве нет будильника. */
    private fun soundUri(context: Context): Uri? {
        val value = SettingsManager(context).currentReminderSound()
        return when (ReminderSounds.kindOf(value)) {
            ReminderSounds.Kind.SILENT -> null
            ReminderSounds.Kind.DEFAULT -> defaultAlarmUri()
            ReminderSounds.Kind.CUSTOM ->
                runCatching { Uri.parse(value) }.getOrNull()
                    ?.takeIf { it.scheme != null }
                    ?: defaultAlarmUri()
        }
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    private fun startInternal(context: Context, taskId: Int, startOffsetMs: Long) {
        if (playingForId == taskId && player?.isPlaying == true) return
        stopInternal()

        val uri: Uri = soundUri(context) ?: return
        val media = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // Wake lock во время воспроизведения, чтобы звук не прервался в Doze.
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    // Стартуем с «догоняющего» участка, чтобы не повторять звук,
                    // который уже отыграл канал, и уложиться в общий лимит.
                    if (startOffsetMs > 0L) {
                        val durationMs = mp.duration.toLong()
                        if (durationMs > 0L) {
                            mp.seekTo(min(startOffsetMs, durationMs).toInt())
                        }
                    }
                    mp.start()
                }
                setOnCompletionListener { stopInternal() }
                setOnErrorListener { listener, _, _ ->
                    stopInternal()
                    true
                }
            }
        } catch (_: Exception) {
            return // нет звука на устройстве — ничего не делаем
        }

        try {
            media.prepareAsync()
        } catch (_: Exception) {
            return
        }
        player = media
        playingForId = taskId

        // Начинаем затухание за FADE_MS до конца лимита — ровно к MAX_PLAY_MS
        // звук полностью стихает, мелодия «доигрывает» плавно, а не обрывается.
        // Зацикливаем вручную: после каждого проигрывания снова seekTo(offset),
        // чтобы в конце участок отыграл ещё раз и уже был заглушён затуханием.
        media.isLooping = false
        media.setOnCompletionListener { mp ->
            if (startOffsetMs > 0L && SystemClock.elapsedRealtime() < fadeEndsAtMs) {
                try {
                    mp.seekTo(0)
                    mp.start()
                } catch (_: Exception) {
                    stopInternal()
                }
            } else {
                stopInternal()
            }
        }

        fadeEndsAtMs = SystemClock.elapsedRealtime() + MAX_PLAY_MS
        handler.removeCallbacks(fadeTick)
        handler.postDelayed(fadeTick, MAX_PLAY_MS - FADE_MS)
    }

    /** Останавливает звук, если он играет именно для этой задачи. */
    fun stop(taskId: Int) {
        if (playingForId != taskId) return
        stopInternal()
    }

    private fun stopInternal() {
        handler.removeCallbacks(fadeTick)
        val current = player
        player = null
        playingForId = -1
        try {
            current?.stop()
            current?.release()
        } catch (_: Exception) {
            // уже освобождён или не был инициализирован
        }
    }
}