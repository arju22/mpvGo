package app.arju.mpvgo.subtitles

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object MovieHash {
  private const val HASH_CHUNK_SIZE = 65536L // 64 KB

  /**
   * Computes the OpenSubtitles 64-bit checksum hash for a local file.
   * Returns a 16-character lowercase hex string, or null if file is too small or unreadable.
   */
  fun compute(file: File): String? {
    if (!file.exists() || !file.isFile) return null
    val size = file.length()
    if (size < HASH_CHUNK_SIZE) return null

    return runCatching {
      FileInputStream(file).use { fis ->
        compute(fis.channel, size)
      }
    }.getOrNull()
  }

  fun compute(channel: FileChannel, size: Long): String {
    val head = channel.map(FileChannel.MapMode.READ_ONLY, 0, minOf(size, HASH_CHUNK_SIZE))
    val tail = channel.map(
      FileChannel.MapMode.READ_ONLY,
      maxOf(0L, size - HASH_CHUNK_SIZE),
      minOf(size, HASH_CHUNK_SIZE)
    )

    var hash = size
    hash += readChunk(head)
    hash += readChunk(tail)

    return String.format("%016x", hash)
  }

  /**
   * Computes the hash for byte arrays (used for in-memory testing or chunks).
   */
  fun computeFromChunks(size: Long, headBytes: ByteArray, tailBytes: ByteArray): String {
    var hash = size
    hash += readBytes(headBytes)
    hash += readBytes(tailBytes)
    return String.format("%016x", hash)
  }

  private fun readChunk(buffer: ByteBuffer): Long {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    var sum = 0L
    while (buffer.remaining() >= 8) {
      sum += buffer.long
    }
    return sum
  }

  private fun readBytes(bytes: ByteArray): Long {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var sum = 0L
    while (buffer.remaining() >= 8) {
      sum += buffer.long
    }
    return sum
  }
}
