package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ErrorWriter
import com.github.kmpp.jsonrpc.internal.toErrorObject
import kotlinx.serialization.json.JsonElement


const val JSON_RPC_2_0 = "2.0"

sealed class JsonRpc(
    val jsonrpc: String = JSON_RPC_2_0
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

sealed class Response<out R>(
    open val result: R?,
    open val error: ErrorObject?,
    open val id: JsonRpcID
) : JsonRpc()

data class Result<R>(
    override val result: R,
    override val id: JsonRpcID
) : Response<R>(result = result, error = null, id = id)

data class Error(
    override val error: ErrorObject,
    override val id: JsonRpcID
) : Response<Nothing>(result = null, error = error, id = id)

data class ErrorObject(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    fun toError(id: JsonRpcID) = Error(this, id)
}

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

sealed class ReadOutcome<out T>(
    open val success: T?,
    open val failure: RequestError?
)

data class ReadSuccess<T> internal constructor(
    override val success: T
) : ReadOutcome<T>(success = success, failure = null)

data class ReadFailure(
    override val failure: RequestError
) : ReadOutcome<Nothing>(success = null, failure = failure)

fun methodNotFound(data: JsonElement? = null): ErrorObject =
    ErrorObject(code = -32601, message = "Method not found", data = data)

fun invalidParams(data: JsonElement? = null): ErrorObject =
    ErrorObject(code = -32602, message = "Invalid params", data = data)

fun internalError(data: JsonElement? = null): ErrorObject =
    ErrorObject(code = -32603, message = "Internal error", data = data)

fun serverError(code: Int, data: JsonElement? = null): ErrorObject {
    require(code in -32099..-32000) { "code=$code outside JSON-RPC server error range" }
    return ErrorObject(code = code, message = "Server error", data = data)
}

sealed class RequestError(
    open val details: String?,
    open val id: JsonRpcID
) {
    val error: Error by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Error(this.toErrorObject(), id)
    }
    val errorJsonString: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ErrorWriter.write(error).toString()
    }
}

/**
 * An error raised when Request was not valid JSON
 */
data class ParseError internal constructor(
    override val details: String?
) : RequestError(details, JsonRpcNullID)

sealed class ClientRequestError(
    override val details: String?,
    override val id: JsonRpcID
) : RequestError(details, id)

/**
 * An error raised when Request was valid JSON, but was not a valid JSON-RPC Request object
 */
data class InvalidRequest internal constructor(
    override val details: String?,
    override val id: JsonRpcID
) : ClientRequestError(details, id)

/**
 * An error raised when Request was a valid JSON-RPC object, but the value present in `"params"`
 * could not be correctly parsed/read for the given `"method"`
 */
data class InvalidParams internal constructor(
    override val details: String?,
    override val id: JsonRpcID
) : ClientRequestError(details, id)

data class InternalError internal constructor(
    override val details: String?
) : ClientRequestError(details, JsonRpcNullID)

