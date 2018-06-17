package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ErrorJsonRpcSaver
import com.github.kmpp.jsonrpc.internal.JSON_TREE_MAPPER
import com.github.kmpp.jsonrpc.internal.RequestJsonRpcLoader
import com.github.kmpp.jsonrpc.internal.RequestJsonRpcSaver
import com.github.kmpp.jsonrpc.internal.ResponseJsonRpcLoader
import com.github.kmpp.jsonrpc.internal.ResponseJsonRpcSaver
import com.github.kmpp.jsonrpc.internal.ResultJsonRpcSaver
import com.github.kmpp.jsonrpc.internal.StringErrorJsonRpcSaver
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

typealias RawRequestJsonRpc = RequestJsonRpc<JsonElement>
typealias RawClientRequestJsonRpc = ClientRequestJsonRpc<JsonElement>
typealias RawNotificationJsonRpc = NotificationJsonRpc<JsonElement>
typealias RawResponseJsonRpc = ResponseJsonRpc<JsonElement, JsonElement>

fun <P> getRequestJsonRpcSaver(
    paramsSaver: KSerialSaver<P>
): KSerialSaver<RequestJsonRpc<P>> = RequestJsonRpcSaver(paramsSaver)

fun <P> getRequestJsonRpcLoaderWithParamsReader(
    reader: (JsonElement) -> P
) : KSerialLoader<RequestJsonRpc<P>> = RequestJsonRpcLoader.withParamsReader(reader)

fun <P> saveRequestJsonRpc(
    saver: KSerialSaver<RequestJsonRpc<P>>,
    request: RequestJsonRpc<P>
): String = JSON.stringify(saver, request)

fun loadRequestJsonRpc(json: String): ReadResult<RawRequestJsonRpc> {
    return try {
        val parsed = JSON.parse(RequestJsonRpcLoader, json)
        ReadResult.validRequest(parsed)
    } catch (e: Exception) {
        ReadResult.error(convertToError(e))
    }
}

fun <P> RawClientRequestJsonRpc.parseRequestParams(
    parser: (JsonElement) -> P
): ReadResult<ClientRequestJsonRpc<P>> = this.parseParams(parser) {
        ClientRequestJsonRpc(
            method,
            params?.let(parser),
            id
        )
    }

fun <P> RawNotificationJsonRpc.parseNotificationParams(
    parser: (JsonElement) -> P
): ReadResult<NotificationJsonRpc<P>> =
    this.parseParams(parser) {
        NotificationJsonRpc(
            method,
            params?.let(parser)
        )
    }

fun <R> getResultJsonRpcSaver(
    resultSaver: KSerialSaver<R>
): KSerialSaver<ResultJsonRpc<R>> = ResultJsonRpcSaver(resultSaver)

fun saveStringErrorJsonRpc(error: ErrorJsonRpc<String>) =
    JSON.stringify(StringErrorJsonRpcSaver, error)

fun <E> getErrorJsonRpcSaver(errorDataSaver: KSerialSaver<E>): KSerialSaver<ErrorJsonRpc<E>> =
        ErrorJsonRpcSaver(errorDataSaver)

fun <R, E> getResponseJsonRpcSaver(
    resultSaver: KSerialSaver<ResultJsonRpc<R>>,
    errorSaver: KSerialSaver<ErrorJsonRpc<E>>
): KSerialSaver<ResponseJsonRpc<R, E>> = ResponseJsonRpcSaver(resultSaver, errorSaver)

fun getResponseJsonRpcLoader(): KSerialLoader<RawResponseJsonRpc> =
    ResponseJsonRpcLoader

fun <R, E> getResponseJsonRpcLoaderWithReaders(
    resultReader: (JsonElement) -> R,
    errorDataReader: (JsonElement) -> E
): KSerialLoader<ResponseJsonRpc<R, E>> =
    ResponseJsonRpcLoader.withReaders(resultReader, errorDataReader)

fun <R, E> saveResponseJsonRpc(
    saver: KSerialSaver<ResponseJsonRpc<R, E>>,
    response: ResponseJsonRpc<R, E>
): String = JSON.stringify(saver, response)

fun <R> saveResultJsonRpc(
    saver: KSerialSaver<ResultJsonRpc<R>>,
    result: ResultJsonRpc<R>
): String = JSON.stringify(saver, result)

fun <E> saveErrorJsonRpc(
    saver: KSerialSaver<ErrorJsonRpc<E>>,
    error: ErrorJsonRpc<E>
): String = JSON.stringify(saver, error)

fun <R, E> loadResponseJsonRpc(
    loader: KSerialLoader<ResponseJsonRpc<R, E>>,
    json: String
): ResponseJsonRpc<R, E> = JSON.parse(loader, json)
