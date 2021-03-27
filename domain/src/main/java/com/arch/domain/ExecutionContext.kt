package com.arch.domain

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

/**
 * ```
 * val result = Result.Success("Hello World!")
 * val error = Result.Error(ErrorResult("Some error text"))
```
 *
 */
sealed class Result<out T : Any> {

    data class Running<out T : Any>(val data: T? = null) : Result<T>()

    data class Success<out T : Any>(val data: T) : Result<T>()

    data class Error<out T : Any>(val error: ErrorResult, val data: T? = null) : Result<T>()

    override fun toString(): String {
        return when (this) {
            is Success -> "Success[data=$data]"
            is Error -> "Error[exception=${error.throwable}"
            is Running -> "Running[cachedData=$data]"
        }
    }

    fun isFinished() = this is Success || this is Error

    fun isRunning() = this is Running

    fun isSuccess() = this is Success

    fun isError() = this is Error

    /**
     * Returns the encapsulated value if this instance represents [Success] or cached data when available in [Running] state
     */
    fun getOrNull() = when {
        this is Success -> data
        this is Running -> data
        this is Error -> data
        else -> null
    }

    /**
     * Returns the encapsulated error if this instance represents [Error]
     */
    fun errorOrNull() = if (this is Error) error else null

    /**
     * Returns [Result] of same type ([Result.Success], [Result.Error] or [Result.Running]) but with different "data" type.
     * Original data are transformed by passed [dataTransform] function.
     *
     * **Example**
     * ```
     * suspend fun getActiveVehicleCode():Result<VehicleCode> =
     *     getActiveVehicleUseCase().map { vehicle ->
     *         vehicle.code
     *     }
     * ```
     * @param dataTransform Function that transforms data from one type to another
     * @param R Target "data" type
     *
     * @return Result of same type but with different data type.
     */
    inline fun <R : Any> map(dataTransform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(dataTransform(data))
        is Error -> Error(this.error, data?.let { dataTransform(it) })
        is Running -> Running(data?.let { dataTransform(it) })
    }
}

open class ErrorResult(open var message: String? = null, open var throwable: Throwable? = null)

/**
 * Wrap a suspending [call] in try/catch. In case an exception is thrown, a [Result.Error] is
 * created based on the [errorMessage].
 */
suspend fun <T : Any> safeCall(call: suspend () -> Result<T>, errorMessage: String): Result<T> {
    return try {
        call()
    } catch (e: Throwable) {
        Result.Error(ErrorResult(errorMessage, e))
    }
}

/**
 * Execute parallelly
 * Run inputs with some [asyncBlock] in parallel. When all async works are done, than function
 * [map] [AsyncResult] into specified [Output]
 */
suspend fun <Input, AsyncResult, Output> List<Input>.executeParallelly(
    context: CoroutineContext? = null,
    asyncBlock: suspend (Input) -> AsyncResult,
    map: ((AsyncResult) -> Output?)
): List<Output> {
    val queueDeferredList = mutableListOf<Deferred<AsyncResult>>()
    val resultList = mutableListOf<Output>()
    this.forEach { input ->
        queueDeferredList.add(CoroutineScope(context ?: coroutineContext).async {
            asyncBlock(
                input
            )
        })
    }
    queueDeferredList.awaitAll().forEach {
        map(it)?.let { output ->
            resultList.add(output)
        }
    }
    return resultList
}

/**
 *  Method for sequential call to local persistence and server request
 *
 *  @param[localCall] Call to local persistence (cache, db, prefs etc.)
 *  @param[remoteCall] Call to server
 *
 *  @return [ReceiveChannel] emitting loaded data with [Result]
 */
@ExperimentalCoroutinesApi
suspend fun <T : Any> combinedCall(
    localCall: suspend () -> T?,
    remoteCall: suspend () -> Result<T>
): ReceiveChannel<Result<T>> = CoroutineScope(coroutineContext).produce {
    // Send info about running process with empty data
    send(Result.Running())
    // TODO improve content of this method to run both task concurrently - needs to manage correct sequence of states!!
    // Send info about running process with local cached data if available
    val cachedLocalData = localCall()
    send(Result.Running(cachedLocalData))

    // Send result of remote request
    val remoteResult = when (val remoteCallResult = remoteCall()) {
        is Result.Error -> remoteCallResult.copy(data = cachedLocalData)
        else -> remoteCallResult
    }

    send(remoteResult)
}
