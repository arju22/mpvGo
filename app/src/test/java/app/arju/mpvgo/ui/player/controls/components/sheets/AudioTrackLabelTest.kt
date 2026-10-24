package app.arju.mpvgo.ui.player.controls.components.sheets

import app.arju.mpvgo.ui.player.TrackNode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AudioTrackLabelTest {
  private val en = Locale.ENGLISH

  private fun audio(
    id: Int = 1,
    title: String? = null,
    lang: String? = null,
    codec: String? = null,
    channels: String? = null,
    count: Long? = null,
  ) = TrackNode(id = id, type = "audio", title = title, lang = lang, codec = codec, demuxChannels = channels, demuxChannelCount = count)

  @Test
  fun `language code becomes name with codec and channels`() {
    assertEquals("#2: Hindi · AAC 5.1", AudioTrackLabel.format(audio(2, lang = "hin", codec = "aac", channels = "5.1(side)"), en))
    assertEquals("#1: English · Opus Stereo", AudioTrackLabel.format(audio(lang = "en", codec = "opus", count = 2), en))
  }

  @Test
  fun `undetermined language is hidden`() {
    assertEquals("#1: AC-3 5.1", AudioTrackLabel.format(audio(lang = "und", codec = "ac3", count = 6), en))
  }

  @Test
  fun `title is kept and language not repeated`() {
    assertEquals("#3: Commentary · English · AAC Stereo", AudioTrackLabel.format(audio(3, "Commentary", "eng", "aac", "stereo"), en))
    assertEquals("#3: Hindi Dub · E-AC-3 5.1", AudioTrackLabel.format(audio(3, "Hindi Dub", "hin", "eac3", "5.1"), en))
    assertEquals("#1: jpn · FLAC", AudioTrackLabel.format(audio(title = "jpn", lang = "jpn", codec = "flac"), en))
  }

  @Test
  fun `bibliographic and unknown codes`() {
    assertEquals("German", AudioTrackLabel.languageName("ger", en))
    assertEquals("French", AudioTrackLabel.languageName("fre", en))
    assertEquals("Portuguese", AudioTrackLabel.languageName("pt-BR", en))
    assertEquals("xyz", AudioTrackLabel.languageName("xyz", en))
    assertEquals(null, AudioTrackLabel.languageName(" ", en))
  }

  @Test
  fun `nothing known falls back to Audio #id`() {
    assertEquals("Audio #4", AudioTrackLabel.format(audio(4), en))
  }

  @Test
  fun `channel fallbacks`() {
    assertEquals("7.1", AudioTrackLabel.channelsName("7.1", null))
    assertEquals("Mono", AudioTrackLabel.channelsName(null, 1))
    assertEquals("4 ch", AudioTrackLabel.channelsName("quad", 4))
    assertEquals(null, AudioTrackLabel.channelsName(null, null))
  }
}
