package sequential

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class ConcurrentFlowsTest {
    @Test
    fun testConcurrentAndBack() = runBlocking<Unit> {
        val data = 1..10
        val result = data.asFlow()
            .concurrent(3)
            .sequential()
            .toSet()
        assertEquals(data.toSet(), result)
    }

    @Test
    fun testConcurrentFilterEven() = runBlocking<Unit> {
        val data = 1..10
        val result = data.asFlow()
            .concurrent(3)
            .filter { it % 2 == 0 }
            .sequential()
            .toSet()
        assertEquals(data.filter { it % 2 == 0 }.toSet(), result)
    }
}