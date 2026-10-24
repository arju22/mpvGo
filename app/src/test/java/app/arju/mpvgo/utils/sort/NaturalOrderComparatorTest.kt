package app.arju.mpvgo.utils.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NaturalOrderComparatorTest {
  private val cmp = SortUtils.NaturalOrderComparator.DEFAULT

  private fun sign(x: Int) = Integer.signum(x)

  @Test
  fun `small numbers sort naturally`() {
    val sorted = listOf("ep10", "ep2", "ep1").sortedWith(cmp)
    assertEquals(listOf("ep1", "ep2", "ep10"), sorted)
  }

  @Test
  fun `digit runs beyond Int and Long range compare by value`() {
    assertTrue(cmp.compare("x3", "x2147483648") < 0)
    assertTrue(cmp.compare("x2147483648", "x10000000000000000000000") < 0)
    assertTrue(cmp.compare("x99999999999999999999", "x100000000000000000000") < 0)
    assertTrue(cmp.compare("x100000000000000000001", "x100000000000000000000") > 0)
  }

  @Test
  fun `leading zeros compare equal by value`() {
    assertEquals(0, cmp.compare("a007b", "a7b"))
    assertTrue(cmp.compare("a0000000000000000000000009", "a10") < 0)
  }

  @Test
  fun `previously non-transitive triple is now consistent`() {
    val a = "x3"
    val b = "x10"
    val c = "x2147483648"
    assertTrue(cmp.compare(a, b) < 0)
    assertTrue(cmp.compare(b, c) < 0)
    assertTrue(cmp.compare(a, c) < 0)
  }

  @Test
  fun `testing baseline case - 30 digit run sorts after Int max (#861)`() {
    assertTrue(cmp.compare("clip 123456789012345678901234567890", "clip 2147483647") > 0)
    val expected = listOf(
      "clip 1", "clip 2", "clip 10", "clip 99",
      "clip 2147483647", "clip 2147483648",
      "clip 9223372036854775807", "clip 9223372036854775808",
      "clip 123456789012345678901234567890",
    )
    // 40+ items so TimSort actually runs its merge/contract checks (it uses insertion sort below 32)
    val many = (expected + (1..40).map { "zz filler $it" }).shuffled(Random(19))
    val sorted = many.sortedWith(cmp)
    assertEquals(expected, sorted.take(expected.size))
    assertEquals((1..40).map { "zz filler $it" }, sorted.drop(expected.size))
  }

  @Test
  fun `comparator contract holds on random names`() {
    val rnd = Random(861)
    val alphabet = "ab0123456789 ._-Z\u0663\u0661"
    fun randomName(): String {
      val len = rnd.nextInt(1, 30)
      return buildString {
        repeat(len) {
          if (rnd.nextInt(5) == 0) {
            repeat(rnd.nextInt(1, 25)) { append('0' + rnd.nextInt(10)) }
          } else {
            append(alphabet[rnd.nextInt(alphabet.length)])
          }
        }
      }
    }
    val names = List(150) { randomName() } + listOf("x3", "x10", "x2147483648", "x9223372036854775808")
    for (x in names) {
      assertEquals("reflexive: $x", 0, cmp.compare(x, x))
      for (y in names) {
        val xy = sign(cmp.compare(x, y))
        assertEquals("antisymmetric: $x / $y", -xy, sign(cmp.compare(y, x)))
        for (z in names) {
          if (xy <= 0 && sign(cmp.compare(y, z)) <= 0) {
            assertTrue("transitive: $x <= $y <= $z", cmp.compare(x, z) <= 0)
          }
        }
      }
    }
    // TimSort must not throw "Comparison method violates its general contract!"
    repeat(50) { names.shuffled(rnd).sortedWith(cmp) }
  }
}
