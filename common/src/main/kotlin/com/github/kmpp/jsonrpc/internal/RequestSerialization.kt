package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ClientRequest
import com.github.kmpp.jsonrpc.InternalError
import com.github.kmpp.jsonrpc.InvalidParams
import com.github.kmpp.jsonrpc.InvalidRequest
import com.github.kmpp.jsonrpc.JsonRpcNullID
import com.github.kmpp.jsonrpc.Notification
import com.github.kmpp.jsonrpc.ParseError
import com.github.kmpp.jsonrpc.ReadError
import com.github.kmpp.jsonrpc.ReadOutcome
import com.github.kmpp.jsonrpc.Request
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
    SerialClassDescImpl("com.github.kmpp.jsonrpc.Request").apply {
        addElement("jsonrpc")
        addElement("method")
        addElement("params")
        addElement("id")
    }

internal class RequestSaver<P>(
    private val paramsSaver: KSerialSaver<P>
) : KSerialSaver<Request<P>> {
    override fun save(output: KOutput, obj: Request<P>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeStringElementValue(serialClassDesc, 1, obj.method)
        obj.params?.let {
            output.writeSerializableElementValue(serialClassDesc, 2, paramsSaver, it)
        }
        if (obj is ClientRequest<P>) {
            output.writeSerializableElementValue(
                serialClassDesc, 3,
                JsonRpcIDSerializer, obj.id
            )
        }

        output.writeEnd(serialClassDesc)
    }
}

internal object RequestLoader : KSerialLoader<Request<JsonElement>> {
    override fun load(input: KInput): Request<JsonElement> {
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

        return id?.let { ClientRequest(method, params, id) }
                ?: Notification(method, params)
    }

    override fun update(input: KInput, old: Request<JsonElement>): Request<JsonElement> =
        throw UnsupportedOperationException("Update not supported")

    internal fun <P> withParamsReader(reader: (JsonElement) -> P): KSerialLoader<Request<P>> =
        RequestLoaderWithParamsReader(reader)
}

internal fun <I : Request<JsonElement>, O : Request<P>, P> I.parseParams(
    parser: (JsonElement) -> P,
    toType: I.(P?) -> O
): ReadOutcome<O> {
    return try {
        ReadOutcome.validRequest(tryParseParams(parser, toType))
    } catch (e: InvalidParamsException) {
        ReadOutcome.error(InvalidParams(e.message, e.id))
    }
}

private fun <I : Request<JsonElement>, O : Request<P>, P> I.tryParseParams(
    parser: (JsonElement) -> P,
    toType: I.(P?) -> O
): O {
    val params = params?.let { jsonElement ->
        try {
            parser(jsonElement)
        } catch (e: Exception) {
            val id = when (this) {
                is ClientRequest<*> -> id
                is Notification<*> -> JsonRpcNullID
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

private class RequestLoaderWithParamsReader<P>(
    private val paramsParser: (JsonElement) -> P
) : KSerialLoader<Request<P>> {

    override fun load(input: KInput): Request<P> {
        val rawRequest = RequestLoader.load(input)
        return rawRequest.tryParseParams(paramsParser) { parsedParams: P? ->
            when (this) {
                is ClientRequest -> ClientRequest(
                    method,
                    parsedParams,
                    id
                )
                is Notification -> Notification(
                    method,
                    parsedParams
                )
            }
        }
    }

    override fun update(input: KInput, old: Request<P>): Request<P> =
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
