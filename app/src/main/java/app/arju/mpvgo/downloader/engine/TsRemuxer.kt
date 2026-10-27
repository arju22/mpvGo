package app.arju.mpvgo.downloader.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/** Copies the H.264/HEVC and AAC tracks of an MPEG-TS file into MP4 without re-encoding. */
object TsRemuxer {
  private const val TAG = "TsRemuxer"
  private val supported = setOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_AUDIO_AAC)

  fun remux(input: File, output: File): Boolean {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    return try {
      extractor.setDataSource(input.absolutePath)
      muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val map = HashMap<Int, Int>()
      var hasVideo = false
      var maxInput = 1024 * 1024
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime !in supported) continue
        if (mime.startsWith("video/")) hasVideo = true
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
          maxInput = maxOf(maxInput, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        extractor.selectTrack(i)
        map[i] = muxer.addTrack(format)
      }
      if (!hasVideo) return false
      muxer.start()
      val buffer = ByteBuffer.allocate(maxInput.coerceAtMost(16 * 1024 * 1024))
      val info = MediaCodec.BufferInfo()
      while (true) {
        val track = extractor.sampleTrackIndex
        if (track < 0) break
        val out = map[track]
        if (out != null) {
          info.offset = 0
          info.size = extractor.readSampleData(buffer, 0)
          if (info.size < 0) break
          info.presentationTimeUs = extractor.sampleTime
          info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
          muxer.writeSampleData(out, buffer, info)
        }
        extractor.advance()
      }
      muxer.stop()
      true
    } catch (t: Throwable) {
      Log.w(TAG, "Remux failed, keeping .ts", t)
      false
    } finally {
      runCatching { muxer?.release() }
      extractor.release()
    }
  }
}
