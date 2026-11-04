package ru.diary.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Общие константы резервного копирования. */
object BackupConstants {
    const val DB_NAME = "diary.db"
    const val DB_ENTRY = "diary.db"
    const val SETTINGS_ENTRY = "diary_settings.xml"
    const val MANIFEST_ENTRY = "manifest.json"
    const val PREFIX = "DiaryBackup_"
    const val SUFFIX = ".diarybak"
    const val SETTINGS_PREFS = "diary_settings"

    /** Версия формата архива. Растёт при несовместимых изменениях структуры. */
    const val FORMAT_VERSION = 2

    /** Читаемые форматы (для обратной совместимости со старыми копиями без манифеста). */
    val SUPPORTED_READ_FORMATS = setOf(1, 2)
}

/**
 * Имя файла резервной копии. Содержит дату и время, чтобы несколько копий
 * за один день не перезаписывали друг друга.
 */
object BackupNaming {
    private const val PATTERN = "yyyy-MM-dd_HH-mm"

    fun backupFileName(date: Date = Date(), locale: Locale = Locale.ROOT): String =
        BackupConstants.PREFIX + SimpleDateFormat(PATTERN, locale).format(date) + BackupConstants.SUFFIX
}

/**
 * Описание содержимого архива. Позволяет узнать версию схемы БД и версию
 * приложения, а также проверить целостность каждой записи (SHA-256).
 */
data class BackupManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val encrypted: Boolean,
    val entries: List<ManifestEntry>
) {
    fun entry(name: String): ManifestEntry? = entries.firstOrNull { it.name == name }
}

data class ManifestEntry(
    val name: String,
    val sha256: String,
    val size: Long
)

/** Запись/чтение манифеста в простом JSON (без внешних зависимостей). */
object ManifestCodec {

    fun encode(manifest: BackupManifest): String {
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"formatVersion\":").append(manifest.formatVersion).append(',')
        sb.append("\"schemaVersion\":").append(manifest.schemaVersion).append(',')
        sb.append("\"appVersion\":\"").append(escape(manifest.appVersion)).append("\",")
        sb.append("\"createdAt\":").append(manifest.createdAt).append(',')
        sb.append("\"encrypted\":").append(manifest.encrypted).append(',')
        sb.append("\"entries\":[")
        manifest.entries.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":\"").append(escape(e.name)).append("\",")
            sb.append("\"sha256\":\"").append(escape(e.sha256)).append("\",")
            sb.append("\"size\":").append(e.size).append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(json: String): BackupManifest? = try {
        val formatVersion = intField(json, "formatVersion")
        val schemaVersion = intField(json, "schemaVersion") ?: 0
        val appVersion = stringField(json, "appVersion") ?: ""
        val createdAt = longField(json, "createdAt") ?: 0L
        val encrypted = boolField(json, "encrypted") ?: false
        val entries = decodeEntries(json)
        BackupManifest(formatVersion ?: 1, schemaVersion, appVersion, createdAt, encrypted, entries)
    } catch (_: Exception) {
        null
    }

    private fun decodeEntries(json: String): List<ManifestEntry> {
        val arrKey = "\"entries\":["
        val start = json.indexOf(arrKey).let { if (it < 0) return emptyList() else it + arrKey.length }
        val result = mutableListOf<ManifestEntry>()
        var i = start
        while (i < json.length) {
            val objStart = json.indexOf('{', i)
            if (objStart < 0) break
            val objEnd = json.indexOf('}', objStart)
            if (objEnd < 0) break
            val obj = json.substring(objStart, objEnd + 1)
            val name = stringField(obj, "name") ?: ""
            val sha = stringField(obj, "sha256") ?: ""
            val size = longField(obj, "size") ?: 0L
            if (name.isNotEmpty()) result.add(ManifestEntry(name, sha, size))
            i = objEnd + 1
            if (json.getOrNull(i) == ']') break
        }
        return result
    }

    private fun intField(json: String, key: String): Int? =
        longField(json, key)?.toInt()

    private fun longField(json: String, key: String): Long? {
        val marker = "\"$key\":"
        val idx = json.indexOf(marker)
        if (idx < 0) return null
        var i = idx + marker.length
        val sb = StringBuilder()
        while (i < json.length && (json[i].isDigit() || json[i] == '-')) {
            sb.append(json[i]); i++
        }
        return sb.toString().toLongOrNull()
    }

    private fun boolField(json: String, key: String): Boolean? {
        val marker = "\"$key\":"
        val idx = json.indexOf(marker)
        if (idx < 0) return null
        val rest = json.substring(idx + marker.length)
        return when {
            rest.startsWith("true") -> true
            rest.startsWith("false") -> false
            else -> null
        }
    }

    private fun stringField(json: String, key: String): String? {
        val marker = "\"$key\":\""
        val idx = json.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        val sb = StringBuilder()
        var i = start
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1]); i += 2; continue
            }
            if (c == '"') break
            sb.append(c); i++
        }
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

