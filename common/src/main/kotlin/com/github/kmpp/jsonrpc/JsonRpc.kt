package com.github.kmpp.jsonrpc

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
    internal inline fun <reified E> withErrorType() = this as ServerJsonRpc<R, E>
}

data class ErrorJsonRpc<E>(
    val error: JsonRpcErrorObject<E>,
    override val id: JsonRpcID
) : ServerJsonRpc<Any, E>(id) {
    @Suppress("UNCHECKED_CAST")
    internal inline fun <reified R> withResultType() = this as ServerJsonRpc<R, E>
}

data class JsonRpcErrorObject<E>(
    val code: Int,
    val message: String,
    val data: E? = null
) {
    init {
        checkErrorCodeAgainstMessage()
    }

    private fun checkErrorCodeAgainstMessage() {
        val expected = when (code) {
            -32700 -> "Parse error"
            -32600 -> "Invalid Request"
            -32601 -> "Method not found"
            -32602 -> "Invalid params"
            -32603 -> "Internal error"
            in (-32099..-32000) -> "Server error"
            in (-32768..-32000) -> throw IllegalArgumentException("Reserved code: $code")
            else -> null
        }
        expected?.let {
            require(message == it) {
                "For code=$code expected message=$it but had message=$message"
            }
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
