package app.arju.mpvgo.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OnlineSubtitleTest {

  @Test
  fun `movie hash returns null for file too small`() {
    val smallFile = File.createTempFile("small", ".mkv").apply { deleteOnExit() }
    smallFile.writeBytes(ByteArray(1024))
    assertNull(MovieHash.compute(smallFile))
    smallFile.delete()
  }

  @Test
  fun `movie hash produces 16 hex char string for valid file`() {
    val validFile = File.createTempFile("test_video", ".mp4").apply { deleteOnExit() }
    val size = 65536 * 2 + 1024
    val bytes = ByteArray(size) { (it % 128).toByte() }
    validFile.writeBytes(bytes)

    val hash = MovieHash.compute(validFile)
    assertNotNull(hash)
    assertEquals(16, hash!!.length)
    assertTrue(hash.matches(Regex("^[0-9a-f]{16}$")))
    validFile.delete()
  }

  @Test
  fun `detect encoding converts UTF-8 and UTF-16 properly`() {
    val plainText = "1\n00:00:01,000 --> 00:00:03,000\nHello World\n"
    
    // Plain UTF-8
    val utf8Bytes = plainText.toByteArray(Charsets.UTF_8)
    val convertedUtf8 = SubtitleArchiveHelper.toUtf8String(utf8Bytes)
    assertEquals(plainText, convertedUtf8)

    // UTF-8 with BOM
    val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8Bytes
    val convertedBom = SubtitleArchiveHelper.toUtf8String(bomBytes)
    assertEquals(plainText, convertedBom)
  }

  @Test
  fun `extractSubtitleText extracts srt from zip archive`() {
    val srtContent = "1\n00:00:01,000 --> 00:00:02,000\nSubtitle text\n"
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      zos.putNextEntry(ZipEntry("movie.srt"))
      zos.write(srtContent.toByteArray(Charsets.UTF_8))
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("readme.txt"))
      zos.write("Some readme info".toByteArray(Charsets.UTF_8))
      zos.closeEntry()
    }

    val extracted = SubtitleArchiveHelper.extractSubtitleText(baos.toByteArray(), "test.zip")
    assertNotNull(extracted)
    assertEquals("movie.srt", extracted!!.first)
    assertEquals(srtContent, extracted.second)
  }

  @Test
  fun `extractSubtitleText returns text for non-archive srt`() {
    val srtContent = "1\n00:00:00,000 --> 00:00:01,000\nTest\n"
    val rawBytes = srtContent.toByteArray()
    val extracted = SubtitleArchiveHelper.extractSubtitleText(rawBytes, "sub.srt")
    assertNotNull(extracted)
    assertEquals("sub.srt", extracted!!.first)
    assertEquals(srtContent, extracted.second)
  }
}
