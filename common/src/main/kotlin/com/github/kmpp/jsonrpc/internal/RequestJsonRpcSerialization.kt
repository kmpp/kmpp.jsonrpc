package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.*
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

internal class RequestJsonRpcSaver<P>(
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
            output.writeSerializableElementValue(
                serialClassDesc, 3,
                JsonRpcIDSerializer, obj.id
            )
        }

        output.writeEnd(serialClassDesc)
    }
}

internal object RequestJsonRpcLoader : KSerialLoader<RequestJsonRpc<JsonElement>> {
    override fun load(input: KInput): RequestJsonRpc<JsonElement> {
        val tree = try {
            input.to<JsonObject>()
        } catch (e: Exception) {
            throw JsonParseException("Error parsing JSON to tree: ${e.message}")
        }

        // Try to parse the ID first so we can make a best effort to include it in error messages
        val id = if ("id" in tree) {
            try {
                tree.readJsonRpcID()
            } catch (e: Exception) {
                throw InvalidRequestException(
                    "Invalid id ${tree["id"]}: ${e.message}",
                    null
                )
            }
        } else {
            null
        }

        val method = try {
            tree.checkJsonrpc()
            tree.getRequired<JsonString>("method", JsonObject::getAsValue).str
        } catch (e: Exception) {
            throw InvalidRequestException(
                "Invalid JSON-RPC Request: ${e.message}",
                id
            )
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

    internal fun <P> withParamsReader(parser: (JsonElement) -> P):
            KSerialLoader<RequestJsonRpc<P>> =
        RequestJsonRpcLoaderWithParamsParser(parser)
}

internal fun <I : RequestJsonRpc<JsonElement>, O : RequestJsonRpc<P>, P> I.parseParams(
    parser: (JsonElement) -> P,
    toType: I.(P?) -> O
): ReadResult<O> {
    return try {
        ReadResult.validRequest(tryParseParams(parser, toType))
    } catch (e: InvalidParamsException) {
        ReadResult.error(InvalidParams(e.message, e.id))
    }
}

private fun <I : RequestJsonRpc<JsonElement>, O : RequestJsonRpc<P>, P> I.tryParseParams(
    parser: (JsonElement) -> P,
    toType: I.(P?) -> O
): O {
    val params = params?.let { jsonElement ->
        try {
            parser(jsonElement)
        } catch (e: Exception) {
            val id = when (this) {
                is ClientRequestJsonRpc<*> -> id
                is NotificationJsonRpc<*> -> JsonRpcNullID
                else -> throw IllegalStateException("Unexpected input type ${this::class}")
            }
            throw InvalidParamsException(
                "Unable to load params: ${e.message}",
                id
            )
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
                is ClientRequestJsonRpc -> ClientRequestJsonRpc(
                    method,
                    parsedParams,
                    id
                )
                is NotificationJsonRpc -> NotificationJsonRpc(
                    method,
                    parsedParams
                )
            }
        }
    }

    override fun update(input: KInput, old: RequestJsonRpc<P>): RequestJsonRpc<P> =
        throw UnsupportedOperationException("Update not supported")
}

internal fun convertToError(e: Exception): ReadError {
    return when (e) {
        is JsonParseException -> ParseError(e.message?.firstLine())
        is InvalidRequestException -> InvalidRequest(
            e.message?.firstLine(),
            e.id ?: JsonRpcNullID
        )
        is InvalidParamsException -> InvalidParams(
            e.message?.firstLine(),
            e.id
        )
        else -> InternalError(e.message?.firstLine())
    }
}

private fun CharSequence.firstLine() = this.lines().firstOrNull()
