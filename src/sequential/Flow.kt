package sequential

interface Flow<T> {
    suspend fun consumeEach(consumer: FlowConsumer<T>)
}

interface FlowConsumer<T> {
    suspend fun consume(value: T)
}

// ------------ Builders ------------

inline fun <T> flow(crossinline block: suspend FlowConsumer<T>.() -> Unit) = object : Flow<T> {
    override suspend fun consumeEach(consumer: FlowConsumer<T>) = consumer.block()
}

fun <T> Iterable<T>.asFlow(): Flow<T> = flow {
    forEach { value ->
        consume(value)
    }
}

fun <T> Sequence<T>.asFlow(): Flow<T> = flow {
    forEach { value ->
        consume(value)
    }
}

// ------------ Intermediate transformations ------------

inline fun <T> Flow<T>.filter(crossinline predicate: suspend (T) -> Boolean): Flow<T> = flow {
    consumeEach { value ->
        if (predicate(value)) consume(value)
    }
}

inline fun <T, R> Flow<T>.map(crossinline transform: suspend (T) -> R): Flow<R> = flow {
    consumeEach { value ->
        consume(transform(value))
    }
}

inline fun <T> Flow<T>.onEach(crossinline action: suspend (T) -> Unit): Flow<T> = flow {
    consumeEach { value ->
        action(value)
        consume(value)
    }
}

// ------------ Terminal operations ------------

suspend inline fun <T> Flow<T>.consumeEach(crossinline action: suspend (T) -> Unit) =
    consumeEach(object : FlowConsumer<T> {
        override suspend fun consume(value: T) = action(value)
    })

@PublishedApi
internal object FlowConsumerAborted : Throwable("Flow consumer aborted", null, false, false)

suspend inline fun <T> Flow<T>.consumeEachWhile(crossinline action: suspend (T) -> Boolean): Boolean = try {
    consumeEach { value ->
        if (!action(value)) throw FlowConsumerAborted
    }
    true
} catch (e: FlowConsumerAborted) {
    false
}

suspend fun <T, C : MutableCollection<in T>> Flow<T>.toCollection(destination: C): C {
    consumeEach { value ->
        destination.add(value)
    }
    return destination
}

suspend fun <T> Flow<T>.toList(): List<T> =
    toCollection(ArrayList<T>())

suspend fun <T> Flow<T>.toSet(): Set<T> =
    toCollection(LinkedHashSet<T>())

suspend inline fun <S, T : S> Flow<T>.reduce(crossinline operation: suspend (acc: S, value: T) -> S): S {
    var found = false
    var accumulator: S? = null
    consumeEach { value ->
        accumulator = if (found) {
            @Suppress("UNCHECKED_CAST")
            operation(accumulator as S, value)
        } else {
            found = true
            value
        }
    }
    if (!found) throw UnsupportedOperationException("Empty flow can't be reduced")
    @Suppress("UNCHECKED_CAST")
    return accumulator as S
}

suspend inline fun <T, R> Flow<T>.fold(initial: R, crossinline operation: suspend (acc: R, value: T) -> R): R {
    var accumulator = initial
    consumeEach { value ->
        accumulator = operation(accumulator, value)
    }
    return accumulator
}

suspend fun Flow<Int>.sum() = fold(0) { acc, value -> acc + value }

suspend fun <T> Flow<T>.first(): T {
    var result: T? = null
    val consumed = consumeEachWhile { value ->
        result = value
        false
    }
    if (consumed) throw NoSuchElementException("Flow is empty")
    @Suppress("UNCHECKED_CAST")
    return result as T
}

suspend inline fun <T> Flow<T>.first(crossinline predicate: suspend (T) -> Boolean): T {
    var result: T? = null
    val consumed = consumeEachWhile { value ->
        if (predicate(value)) {
            result = value
            false
        } else
            true
    }
    if (consumed) throw NoSuchElementException("Flow contains no element matching the predicate")
    @Suppress("UNCHECKED_CAST")
    return result as T
}



// ----

suspend fun main() {
    (1..10).asFlow()
        .consumeEach {
            println(it)
        }
}