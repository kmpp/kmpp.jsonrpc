package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON

const val JSON_RPC = "2.0"

sealed class JsonRpc(
    val jsonrpc: String = JSON_RPC
)

sealed class ClientJsonRpc<P>(
    open val method: String,
    open val params: P? = null
) : JsonRpc()

data class RequestJsonRpc<P>(
    override val method: String,
    override val params: P? = null,
    val id: JsonRpcID
) : ClientJsonRpc<P>(method, params)

data class NotificationJsonRpc<P>(
    override val method: String,
    override val params: P? = null
) : ClientJsonRpc<P>(method, params)

sealed class ServerJsonRpc<R, E>(
    open val id: JsonRpcID
) : JsonRpc()

data class ResultJsonRpc<R>(
    val result: R,
    override val id: JsonRpcID
) : ServerJsonRpc<R, Any>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified E> withErrorType() = this as ServerJsonRpc<R, E>
}

data class ErrorJsonRpc<E>(
    val error: JsonRpcErrorObject<E>,
    override val id: JsonRpcID
) : ServerJsonRpc<Any, E>(id) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified R> withResultType() = this as ServerJsonRpc<R, E>
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

sealed class ParsingResult<T> {
    companion object {
        fun <T : ClientJsonRpc<P>, P> valid(message: T): ParsingResult<T> = ParsingSuccess(message)

        @Suppress("UNCHECKED_CAST")
        fun <P> error(error: ParsingError): ParsingResult<P> =
            error as ParsingResult<P>
    }
}

data class ParsingSuccess<T : ClientJsonRpc<P>, P> internal constructor(
    val message: T
) : ParsingResult<T>()

sealed class ParsingError(
    open val details: String?,
    open val id: JsonRpcID
) : ParsingResult<Any>() {
    val stringErrorJson: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JSON.stringify(StringErrorJsonRpcSerialSaver, ErrorJsonRpc(this.toErrorObject(), id))
    }

    fun toErrorObject(): JsonRpcErrorObject<String> = when (this) {
        is ParseError -> JsonRpcErrorObject.parseError(details)
        is InvalidRequest -> JsonRpcErrorObject.invalidRequest(details)
        is InvalidParams -> JsonRpcErrorObject.invalidParams(details)
        is InternalError -> JsonRpcErrorObject.internalError(details)
    }
}

data class ParseError(
    override val details: String?
) : ParsingError(details, JsonRpcNullID)

data class InvalidRequest(
    override val details: String?,
    override val id: JsonRpcID
) : ParsingError(details, id)

data class InvalidParams(
    override val details: String?,
    override val id: JsonRpcID
) : ParsingError(details, id)

data class InternalError(
    override val details: String?
) : ParsingError(details, JsonRpcNullID)

