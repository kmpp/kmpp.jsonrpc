package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.RawRequestReader
import com.github.kmpp.jsonrpc.internal.convertToError
import com.github.kmpp.jsonrpc.internal.parseParams
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTreeParser

val <E> ((JsonElement) -> E).array: (JsonElement) -> List<E>
    get() = { (it as JsonArray).content.map(this) }

val PrimitiveReader = { elem: JsonElement -> (elem.primitive) }
val StringReader = { elem: JsonElement -> PrimitiveReader(elem).content }
val BooleanReader = { elem: JsonElement -> PrimitiveReader(elem).boolean }
val IntReader = { elem: JsonElement -> PrimitiveReader(elem).int }
val LongReader = { elem: JsonElement -> PrimitiveReader(elem).long }
val DoubleReader = { elem: JsonElement -> PrimitiveReader(elem).double }

fun <P> Request<JsonElement>.parseParams(parser: (JsonElement) -> P): ReadOutcome<Request<P>> {
    return when (this) {
        is ClientRequest<JsonElement> -> this.parseParams(parser) {
            ClientRequest(method, params?.let(parser), id)
        }
        is Notification<JsonElement> -> this.parseParams(parser) {
            Notification(method, params?.let(parser))
        }
    }
}

internal fun <P : Any> JsonReader<Request<P>>.readOutcome(json: String): ReadOutcome<Request<P>> {
    return try {
        val parsed = JsonTreeParser(json).readFully()
        val request = read(parsed)
        ReadSuccess(request)
    } catch (e: Exception) {
        ReadFailure(convertToError(e))
    }
}

fun loadRawRequest(json: String): ReadOutcome<Request<JsonElement>> =
    RawRequestReader.readOutcome(json)
