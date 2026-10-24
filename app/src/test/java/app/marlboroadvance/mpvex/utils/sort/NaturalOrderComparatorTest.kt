package app.marlboroadvance.mpvex.utils.sort

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
