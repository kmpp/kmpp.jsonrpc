package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.internal.SerialClassDescImpl

private val serialClassDesc: KSerialClassDesc =
    SerialClassDescImpl("com.github.kmpp.jsonrpc.ClientJsonRpc").apply {
        addElement("jsonrpc")
        addElement("method")
        addElement("params")
        addElement("id")
    }

class ClientJsonRpcSerialSaver<P>(
    private val paramsSaver: KSerialSaver<P>
) : KSerialSaver<ClientJsonRpc<P>> {
    override fun save(output: KOutput, obj: ClientJsonRpc<P>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeStringElementValue(serialClassDesc, 1, obj.method)
        obj.params?.let { tParams ->
            output.writeSerializableElementValue(serialClassDesc, 2, paramsSaver, tParams)
        }
        if (obj is RequestJsonRpc<*>) {
            output.writeSerializableElementValue(
                serialClassDesc, 3,
                JsonRpcIDSerializer, obj.id
            )
        }

        output.writeEnd(serialClassDesc)
    }
}

object ClientJsonRpcSerialLoader : KSerialLoader<ClientJsonRpc<JsonElement>> {
    override fun load(input: KInput): ClientJsonRpc<JsonElement> {
        val tree = try {
            input.to<JsonObject>()
        } catch (e: Exception) {
            throw JsonParseException("Error parsing JSON to tree: ${e.message}")
        }

        val method = try {
            tree.checkJsonrpc()
            tree.getRequired<JsonString>(JsonObject::getAsValue, "method").str
        } catch (e: Exception) {
            throw InvalidRequestException("Invalid JSON-RPC Request: ${e.message}")
        }

        val paramsElement = tree["params"]
        val params = paramsElement?.let { elem ->
            when (elem) {
                is JsonObject, is JsonArray -> elem
                else -> throw InvalidRequestException(
                    "\"params\" must be omitted or contain JSON Object or Array"
                )
            }
        }

        return if ("id" in tree) {
            val id = try {
                tree.getAsValue("id").readJsonRpcID()
            } catch (e: Exception) {
                throw InvalidRequestException("Invalid JSON-RPC id ${tree["id"]}: ${e.message}")
            }
            RequestJsonRpc(method, params, id)
        } else {
            NotificationJsonRpc(method, params)
        }
    }

    override fun update(
        input: KInput,
        old: ClientJsonRpc<JsonElement>
    ): ClientJsonRpc<JsonElement> = throw UnsupportedOperationException("Update not supported")

    fun <P> withParamsConverter(converter: (JsonElement) -> P): KSerialLoader<ClientJsonRpc<P>> =
        ClientJsonRpcSerialLoaderForParamsType(converter)
}

fun loadClientJsonRpc(json: String): ClientJsonRpcResult<JsonElement> {
    return try {
        ClientJsonRpcResult.valid(JSON.parse(ClientJsonRpcSerialLoader, json))
    } catch (e: Exception) {
        ClientJsonRpcResult.error(convertToError(e))
    }
}

private fun convertToError(e: Exception): ClientJsonRpcError {
    return when (e) {
        is JsonParseException -> ParseError(e.message)
        is InvalidRequestException -> InvalidRequest(e.message)
        is InvalidParamsException -> InvalidParams(e.message)
        else -> InternalError(e.message)
    }
}

fun <P> convertParams(
    obj: ClientJsonRpc<JsonElement>,
    converter: (JsonElement) -> P
): ClientJsonRpcResult<P> {
    return try {
        val loaded = tryConvertParams(converter, obj)
        ClientJsonRpcResult.valid(loaded)
    } catch (e: InvalidParamsException) {
        ClientJsonRpcResult.error(InvalidParams(e.message))
    }
}

private fun <P> tryConvertParams(
    converter: (JsonElement) -> P,
    obj: ClientJsonRpc<JsonElement>
): ClientJsonRpc<P> {
    val params = obj.params?.let { jsonElement ->
        try {
            converter(jsonElement)
        } catch (e: Exception) {
            throw InvalidParamsException("Unable to load params: ${e.message}")
        }
    }

    return when (obj) {
        is RequestJsonRpc -> RequestJsonRpc(obj.method, params, obj.id)
        is NotificationJsonRpc -> NotificationJsonRpc(obj.method, params)
    }
}

private class ClientJsonRpcSerialLoaderForParamsType<P>(
    private val paramsConverter: (JsonElement) -> P
) : KSerialLoader<ClientJsonRpc<P>> {

    override fun load(input: KInput): ClientJsonRpc<P> {
        val untyped = ClientJsonRpcSerialLoader.load(input)
        return tryConvertParams(paramsConverter, untyped)
    }

    override fun update(input: KInput, old: ClientJsonRpc<P>): ClientJsonRpc<P> =
        throw UnsupportedOperationException("Update not supported")
}
