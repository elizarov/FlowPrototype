package sequential

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class FlowTest {
    @Test
    fun testIntRangeToList() = runBlocking {
        val result = (1..3).asFlow().toList()
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun testFirst() = runBlocking {
        val result = listOf("A", "B", "C").asFlow().first()
        assertEquals("A", result)
    }

    @Test
    fun testFirstEven() = runBlocking {
        val result = listOf(1, 2, 3).asFlow().first { it % 2 == 0 }
        assertEquals(2, result)
    }

    @Test
    fun testReduce() = runBlocking {
        val result = listOf("A", "B", "C").asFlow().reduce { acc, s -> acc + s }
        assertEquals("ABC", result)
    }

    @Test
    fun testSum() = runBlocking {
        val result = (1..4).asFlow().sum()
        assertEquals(10, result)
    }

    @Test
    fun testFilterEven() = runBlocking {
        val result = (1..5).asFlow().filter { it % 2 == 0 }.toList()
        assertEquals(listOf(2, 4), result)
    }

    @Test
    fun testMapSquare() = runBlocking {
        val result = (1..5).asFlow().map { it * it }.toList()
        assertEquals(listOf(1, 4, 9, 16, 25), result)
    }
}