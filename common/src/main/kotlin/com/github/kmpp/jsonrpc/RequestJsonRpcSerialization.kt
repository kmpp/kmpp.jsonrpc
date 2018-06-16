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
    SerialClassDescImpl("com.github.kmpp.jsonrpc.RequestJsonRpc").apply {
        addElement("jsonrpc")
        addElement("method")
        addElement("params")
        addElement("id")
    }

class RequestJsonRpcSaver<P>(
    private val paramsSaver: KSerialSaver<P>
) : KSerialSaver<RequestJsonRpc<P>> {
    override fun save(output: KOutput, obj: RequestJsonRpc<P>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeStringElementValue(serialClassDesc, 1, obj.method)
        obj.params?.let {
            output.writeSerializableElementValue(serialClassDesc, 2, paramsSaver, it)
        }
        if (obj is ClientRequestJsonRpc<P>) {
            output.writeSerializableElementValue(serialClassDesc, 3, JsonRpcIDSerializer, obj.id)
        }

        output.writeEnd(serialClassDesc)
    }
}

object RequestJsonRpcLoader : KSerialLoader<RequestJsonRpc<JsonElement>> {
    override fun load(input: KInput): RequestJsonRpc<JsonElement> {
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

        return id?.let { ClientRequestJsonRpc(method, params, id) }
                ?: NotificationJsonRpc(method, params)
    }

    override fun update(
        input: KInput,
        old: RequestJsonRpc<JsonElement>
    ): RequestJsonRpc<JsonElement> = throw UnsupportedOperationException("Update not supported")

    fun <P> withParamsParser(parser: (JsonElement) -> P): KSerialLoader<RequestJsonRpc<P>> =
        RequestJsonRpcLoaderWithParamsParser(parser)
}

fun <P> saveRequestJsonRpc(saver: KSerialSaver<RequestJsonRpc<P>>, request: RequestJsonRpc<P>) =
    JSON.stringify(saver, request)

fun loadRequestJsonRpc(json: String): ParsingResult<RequestJsonRpc<JsonElement>> {
    return try {
        val parsed = JSON.parse(RequestJsonRpcLoader, json)
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

private fun CharSequence.firstLine() = this.lines().firstOrNull()

fun <P> ClientRequestJsonRpc<JsonElement>.parseRequestParams(parser: (JsonElement) -> P) =
    this.parseParams(parser) { ClientRequestJsonRpc(method, params?.let(parser), id) }

fun <P> NotificationJsonRpc<JsonElement>.parseNotificationParams(parser: (JsonElement) -> P) =
    this.parseParams(parser) { NotificationJsonRpc(method, params?.let(parser)) }

private fun <T : RequestJsonRpc<P>, P> RequestJsonRpc<JsonElement>.parseParams(
    parser: (JsonElement) -> P,
    toType: RequestJsonRpc<JsonElement>.(P?) -> T
): ParsingResult<T> {
    return try {
        ParsingResult.valid(tryParseParams(parser, toType))
    } catch (e: InvalidParamsException) {
        ParsingResult.error(InvalidParams(e.message, e.id))
    }
}

private fun <T : RequestJsonRpc<P>, P> RequestJsonRpc<JsonElement>.tryParseParams(
    parser: (JsonElement) -> P,
    toType: RequestJsonRpc<JsonElement>.(P?) -> T
): T {
    val params = params?.let { jsonElement ->
        try {
            parser(jsonElement)
        } catch (e: Exception) {
            val id = when (this) {
                is ClientRequestJsonRpc<JsonElement> -> id
                is NotificationJsonRpc<JsonElement> -> JsonRpcNullID
            }
            throw InvalidParamsException("Unable to load params: ${e.message}", id)
        }
    }

    return this.toType(params)
}

private class RequestJsonRpcLoaderWithParamsParser<P>(
    private val paramsParser: (JsonElement) -> P
) : KSerialLoader<RequestJsonRpc<P>> {

    override fun load(input: KInput): RequestJsonRpc<P> {
        val untyped = RequestJsonRpcLoader.load(input)
        return untyped.tryParseParams(paramsParser) { parsedParams: P? ->
            when (this) {
                is ClientRequestJsonRpc -> ClientRequestJsonRpc(method, parsedParams, id)
                is NotificationJsonRpc -> NotificationJsonRpc(method, parsedParams)
            }
        }
    }

    override fun update(input: KInput, old: RequestJsonRpc<P>): RequestJsonRpc<P> =
        throw UnsupportedOperationException("Update not supported")
}
