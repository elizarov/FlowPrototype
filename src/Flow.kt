import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

// todo: remove it, does not seem needed, only isSequential: Boolean should be enough
inline class Concurrency(val value: Int) {
    val isSequential: Boolean get() = value == 1
    val isUnlimited: Boolean get() = value == Int.MAX_VALUE

    operator fun plus(other: Concurrency): Concurrency {
        val sum = value + other.value
        return if (sum < value) UNLIMITED else Concurrency(sum)
    }

    companion object {
        val SEQUENTIAL: Concurrency = Concurrency(1)
        val UNLIMITED: Concurrency = Concurrency(Int.MAX_VALUE)
    }
}

interface Flow<out T> {
    val concurrency: Concurrency
    fun CoroutineScope.start(consumer: FlowConsumer<T>)

}

// todo: find a better name
interface FlowConsumer<in T> {
    fun createSink(): FlowSink<T>
}

// todo: find a better name
interface FlowSink<in T> {
    suspend fun process(element: T)
}

@PublishedApi
internal abstract class SequentialFlow<T> : Flow<T> {
    override val concurrency: Concurrency get() = Concurrency.SEQUENTIAL
}

private class IterableFlow<T>(
    private val iterable: Iterable<T>
) : SequentialFlow<T>() {
    override fun CoroutineScope.start(consumer: FlowConsumer<T>) {
        val sink = consumer.createSink()
        launch {
            for (element in iterable) {
                sink.process(element)
            }
        }
    }
}

fun <T> Iterable<T>.asFlow(): Flow<T> = IterableFlow(this)

private class SequenceFlow<T>(
    private val sequence: Sequence<T>
) : SequentialFlow<T>() {
    override fun CoroutineScope.start(consumer: FlowConsumer<T>) {
        val sink = consumer.createSink()
        launch {
            for (element in sequence) {
                sink.process(element)
            }
        }
    }
}

fun <T> Sequence<T>.asFlow(): Flow<T> = SequenceFlow(this)

private class IntRangeFlow(
    private val start: Int,
    private val endInclusive: Int
) : SequentialFlow<Int>() {
    override fun CoroutineScope.start(consumer: FlowConsumer<Int>) {
        val sink = consumer.createSink()
        launch {
            for (element in start..endInclusive) {
                sink.process(element)
            }
        }
    }
}

fun IntRange.asFlow(): Flow<Int> = IntRangeFlow(start, endInclusive)

internal abstract class TransformFlow<S, T>(
    @JvmField val source: Flow<S>,
    override val concurrency: Concurrency
) : Flow<T> {
    @Suppress("UNCHECKED_CAST")
    override fun CoroutineScope.start(consumer: FlowConsumer<T>) {
        var transform = this as TransformFlow<Any?, Any?>
        var transformedConsumer = consumer as FlowConsumer<Any?>
        while (true) {
            with(transform) {
                transformedConsumer = transformConsumer(transformedConsumer)
            }
            transform = ((transform.source as? TransformFlow<*, *>) ?: break) as TransformFlow<Any?, Any?>
        }
        with(transform.source) {
            start(transformedConsumer)
        }
    }

    protected abstract fun CoroutineScope.transformConsumer(consumer: FlowConsumer<T>): FlowConsumer<S>
}

@PublishedApi
internal abstract class TransformSinkFlow<S, T>(
    source: Flow<S>
) : TransformFlow<S, T>(source, source.concurrency) {
    override fun CoroutineScope.transformConsumer(consumer: FlowConsumer<T>): FlowConsumer<S> =
        object : FlowConsumer<S> {
            override fun createSink(): FlowSink<S> =
                transformSink(consumer.createSink())
        }

    abstract fun transformSink(sink: FlowSink<T>): FlowSink<S>
}

inline fun <T> Flow<T>.filter(crossinline predicate: suspend (T) -> Boolean): Flow<T> =
    object : TransformSinkFlow<T, T>(this@filter) {
        override fun transformSink(sink: FlowSink<T>): FlowSink<T> = object : FlowSink<T> {
            override suspend fun process(element: T) {
                if (predicate(element)) {
                    sink.process(element)
                }
            }
        }
    }

inline fun <S, T> Flow<S>.map(crossinline transform: suspend (S) -> T): Flow<T> =
    object : TransformSinkFlow<S, T>(this@map) {
        override fun transformSink(sink: FlowSink<T>): FlowSink<S> = object : FlowSink<S> {
            override suspend fun process(element: S) {
                sink.process(transform(element))
            }
        }
    }

private class MergeFlow<R, T : R, U : R>(
    private val flow1: Flow<T>,
    private val flow2: Flow<U>
) : Flow<R> {
    override val concurrency: Concurrency = flow1.concurrency + flow2.concurrency

    override fun CoroutineScope.start(consumer: FlowConsumer<R>) {
        with(flow1) {
            start(consumer)
        }
        with(flow2) {
            start(consumer)
        }
    }
}

infix fun <R, T : R, U : R> Flow<T>.merge(other: Flow<U>): Flow<R> =
    MergeFlow(this, other)

@PublishedApi
internal abstract class ActionSink<T> : FlowSink<T>, FlowConsumer<T> {
    override fun createSink(): FlowSink<T> = this
}

suspend inline fun <T> Flow<T>.forEach(crossinline action: suspend (T) -> Unit) {
    coroutineScope {
        start(object : ActionSink<T>() {
            override suspend fun process(element: T) = action(element)
        })
    }
}

private class ChannelSink<T>(private val channel: SendChannel<T>) : ActionSink<T>() {
    override suspend fun process(element: T) = channel.send(element)
}

private class ConcurrentFlow<T>(
    source: Flow<T>,
    concurrency: Concurrency
) : TransformFlow<T, T>(source, concurrency) {
    override fun CoroutineScope.transformConsumer(consumer: FlowConsumer<T>): FlowConsumer<T> {
        val channel = Channel<T>()
        // todo: launch on demand
        repeat(concurrency.value) {
            val sink = consumer.createSink()
            launch {
                for (element in channel) {
                    sink.process(element)
                }
            }
        }
        return ChannelSink(channel)
    }
}

tailrec fun <T> Flow<T>.concurrent(concurrency: Concurrency): Flow<T> {
    if (this.concurrency == concurrency) return this
    if (this is ConcurrentFlow) return source.concurrent(concurrency)
    return ConcurrentFlow(this, concurrency)
}

fun <T> Flow<T>.sequential(): Flow<T> = concurrent(Concurrency.SEQUENTIAL)

suspend fun <T> Flow<T>.toList(): List<T> {
    val result = ArrayList<T>()
    sequential().forEach { result.add(it) }
    return result
}

private enum class Symbol { NOT_RECEIVED }

private object FlowCancelled : CancellationException("Flow is cancelled") {
    init {
        stackTrace = arrayOf()
    }
}

private fun cancelFlow(): Nothing = throw FlowCancelled

suspend fun <T> Flow<T>.first(): T {
    var element: Any? = Symbol.NOT_RECEIVED
    try {
        sequential().forEach {
            element = it
            cancelFlow()
        }
    } catch (e: FlowCancelled) { /** done **/ }
    if (element === Symbol.NOT_RECEIVED) throw NoSuchElementException("No element was received")
    @Suppress("UNCHECKED_CAST")
    return element as T
}


