package concurrent

import kotlinx.coroutines.*

fun main() = runBlocking<Unit> {
    (1..10).asFlow().forEach {
        println(it)
    }
}