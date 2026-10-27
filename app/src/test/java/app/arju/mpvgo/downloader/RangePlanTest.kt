package app.arju.mpvgo.downloader

import app.arju.mpvgo.downloader.engine.RangePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangePlanTest {
  private val mb = 1024L * 1024

  @Test
  fun `head and tail edges are claimed first`() {
    val plan = RangePlan.create(100 * mb, 8)
    val first = plan.claim()!!
    val second = plan.claim()!!
    assertEquals(0L, first.start)
    assertEquals(2 * mb, first.end)
    assertEquals(100 * mb, second.end)
    assertEquals(98 * mb, second.start)
    assertEquals(100 * mb, plan.snapshot().sumOf { it.end - it.start })
  }

  @Test
  fun `small files use plain equal parts`() {
    val plan = RangePlan.create(1000, 4)
    assertEquals(listOf(0L, 250L, 500L, 750L), plan.snapshot().map { it.start })
    assertEquals(1000L, plan.snapshot().last().end)
  }

  @Test
  fun `idle connection splits the largest running part`() {
    val plan = RangePlan.create(40 * mb, 1)
    val only = plan.claim()!!
    plan.advance(only, (4 * mb).toInt())
    val stolen = plan.claim()!!
    assertEquals(22 * mb, stolen.start)
    assertEquals(40 * mb, stolen.end)
    assertEquals(22 * mb, only.end)
  }

  @Test
  fun `nothing to claim when remaining work is too small to split`() {
    val plan = RangePlan.create(1 * mb, 1)
    plan.claim()!!
    assertNull(plan.claim())
  }

  @Test
  fun `completion and availability`() {
    val plan = RangePlan.create(1000, 2)
    val a = plan.claim()!!
    plan.advance(a, 500)
    assertTrue(plan.isAvailable(499))
    assertFalse(plan.isAvailable(500))
    assertEquals(500L, plan.availableFrom(0))
    assertFalse(plan.isComplete())
    val b = plan.claim()!!
    plan.advance(b, 500)
    assertTrue(plan.isComplete())
    assertEquals(1000L, plan.availableFrom(0))
    assertEquals(1000L, plan.downloadedBytes())
  }

  @Test
  fun `prioritized seek offset is claimed next`() {
    val plan = RangePlan.create(100 * mb, 4)
    plan.claim()
    plan.prioritize(50 * mb)
    val p = plan.claim()!!
    assertEquals(50 * mb, p.start)
    assertEquals(100 * mb, plan.snapshot().sumOf { it.end - it.start })
  }

  @Test
  fun `already downloaded offsets are not prioritized`() {
    val plan = RangePlan.create(1000, 1)
    val p = plan.claim()!!
    plan.advance(p, 600)
    plan.prioritize(100)
    assertFalse(plan.hasPriorityWork())
  }

  @Test
  fun `serialize round trip keeps progress`() {
    val plan = RangePlan.create(100 * mb, 4)
    val p = plan.claim()!!
    plan.advance(p, 1234)
    val restored = RangePlan.deserialize(100 * mb, plan.serialize())
    assertNotNull(restored)
    assertEquals(plan.downloadedBytes(), restored!!.downloadedBytes())
    assertEquals(plan.serialize(), restored.serialize())
  }

  @Test
  fun `corrupt serialized plan is rejected`() {
    assertNull(RangePlan.deserialize(100, "0:0:50"))
    assertNull(RangePlan.deserialize(100, "garbage"))
  }
}
