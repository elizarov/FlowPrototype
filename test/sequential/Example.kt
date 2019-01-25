package sequential

import kotlinx.coroutines.*

fun main() = runBlocking<Unit> {
    (1..10).asFlow()
//        .onEach { delay(100) }
        .consumeEach { println(it) }

//    (1..10).asFlow()
//        .onEach { print("$it: ") }
//        .onEach { delay(100) }
//        .map { it * it }
//        .filter { it % 2 == 0 }
//        .consumeEach { println(it) }
}
