package com.github.kmpp.jsonrpc

const val JSON_RPC = "2.0"

sealed class JsonRpc(
    val jsonrpc: String = JSON_RPC
)

sealed class RequestJsonRpc<P>(
    open val method: String,
    open val params: P? = null
) : JsonRpc()

data class ClientRequestJsonRpc<P>(
    override val method: String,
    override val params: P? = null,
    val id: JsonRpcID
) : RequestJsonRpc<P>(method, params)

data class NotificationJsonRpc<P>(
    override val method: String,
    override val params: P? = null
) : RequestJsonRpc<P>(method, params)

@Suppress("unused") // IntelliJ incorrectly reads type params as unused
sealed class ResponseJsonRpc<R, E>(
    open val id: JsonRpcID
) : JsonRpc()

data class ResultJsonRpc<R>(
    val result: R,
    override val id: JsonRpcID
) : ResponseJsonRpc<R, Any>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified E> coerceErrorType() = this as ResponseJsonRpc<R, E>
}

data class ErrorJsonRpc<E>(
    val error: JsonRpcErrorObject<E>,
    override val id: JsonRpcID
) : ResponseJsonRpc<Any, E>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified R> coerceResultType() = this as ResponseJsonRpc<R, E>
}

data class JsonRpcErrorObject<E>(
    val code: Int,
    val message: String,
    val data: E? = null
) {
    companion object {
        fun <E> parseError(data: E? = null): JsonRpcErrorObject<E> =
            JsonRpcErrorObject(code = -32700, message = "Parse error", data = data)

        fun <E> invalidRequest(data: E? = null): JsonRpcErrorObject<E> =
            JsonRpcErrorObject(code = -32600, message = "Invalid Request", data = data)

        fun <E> methodNotFound(data: E? = null): JsonRpcErrorObject<E> =
            JsonRpcErrorObject(code = -32601, message = "Method not found", data = data)

        fun <E> invalidParams(data: E? = null): JsonRpcErrorObject<E> =
            JsonRpcErrorObject(code = -32602, message = "Invalid params", data = data)

        fun <E> internalError(data: E? = null): JsonRpcErrorObject<E> =
            JsonRpcErrorObject(code = -32603, message = "Internal error", data = data)

        fun <E> serverError(code: Int, data: E? = null): JsonRpcErrorObject<E> {
            require(code in -32099..-32000) { "code=$code outside JSON-RPC server error range" }
            return JsonRpcErrorObject(code = code, message = "Server error", data = data)
        }
    }
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

@Suppress("unused")
sealed class ReadResult<T> {
    companion object {
        fun <T> validRequest(request: T): ReadResult<T> = ReadSuccess(request)

        @Suppress("UNCHECKED_CAST")
        fun <T> error(error: ReadError): ReadResult<T> = error as ReadResult<T>
    }
}

data class ReadSuccess<T> internal constructor(val message: T) : ReadResult<T>()

sealed class ReadError(
    open val details: String?,
    open val id: JsonRpcID
) : ReadResult<Any>() {
    val stringError by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ErrorJsonRpc(this.toErrorObject(), id)
    }
    val stringErrorJson by lazy(LazyThreadSafetyMode.PUBLICATION) {
        saveStringErrorJsonRpc(stringError)
    }

    fun toErrorObject(): JsonRpcErrorObject<String> = when (this) {
        is ParseError -> JsonRpcErrorObject.parseError(details)
        is InvalidRequest -> JsonRpcErrorObject.invalidRequest(details)
        is InvalidParams -> JsonRpcErrorObject.invalidParams(details)
        is InternalError -> JsonRpcErrorObject.internalError(details)
    }
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

