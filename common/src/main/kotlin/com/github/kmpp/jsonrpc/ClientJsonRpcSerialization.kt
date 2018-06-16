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

internal object ClientJsonRpcSerialLoader : KSerialLoader<ClientJsonRpc<JsonElement>> {
    override fun load(input: KInput): ClientJsonRpc<JsonElement> {
        val tree = try {
            input.to<JsonObject>()
        } catch (e: Exception) {
            throw JsonParseException("Error parsing JSON to tree: ${e.message}")
        }

        // Try to parse the ID first so we can make a best effort to include it in error messages
        val id = if ("id" in tree) {
            try {
                tree.parseJsonRpcID()
            } catch (e: Exception) {
                throw InvalidRequestException("Invalid id ${tree["id"]}: ${e.message}", null)
            }
        } else {
            null
        }

        val method = try {
            tree.checkJsonrpc()
            tree.getRequired<JsonString>("method", JsonObject::getAsValue).str
        } catch (e: Exception) {
            throw InvalidRequestException("Invalid JSON-RPC Request: ${e.message}", id)
        }

        val paramsElement = tree["params"]
        val params = paramsElement?.let { elem ->
            when (elem) {
                is JsonObject, is JsonArray -> elem
                else -> throw InvalidRequestException(
                    "\"params\" must be omitted or contain JSON Object or Array",
                    id
                )
            }
        }

        return id?.let { RequestJsonRpc(method, params, id) } ?: NotificationJsonRpc(method, params)
    }

    override fun update(
        input: KInput,
        old: ClientJsonRpc<JsonElement>
    ): ClientJsonRpc<JsonElement> = throw UnsupportedOperationException("Update not supported")

    fun <P> withParamsParser(parser: (JsonElement) -> P): KSerialLoader<ClientJsonRpc<P>> =
        ClientJsonRpcSerialLoaderForParams(parser)
}

fun <P> saveClientJsonRpc(saver: KSerialSaver<ClientJsonRpc<P>>, rpc: ClientJsonRpc<P>) =
    JSON.stringify(saver, rpc)

fun loadClientJsonRpc(json: String): ParsingResult<ClientJsonRpc<JsonElement>> {
    return try {
        val parsed = JSON.parse(ClientJsonRpcSerialLoader, json)
        ParsingResult.valid(parsed)
    } catch (e: Exception) {
        ParsingResult.error(convertToError(e))
    }
}

private fun convertToError(e: Exception): ParsingError {
    return when (e) {
        is JsonParseException -> ParseError(e.message?.firstLine())
        is InvalidRequestException -> InvalidRequest(
            e.message?.firstLine(),
            e.id ?: JsonRpcNullID
        )
        is InvalidParamsException -> InvalidParams(e.message?.firstLine(), e.id)
        else -> InternalError(e.message?.firstLine())
    }
}

private fun CharSequence.firstLine() = this.lines().first()

fun <P> RequestJsonRpc<JsonElement>.parseRequestParams(
    parser: (JsonElement) -> P,
    toType: RequestJsonRpc<JsonElement>.(P?) -> RequestJsonRpc<P>
): ParsingResult<RequestJsonRpc<P>> = parseParams(parser) { parsedParams: P? ->
    toType(parsedParams)
}

fun <P> NotificationJsonRpc<JsonElement>.parseNotificationParams(
    parser: (JsonElement) -> P,
    toType: NotificationJsonRpc<JsonElement>.(P?) -> NotificationJsonRpc<P>
): ParsingResult<NotificationJsonRpc<P>> = parseParams(parser) { parsedParams: P? ->
    toType(parsedParams)
}


fun <T : ClientJsonRpc<P>, P> ClientJsonRpc<JsonElement>.parseParams(
    parser: (JsonElement) -> P,
    toType: ClientJsonRpc<JsonElement>.(P?) -> T
): ParsingResult<T> {
    return try {
        ParsingResult.valid(tryParseParams(this, parser, toType))
    } catch (e: InvalidParamsException) {
        ParsingResult.error(InvalidParams(e.message, e.id))
    }
}

private fun <T : ClientJsonRpc<P>, P> tryParseParams(
    obj: ClientJsonRpc<JsonElement>,
    parser: (JsonElement) -> P,
    toType: ClientJsonRpc<JsonElement>.(P?) -> T
): T {
    val params = obj.params?.let { jsonElement ->
        try {
            parser(jsonElement)
        } catch (e: Exception) {
            val id = when (obj) {
                is RequestJsonRpc<JsonElement> -> obj.id
                is NotificationJsonRpc<JsonElement> -> JsonRpcNullID
            }
            throw InvalidParamsException("Unable to load params: ${e.message}", id)
        }
    }

    return obj.toType(params)
}

private class ClientJsonRpcSerialLoaderForParams<P>(
    private val paramsParser: (JsonElement) -> P
) : KSerialLoader<ClientJsonRpc<P>> {

    override fun load(input: KInput): ClientJsonRpc<P> {
        val untyped = ClientJsonRpcSerialLoader.load(input)
        return tryParseParams(untyped, paramsParser) { parsedParams: P? ->
            when (this) {
                is RequestJsonRpc -> RequestJsonRpc(method, parsedParams, id)
                is NotificationJsonRpc -> NotificationJsonRpc(method, parsedParams)
            }
        }
    }

    override fun update(input: KInput, old: ClientJsonRpc<P>): ClientJsonRpc<P> =
        throw UnsupportedOperationException("Update not supported")
}
