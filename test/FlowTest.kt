import kotlinx.coroutines.*
import kotlin.test.*

class FlowTest {
    @Test
    fun testIntRangeToList() = runBlocking {
        val result = (1..3).asFlow().toList()
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun testListFirst() = runBlocking {
        val result = listOf("A", "B", "C").asFlow().first()
        assertEquals("A", result)
    }
}