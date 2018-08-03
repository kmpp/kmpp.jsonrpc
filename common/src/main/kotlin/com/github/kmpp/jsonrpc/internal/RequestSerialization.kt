package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ClientRequest
import com.github.kmpp.jsonrpc.InternalError
import com.github.kmpp.jsonrpc.InvalidParams
import com.github.kmpp.jsonrpc.InvalidRequest
import com.github.kmpp.jsonrpc.JsonReader
import com.github.kmpp.jsonrpc.JsonRpcNullID
import com.github.kmpp.jsonrpc.JsonWriter
import com.github.kmpp.jsonrpc.Notification
import com.github.kmpp.jsonrpc.ParseError
import com.github.kmpp.jsonrpc.ReadFailure
import com.github.kmpp.jsonrpc.ReadOutcome
import com.github.kmpp.jsonrpc.ReadSuccess
import com.github.kmpp.jsonrpc.Request
import com.github.kmpp.jsonrpc.RequestError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json

private const val JSONRPC = "jsonrpc"
private const val METHOD = "method"
private const val PARAMS = "params"
private const val ID = "id"

internal object RawRequestReader : RequestReader<JsonElement>(JsonElementReader)

internal open class RequestReader<P : Any>(private val paramsReader: JsonReader<P>) :
    JsonReader<Request<P>>() {
    override fun read(json: JsonElement): Request<P> {
        val jsonObject = try {
            json.jsonObject
        } catch (e: Exception) {
            throw JsonParseException("Error parsing JSON to tree: ${e.message}")
        }

        // Try to parse the ID first so we can make a best effort to include it in error messages
        val id = jsonObject.getPrimitiveOrNull(ID)?.let { JsonRpcIDReader.read(it) }

        val method = try {
            jsonObject.checkJsonrpc()
            jsonObject.getPrimitive(METHOD).content
        } catch (e: Exception) {
            throw InvalidRequestException("Invalid JSON-RPC Request: ${e.message}", id)
        }

        val paramsElement = jsonObject.getOrNull(PARAMS)
        val params: P? = paramsElement?.let { elem ->
            when (elem) {
                is JsonObject, is JsonArray -> paramsReader.read(elem)
                else -> throw InvalidRequestException(
                    "\"params\" must be omitted or contain JSON Object or Array",
                    id
                )
            }
        }

        return id?.let { ClientRequest(method, params, id) } ?: Notification(method, params)
    }
}

internal open class RequestWriter<P : Any>(private val paramsWriter: JsonWriter<P>) :
    JsonWriter<Request<P>>() {
    override fun write(value: Request<P>) = json {
        JSONRPC to value.jsonrpc
        METHOD to value.method
        value.params?.let { PARAMS to paramsWriter.write(it) }
        if (value is ClientRequest<P>) {
            ID to JsonRpcIDWriter.write(value.id)
        }
    }
}

internal fun <I : Request<JsonElement>, O : Request<P>, P> I.parseParams(
    parser: (JsonElement) -> P,
    toType: I.(P?) -> O
): ReadOutcome<O> {
    return try {
        ReadSuccess(tryParseParams(parser, toType))
    } catch (e: InvalidParamsException) {
        ReadFailure(InvalidParams(e.message, e.id))
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

internal fun convertToError(e: Exception): RequestError {
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
