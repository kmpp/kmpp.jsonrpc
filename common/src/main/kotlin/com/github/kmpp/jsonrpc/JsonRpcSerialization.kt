package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ErrorSaver
import com.github.kmpp.jsonrpc.internal.JSON_TREE_MAPPER
import com.github.kmpp.jsonrpc.internal.RawRequestLoader
import com.github.kmpp.jsonrpc.internal.RequestLoader
import com.github.kmpp.jsonrpc.internal.RequestSaver
import com.github.kmpp.jsonrpc.internal.ResponseSaver
import com.github.kmpp.jsonrpc.internal.convertToError
import com.github.kmpp.jsonrpc.internal.parseParams
import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonPrimitive
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver

val <T> KSerialLoader<T>.tree: (JsonElement) -> T
    get() = { jsonElement -> JSON_TREE_MAPPER.readTree(jsonElement, this) }

val <E> ((JsonElement) -> E).array: (JsonElement) -> List<E>
    get() = { (it as JsonArray).content.map(this) }

val PrimitiveReader = { elem: JsonElement -> (elem as JsonPrimitive) }
val StringReader = { elem: JsonElement -> PrimitiveReader(elem).str }
val BooleanReader = { elem: JsonElement -> PrimitiveReader(elem).asBoolean }
val IntReader = { elem: JsonElement -> PrimitiveReader(elem).asInt }
val LongReader = { elem: JsonElement -> PrimitiveReader(elem).asLong }
val DoubleReader = { elem: JsonElement -> PrimitiveReader(elem).asDouble }

fun <P> getRequestSaver(paramsSaver: KSerialSaver<P>): KSerialSaver<Request<P>> =
    RequestSaver(paramsSaver)

fun <T> KSerialSaver<T>.save(obj: T): String = JSON.stringify(this, obj)

fun <P> getRequestLoader(reader: (JsonElement) -> P): KSerialLoader<Request<P>> =
    RequestLoader(reader)

fun <P> getRequestLoader(reader: KSerialLoader<P>): KSerialLoader<Request<P>> =
    RequestLoader(reader.tree)

fun <P> KSerialLoader<Request<P>>.load(json: String): ReadOutcome<Request<P>> {
    return try {
        val parsed = JSON.parse(this, json)
        ReadSuccess(parsed)
    } catch (e: Exception) {
        ReadFailure(convertToError(e))
    }
}

fun loadRawRequest(json: String): ReadOutcome<Request<JsonElement>> = RawRequestLoader.load(json)

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

fun <R> getResponseSaver(resultSaver: KSerialSaver<R>): KSerialSaver<Response<R>> =
    ResponseSaver(resultSaver)

fun <R> KSerialSaver<Response<R>>.save(response: Response<R>): String =
    JSON.stringify(this, response)

fun saveErrorJson(error: Error) = JSON.stringify(ErrorSaver, error)
