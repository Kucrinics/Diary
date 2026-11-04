package ru.diary.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @Test
    fun `write stores database and settings entries`() {
        val out = ByteArrayOutputStream()

        val ok = BackupArchive.write(
            out,
            ByteArrayInputStream("db-bytes".toByteArray()),
            ByteArrayInputStream("settings-xml".toByteArray())
        )

        assertTrue(ok)
        val entries = readEntries(out.toByteArray())
        assertEquals("db-bytes", entries["diary.db"])
        assertEquals("settings-xml", entries["diary_settings.xml"])
    }

    @Test
    fun `write without settings omits settings entry`() {
        val out = ByteArrayOutputStream()

        assertTrue(BackupArchive.write(out, ByteArrayInputStream("db".toByteArray()), null))

        assertEquals(
            setOf(BackupConstants.MANIFEST_ENTRY, "diary.db"),
            readEntries(out.toByteArray()).keys
        )
    }

    @Test
    fun `read forwards known entries to sink and copies their content`() {
        val archive = zipBytes(
            mapOf(
                "diary.db" to "hello-db",
                "diary_settings.xml" to "xml",
                "extra.txt" to "ignored"
            )
        )
        val captured = mutableMapOf<String, String>()

        val result = BackupArchive.read(ByteArrayInputStream(archive)) { name ->
            if (name == "diary.db" || name == "diary_settings.xml") {
                object : ByteArrayOutputStream() {
                    override fun close() {
                        captured[name] = toString("UTF-8")
                        super.close()
                    }
                }
            } else {
                null
            }
        }

        assertTrue(result.success)
        assertEquals("hello-db", captured["diary.db"])
        assertEquals("xml", captured["diary_settings.xml"])
        assertFalse("неизвестные записи не должны разбираться", captured.containsKey("extra.txt"))
    }

    @Test
    fun `read returns false on malformed archive`() {
        val result = BackupArchive.read(ByteArrayInputStream("not a zip at all".toByteArray())) { null }

        assertFalse(result.success)
    }

    @Test
    fun `write embeds manifest with schema and app version`() {
        val out = ByteArrayOutputStream()

        BackupArchive.write(
            out,
            ByteArrayInputStream("db".toByteArray()),
            null,
            schemaVersion = 11,
            appVersion = "1.7.0",
            createdAt = 123L
        )

        val entries = readEntries(out.toByteArray())
        val manifest = entries[BackupConstants.MANIFEST_ENTRY] ?: error("manifest missing")
        assertTrue(manifest.contains("\"schemaVersion\":11"))
        assertTrue(manifest.contains("\"appVersion\":\"1.7.0\""))
        assertTrue(manifest.contains("\"formatVersion\":2"))
    }

    @Test
    fun `read rejects archive with mismatched checksum`() {
        val manifest = BackupManifest(
            formatVersion = 2,
            schemaVersion = 11,
            appVersion = "1.7.0",
            createdAt = 0L,
            encrypted = false,
            entries = listOf(ManifestEntry("diary.db", Sha256.hex("other".toByteArray()), 5))
        )
        val archive = zipBytes(
            mapOf(
                BackupConstants.MANIFEST_ENTRY to ManifestCodec.encode(manifest),
                "diary.db" to "db-bytes"
            )
        )

        val result = BackupArchive.read(ByteArrayInputStream(archive)) { null }

        assertFalse(result.success)
        assertEquals(BackupReadError.CHECKSUM_MISMATCH, result.error)
    }

    @Test
    fun `encrypted container round-trips and rejects wrong password`() {
        val plain = "secret-diary".toByteArray()

        val encrypted = EncryptedContainer.encrypt(plain, "hunter2".toCharArray())
        assertTrue(EncryptedContainer.isEncrypted(encrypted))
        assertTrue(encrypted.toList() != plain.toList())

        assertArrayEquals(plain, EncryptedContainer.decrypt(encrypted, "hunter2".toCharArray()))
        assertNull(EncryptedContainer.decrypt(encrypted, "wrong".toCharArray()))
    }

    private fun zipBytes(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun readEntries(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                result[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }
}
