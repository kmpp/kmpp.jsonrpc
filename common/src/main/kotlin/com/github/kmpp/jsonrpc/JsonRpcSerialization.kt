package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.JSON_TREE_MAPPER
import com.github.kmpp.jsonrpc.internal.RequestJsonRpcLoader
import com.github.kmpp.jsonrpc.internal.RequestJsonRpcSaver
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

@PublishedApi
internal fun <P> getRequestJsonRpcSaver(
    paramsSaver: KSerialSaver<P>
): KSerialSaver<RequestJsonRpc<P>> = RequestJsonRpcSaver(paramsSaver)

fun getRequestJsonRpcLoader(): KSerialLoader<RequestJsonRpc<JsonElement>> = RequestJsonRpcLoader

fun <P> getRequestJsonRpcLoaderWithParamsReader(
    reader: (JsonElement) -> P
) : KSerialLoader<RequestJsonRpc<P>> = RequestJsonRpcLoader.withParamsReader(reader)

fun <P> saveRequestJsonRpc(
    saver: KSerialSaver<RequestJsonRpc<P>>,
    request: RequestJsonRpc<P>
): String = JSON.stringify(saver, request)

fun loadRequestJsonRpc(json: String): ReadResult<RequestJsonRpc<JsonElement>> {
    return try {
        val parsed = JSON.parse(RequestJsonRpcLoader, json)
        ReadResult.validRequest(parsed)
    } catch (e: Exception) {
        ReadResult.error(convertToError(e))
    }
}

fun <P> ClientRequestJsonRpc<JsonElement>.parseRequestParams(
    parser: (JsonElement) -> P
): ReadResult<ClientRequestJsonRpc<P>> = this.parseParams(parser) {
        ClientRequestJsonRpc(
            method,
            params?.let(parser),
            id
        )
    }

fun <P> NotificationJsonRpc<JsonElement>.parseNotificationParams(
    parser: (JsonElement) -> P
): ReadResult<NotificationJsonRpc<P>> =
    this.parseParams(parser) {
        NotificationJsonRpc(
            method,
            params?.let(parser)
        )
    }