/** SHA-256 — хеширование записей для проверки целостности. */
object Sha256 {
    fun hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF])
            sb.append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
}

/** Результат чтения архива. */
data class BackupReadResult(
    val success: Boolean,
    val manifest: BackupManifest?,
    val error: BackupReadError? = null
)

enum class BackupReadError {
    MALFORMED,
    CHECKSUM_MISMATCH,
    UNSUPPORTED_FORMAT,
    PASSWORD_REQUIRED,
    WRONG_PASSWORD
}

/**
 * Чистая работа с ZIP-архивом бэкапа (без Android): запись БД и настроек,
 * чтение с выдачей приёмников под известные записи.
 *
 * Формат:
 *  - ZIP с записью `manifest.json` (метаданные + контрольные суммы) и
 *    файлами `diary.db` / `diary_settings.xml`;
 *  - опционально весь ZIP может быть обёрнут в шифрованный контейнер [EncryptedContainer] (R4C).
 */
object BackupArchive {

    /** Записывает ZIP с базой и (опционально) настройками. Возвращает `true` при успехе. */
    fun write(
        output: OutputStream,
        database: InputStream,
        settings: InputStream?,
        schemaVersion: Int = 0,
        appVersion: String = "",
        createdAt: Long = System.currentTimeMillis()
    ): Boolean = try {
        val dbBytes = database.readBytes()
        val settingsBytes = settings?.readBytes()
        val entries = mutableListOf<ManifestEntry>()
        entries.add(ManifestEntry(BackupConstants.DB_ENTRY, Sha256.hex(dbBytes), dbBytes.size.toLong()))
        if (settingsBytes != null) {
            entries.add(
                ManifestEntry(BackupConstants.SETTINGS_ENTRY, Sha256.hex(settingsBytes), settingsBytes.size.toLong())
            )
        }
        val manifest = BackupManifest(
            formatVersion = BackupConstants.FORMAT_VERSION,
            schemaVersion = schemaVersion,
            appVersion = appVersion,
            createdAt = createdAt,
            encrypted = false,
            entries = entries
        )

        ZipOutputStream(output).use { zos ->
            zos.putNextEntry(ZipEntry(BackupConstants.MANIFEST_ENTRY))
            zos.write(ManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(BackupConstants.DB_ENTRY))
            zos.write(dbBytes)
            zos.closeEntry()

            if (settingsBytes != null) {
                zos.putNextEntry(ZipEntry(BackupConstants.SETTINGS_ENTRY))
                zos.write(settingsBytes)
                zos.closeEntry()
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Читает ZIP и для каждой известной записи просит приёмник через [sink].
     * Если [sink] вернул `null` — запись пропускается. Проверяет SHA-256 из манифеста.
     */
    fun read(input: InputStream, sink: (entryName: String) -> OutputStream?): BackupReadResult {
        val raw = try {
            input.readBytes()
        } catch (_: Exception) {
            return BackupReadResult(false, null, BackupReadError.MALFORMED)
        }
        return readBytes(raw, sink)
    }

    fun readBytes(raw: ByteArray, sink: (entryName: String) -> OutputStream?): BackupReadResult {
        val entriesData = mutableMapOf<String, ByteArray>()
        var manifestJson: String? = null

        try {
            ZipInputStream(ByteArrayInputStream(raw)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == BackupConstants.MANIFEST_ENTRY) {
                        manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                    } else {
                        entriesData[name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            return BackupReadResult(false, null, BackupReadError.MALFORMED)
        }

        val manifest = manifestJson?.let { ManifestCodec.decode(it) }

        if (manifest != null && manifest.formatVersion !in BackupConstants.SUPPORTED_READ_FORMATS) {
            return BackupReadResult(false, manifest, BackupReadError.UNSUPPORTED_FORMAT)
        }

        // Проверка целостности по контрольным суммам манифеста.
        if (manifest != null) {
            for (entry in manifest.entries) {
                val data = entriesData[entry.name] ?: continue
                if (entry.sha256.isNotEmpty() && Sha256.hex(data) != entry.sha256) {
                    return BackupReadResult(false, manifest, BackupReadError.CHECKSUM_MISMATCH)
                }
            }
        }

        var extractedAny = false
        for ((name, data) in entriesData) {
            sink(name)?.use { target ->
                target.write(data)
                extractedAny = true
            }
        }
        return BackupReadResult(extractedAny, manifest, if (extractedAny) null else BackupReadError.MALFORMED)
    }
}

/**
 * Шифрованный контейнер резервной копии (формат R4C).
 *
 * Структура: `R4C1` (4 байта) | saltLen(2) | salt | ivLen(2) | iv | ciphertext(AES-256-GCM).
 * Ключ выводится из пароля через PBKDF2-HMAC-SHA256. GCM даёт и шифрование, и аутентификацию.
 */
object EncryptedContainer {

    private val MAGIC = "R4C1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))

        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        writeShort(out, salt.size)
        out.write(salt)
        writeShort(out, iv.size)
        out.write(iv)
        CipherOutputStream(out, cipher).use { it.write(plain) }
        return out.toByteArray()
    }

    /** Расшифровывает контейнер. `null`, если пароль неверный или данные повреждены. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray? {
        if (!isEncrypted(data)) return null
        return try {
            var pos = MAGIC.size
            val saltLen = readShort(data, pos); pos += 2
            val salt = data.copyOfRange(pos, pos + saltLen); pos += saltLen
            val ivLen = readShort(data, pos); pos += 2
            val iv = data.copyOfRange(pos, pos + ivLen); pos += ivLen
            val cipherText = data.copyOfRange(pos, data.size)

            val key = deriveKey(password, salt, ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readShort(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
}

/** Ротация копий: какие файлы удалить, чтобы осталось не больше [max] самых новых. */
object BackupRetention {

    /**
     * @param items пары (имя файла, время изменения в мс).
     * @return имена самых старых копий, подлежащих удалению (может быть пустым).
     */
    fun oldestToDelete(items: List<Pair<String, Long>>, max: Int): List<String> {
        if (max <= 0) return items.sortedBy { it.second }.map { it.first }
        val sortedByAge = items.sortedBy { it.second }
        val excess = sortedByAge.size - max
        return if (excess <= 0) emptyList() else sortedByAge.take(excess).map { it.first }
    }
}

/** Периодичность фонового резервного копирования. */
object BackupScheduler {
    /** 12 часов. */
    const val INTERVAL_MS = 12L * 60 * 60 * 1000

    fun shouldBackup(lastBackupMs: Long, nowMs: Long, intervalMs: Long = INTERVAL_MS): Boolean =
        nowMs - lastBackupMs >= intervalMs
}
