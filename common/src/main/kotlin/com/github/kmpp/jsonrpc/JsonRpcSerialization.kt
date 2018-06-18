package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ErrorJsonSaver
import com.github.kmpp.jsonrpc.internal.ErrorSaver
import com.github.kmpp.jsonrpc.internal.JSON_TREE_MAPPER
import com.github.kmpp.jsonrpc.internal.JsonResultSaver
import com.github.kmpp.jsonrpc.internal.RequestLoader
import com.github.kmpp.jsonrpc.internal.RequestSaver
import com.github.kmpp.jsonrpc.internal.ResponseLoader
import com.github.kmpp.jsonrpc.internal.ResponseSaver
import com.github.kmpp.jsonrpc.internal.ResultSaver
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

fun <P> getRequestLoaderWithParamsReader(reader: (JsonElement) -> P): KSerialLoader<Request<P>> =
    RequestLoader.withParamsReader(reader)

fun <P> saveRequest(saver: KSerialSaver<Request<P>>, request: Request<P>): String =
    JSON.stringify(saver, request)

fun loadRequest(json: String): ReadOutcome<Request<JsonElement>> {
    return try {
        val parsed = JSON.parse(RequestLoader, json)
        ReadOutcome.validRequest(parsed)
    } catch (e: Exception) {
        ReadOutcome.error(convertToError(e))
    }
}

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

fun <R> getResultSaver(resultSaver: KSerialSaver<R>): KSerialSaver<Result<R>> =
    ResultSaver(resultSaver)

fun saveErrorJson(error: Error<JsonElement>) = JSON.stringify(ErrorJsonSaver, error)

fun <E> getErrorSaver(errorDataSaver: KSerialSaver<E>): KSerialSaver<Error<E>> =
    ErrorSaver(errorDataSaver)

fun <R, E> getResponseSaver(
    resultSaver: KSerialSaver<Result<R>>,
    errorSaver: KSerialSaver<Error<E>>
): KSerialSaver<Response<R, E>> = ResponseSaver(resultSaver, errorSaver)

fun getResponseLoader(): KSerialLoader<Response<JsonElement, JsonElement>> = ResponseLoader

fun <R, E> getResponseLoaderWithReaders(
    resultReader: (JsonElement) -> R,
    errorDataReader: (JsonElement) -> E
): KSerialLoader<Response<R, E>> = ResponseLoader.withReaders(resultReader, errorDataReader)

fun <R, E> saveResponse(saver: KSerialSaver<Response<R, E>>, response: Response<R, E>): String =
    JSON.stringify(saver, response)

fun <R> saveResult(saver: KSerialSaver<Result<R>>, result: Result<R>): String =
    JSON.stringify(saver, result)

fun saveResultJson(result: Result<JsonElement>): String = saveResult(JsonResultSaver, result)

fun <E> saveError(saver: KSerialSaver<Error<E>>, error: Error<E>): String =
    JSON.stringify(saver, error)

fun <R, E> loadResponse(loader: KSerialLoader<Response<R, E>>, json: String): Response<R, E> =
    JSON.parse(loader, json)
