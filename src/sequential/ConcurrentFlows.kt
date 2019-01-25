@file:UseExperimental(ExperimentalCoroutinesApi::class)

package sequential

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

interface ConcurrentFlows<T> {
    fun CoroutineScope.launchConcurrentFlows(consumerFactory: () -> FlowConsumer<T>): List<Job>
}

// ------------ Builders ------------

fun <T> Flow<T>.concurrent(concurrency: Int): ConcurrentFlows<T> = object : ConcurrentFlows<T> {
    override fun CoroutineScope.launchConcurrentFlows(consumerFactory: () -> FlowConsumer<T>): List<Job> {
        val channel = produce<T> {
            consumeEach { value ->
                send(value)
            }
        }
        return List(concurrency) {
            launch {
                val consumer = consumerFactory()
                for (value in channel) {
                    consumer.consume(value)
                }
            }
        }
    }
}

fun <T> ConcurrentFlows<T>.sequential(): Flow<T> = flow {
    val channel = Channel<T>()
    val consumer = object : FlowConsumer<T> {
        override suspend fun consume(value: T) = channel.send(value)
    }
    coroutineScope {
        val jobs = launchConcurrentFlows { consumer }
        // kludge: close channel when all flow jobs are complete. todo: can we do better?
        launch {
            jobs.forEach { it.join() }
            channel.close()
        }
        for (value in channel) {
            consume(value)
        }
    }
}

// ------------ Intermediate transformations ------------

// todo: name?
inline fun <T, R> ConcurrentFlows<T>.transform(crossinline action: suspend FlowConsumer<R>.(T) -> Unit): ConcurrentFlows<R> =
    object : ConcurrentFlows<R> {
        // todo: unwind launchConcurrentFlows call stack
        override fun CoroutineScope.launchConcurrentFlows(consumerFactory: () -> FlowConsumer<R>): List<Job> =
            with(this@transform) {
                launchConcurrentFlows {
                    val consumer = consumerFactory()
                    object : FlowConsumer<T> {
                        override suspend fun consume(value: T) = consumer.action(value)
                    }
                }
            }
    }

inline fun <T> ConcurrentFlows<T>.filter(crossinline predicate: suspend (T) -> Boolean): ConcurrentFlows<T> =
    transform { value ->
        if (predicate(value)) consume(value)
    }

inline fun <T, R> ConcurrentFlows<T>.map(crossinline transform: suspend (T) -> R): ConcurrentFlows<R> =
    this@map.transform { value ->
        consume(transform(value))
    }

inline fun <T> ConcurrentFlows<T>.onEach(crossinline action: suspend (T) -> Unit): ConcurrentFlows<T> =
    transform { value ->
        action(value)
        consume(value)
    }

