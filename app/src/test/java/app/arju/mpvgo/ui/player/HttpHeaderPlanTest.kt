package app.arju.mpvgo.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpHeaderPlanTest {
  @Test
  fun `legacy headers extra keeps user agent and all pairs`() {
    val plan = HttpHeaderPlan.build(arrayOf("User-Agent", "UA/1", "X-Token", "abc"), autoReferer = null)
    assertEquals("UA/1", plan.userAgent)
    assertEquals(listOf("X-Token: abc"), plan.fields)
  }

  @Test
  fun `first pair is kept even when it is not the user agent`() {
    val plan = HttpHeaderPlan.build(arrayOf("Referer", "https://a.com/", "Origin", "https://a.com"), autoReferer = null)
    assertNull(plan.userAgent)
    assertEquals(listOf("Referer: https://a.com/", "Origin: https://a.com"), plan.fields)
  }

  @Test
  fun `given referer overrides auto referer case-insensitively`() {
    val plan = HttpHeaderPlan.build(arrayOf("referer", "https://page.com/watch"), autoReferer = "https://cdn.com/")
    assertEquals(listOf("referer: https://page.com/watch"), plan.fields)
  }

  @Test
  fun `auto referer is used when none given`() {
    assertEquals(listOf("Referer: https://cdn.com/"), HttpHeaderPlan.build(null, "https://cdn.com/").fields)
  }

  @Test
  fun `webview cookies merge into cookie header and request cookies win`() {
    val plan = HttpHeaderPlan.build(arrayOf("Cookie", "sid=1; a=x"), null, extraCookies = "sid=2; cf=ok")
    assertEquals(listOf("Cookie: sid=1; a=x; cf=ok"), plan.fields)
  }

  @Test
  fun `webview cookies alone create a cookie header`() {
    assertEquals(listOf("Cookie: cf=ok"), HttpHeaderPlan.build(null, null, "cf=ok").fields)
  }

  @Test
  fun `header injection and odd entries are dropped`() {
    val plan = HttpHeaderPlan.build(arrayOf("X-A", "1\r\nEvil: yes", "X-B", "", "X-C"), null)
    assertEquals(emptyList<String>(), plan.fields)
  }

  @Test
  fun `mergeCookies dedupes by name and ignores blanks`() {
    assertEquals("a=1; b=2", HttpHeaderPlan.mergeCookies("a=1;; b=2", null, "a=9", " "))
    assertNull(HttpHeaderPlan.mergeCookies(null, ""))
  }

  @Test
  fun `mpv list escapes commas so cookies survive`() {
    val list = HttpHeaderPlan.toMpvList(listOf("Cookie: a=1,2; b=\\x", "Origin: https://a.com"))
    assertEquals("Cookie: a=1\\,2; b=\\x,Origin: https://a.com", list)
    assertEquals("", HttpHeaderPlan.toMpvList(emptyList()))
  }

  @Test
  fun `media request round-trips into the headers extra format`() {
    val request = MediaRequest(
      url = "https://cdn.com/v.m3u8",
      userAgent = "UA/1",
      referer = "https://page.com/",
      origin = "https://page.com",
      cookies = "sid=1",
      extraHeaders = mapOf("X-T" to "t"),
    )
    val plan = HttpHeaderPlan.build(HttpHeaderPlan.toPairs(request).toTypedArray(), "https://cdn.com/")
    assertEquals("UA/1", plan.userAgent)
    assertEquals(listOf("Referer: https://page.com/", "Origin: https://page.com", "Cookie: sid=1", "X-T: t"), plan.fields)
  }
}
