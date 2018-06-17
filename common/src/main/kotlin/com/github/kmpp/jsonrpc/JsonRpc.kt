package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.toErrorObject
import com.github.kmpp.jsonrpc.jsonast.JsonElement

interface JsonRpcServer {
    fun handleClientRequest(request: ClientRequest<JsonElement>): Response<JsonElement, JsonElement>
    fun handleNotification(notification: Notification<JsonElement>)
    fun raisedError(error: Error<JsonElement>)
}

interface JsonRpcServerSerializer {
    fun parse(requestJson: String): ReadOutcome<Request<JsonElement>>
    fun stringifyResult(result: Result<*>): String
    fun stringifyError(error: Error<*>): String
}

interface JsonRpcClient {
    fun handleResponse(response: Response<*, *>)
}

interface JsonRpcClientSerializer {
    fun parse(responseJson: String): ReadOutcome<Response<JsonElement, JsonElement>>
    fun stringifyClientRequest(request: ClientRequest<*>): String
    fun stringifyNotification(notification: Notification<*>): String
}

const val JSON_RPC = "2.0"

sealed class JsonRpc(
    val jsonrpc: String = JSON_RPC
)

sealed class Request<P>(
    open val method: String,
    open val params: P? = null
) : JsonRpc()

data class ClientRequest<P>(
    override val method: String,
    override val params: P? = null,
    val id: JsonRpcID
) : Request<P>(method, params)

data class Notification<P>(
    override val method: String,
    override val params: P? = null
) : Request<P>(method, params)

@Suppress("unused") // IntelliJ incorrectly sees type params as unused
sealed class Response<R, E>(
    open val id: JsonRpcID
) : JsonRpc()

data class Result<R>(
    val result: R,
    override val id: JsonRpcID
) : Response<R, Nothing>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified E> coerceErrorType() = this as Response<R, E>
}

data class Error<E>(
    val error: ErrorObject<E>,
    override val id: JsonRpcID
) : Response<Nothing, E>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified R> coerceResultType() = this as Response<R, E>
}

data class ErrorObject<E>(
    val code: Int,
    val message: String,
    val data: E? = null
)

sealed class JsonRpcID {
    companion object {
        operator fun invoke(id: String) = JsonRpcStringID(id)
        operator fun invoke(id: Long) = JsonRpcNumberID(id)
    }
}

data class JsonRpcStringID internal constructor(val id: String) : JsonRpcID()
data class JsonRpcNumberID internal constructor(val id: Long) : JsonRpcID()
object JsonRpcNullID : JsonRpcID() {
    override fun toString() = "JsonRpcNullID(id=null)"
}

@Suppress("unused") // IntelliJ incorrectly sees type param as unused
sealed class ReadOutcome<T> {
    companion object {
        fun <T> validRequest(request: T): ReadOutcome<T> = ReadSuccess(request)

        @Suppress("UNCHECKED_CAST")
        fun <T> error(error: ReadError): ReadOutcome<T> = error as ReadOutcome<T>
    }
}

data class ReadSuccess<T> internal constructor(val message: T) : ReadOutcome<T>()

sealed class ReadError(
    open val details: String?,
    open val id: JsonRpcID
) : ReadOutcome<Any>() {
    val stringError: Error<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Error(this.toErrorObject(), id)
    }
    val stringErrorJson: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        saveStringError(stringError)
    }
}

fun <E> methodNotFound(data: E? = null): ErrorObject<E> =
    ErrorObject(code = -32601, message = "Method not found", data = data)

fun <E> invalidParams(data: E? = null): ErrorObject<E> =
    ErrorObject(code = -32602, message = "Invalid params", data = data)

fun <E> internalError(data: E? = null): ErrorObject<E> =
    ErrorObject(code = -32603, message = "Internal error", data = data)

fun <E> serverError(code: Int, data: E? = null): ErrorObject<E> {
    require(code in -32099..-32000) { "code=$code outside JSON-RPC server error range" }
    return ErrorObject(code = code, message = "Server error", data = data)
}

/**
 * An error raised when Request was not valid JSON
 */
data class ParseError internal constructor(
    override val details: String?
) : ReadError(details, JsonRpcNullID)

/**
 * An error raised when Request was valid JSON, but was not a valid JSON-RPC Request object
 */
data class InvalidRequest internal constructor(
    override val details: String?,
    override val id: JsonRpcID
) : ReadError(details, id)

/**
 * An error raised when Request was a valid JSON-RPC object, but the value present in `"params"`
 * could not be correctly parsed/read for the given `"method"`
 */
data class InvalidParams internal constructor(
    override val details: String?,
    override val id: JsonRpcID
) : ReadError(details, id)

data class InternalError internal constructor(
    override val details: String?
) : ReadError(details, JsonRpcNullID)

