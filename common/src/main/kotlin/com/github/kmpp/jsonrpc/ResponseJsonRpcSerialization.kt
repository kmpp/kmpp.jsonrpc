package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UpdateNotSupportedException
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.internal.StringSerializer

private val serialClassDesc = SerialClassDescImpl("com.github.kmpp.jsonrpc.ResponseJsonRpc")
    .apply {
        addElement("jsonrpc")
        addElement("result")
        addElement("error")
        addElement("id")
    }

class ResultJsonRpcSaver<R>(
    private val resultSaver: KSerialSaver<R>
) : KSerialSaver<ResultJsonRpc<R>> {
    override fun save(output: KOutput, obj: ResultJsonRpc<R>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 1, resultSaver, obj.result)
        output.writeSerializableElementValue(serialClassDesc, 3, JsonRpcIDSerializer, obj.id)
        output.writeEnd(serialClassDesc)
    }
}

object StringErrorJsonRpcSerialSaver :
    KSerialSaver<ErrorJsonRpc<String>> by ErrorJsonRpcSerialSaver(StringSerializer)

class ErrorJsonRpcSerialSaver<E>(errorDataSaver: KSerialSaver<E>) : KSerialSaver<ErrorJsonRpc<E>> {
    private val errorObjectSaver = JsonRpcErrorObjectSaver(errorDataSaver)

    override fun save(output: KOutput, obj: ErrorJsonRpc<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 2, errorObjectSaver, obj.error)
        output.writeSerializableElementValue(serialClassDesc, 3, JsonRpcIDSerializer, obj.id)
        output.writeEnd(serialClassDesc)
    }
}

class ResponseJsonRpcSaver<R, E>(
    private val resultSaver: KSerialSaver<ResultJsonRpc<R>>,
    private val errorSaver: KSerialSaver<ErrorJsonRpc<E>>
) : KSerialSaver<ResponseJsonRpc<R, E>> {
    override fun save(output: KOutput, obj: ResponseJsonRpc<R, E>) {
        when (obj) {
            is ResultJsonRpc<R> -> resultSaver.save(output, obj)
            is ErrorJsonRpc<E> -> errorSaver.save(output, obj)
        }
    }
}

object ResponseJsonRpcLoader : KSerialLoader<ResponseJsonRpc<JsonElement, JsonElement>> {
    override fun load(input: KInput): ResponseJsonRpc<JsonElement, JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val id = tree.parseJsonRpcID()

        return when {
            "result" in tree -> ResultJsonRpc(tree["result"]!!, id).coerceErrorType()
            "error" in tree -> {
                val errorObjectTree = tree.getRequired<JsonObject>("error", JsonObject::getAsObject)
                val error = errorObjectTree.toJsonRpcErrorObject()
                ErrorJsonRpc(error, id).coerceResultType()
            }
            else -> throw SerializationException("$tree did not contain \"result\" or \"error\"")
        }
    }

    override fun update(
        input: KInput,
        old: ResponseJsonRpc<JsonElement, JsonElement>
    ): ResponseJsonRpc<JsonElement, JsonElement> =
        throw UpdateNotSupportedException("Update not supported")

    fun <R, E> withParsers(
        resultParser: (JsonElement) -> R,
        errorDataParser: (JsonElement) -> E
    ): KSerialLoader<ResponseJsonRpc<R, E>> = ResponseJsonRpcLoaderWithResultAndErrorParsers(
        resultParser,
        errorDataParser
    )
}

internal class ResponseJsonRpcLoaderWithResultAndErrorParsers<R, E>(
    private val resultParser: (JsonElement) -> R,
    private val errorDataParser: (JsonElement) -> E
) : KSerialLoader<ResponseJsonRpc<R, E>> {

    override fun load(input: KInput): ResponseJsonRpc<R, E> {
        val untyped = ResponseJsonRpcLoader.load(input)
        return when (untyped) {
            is ResultJsonRpc<JsonElement> -> {
                @Suppress("UNCHECKED_CAST")
                ResultJsonRpc(resultParser(untyped.result), untyped.id) as ResponseJsonRpc<R, E>
            }
            is ErrorJsonRpc<JsonElement> -> {
                val untypedErr = untyped.error
                val error = JsonRpcErrorObject(
                    untypedErr.code,
                    untypedErr.message,
                    untypedErr.data?.let(errorDataParser)
                )
                @Suppress("UNCHECKED_CAST")
                ErrorJsonRpc(error, untyped.id) as ResponseJsonRpc<R, E>
            }
        }
    }

    override fun update(input: KInput, old: ResponseJsonRpc<R, E>): ResponseJsonRpc<R, E> =
        throw UpdateNotSupportedException("Update not supported")
}

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
