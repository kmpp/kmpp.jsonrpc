package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.Error
import com.github.kmpp.jsonrpc.ErrorObject
import com.github.kmpp.jsonrpc.Response
import com.github.kmpp.jsonrpc.Result
import com.github.kmpp.jsonrpc.getResponseLoader
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

private val serialClassDesc = SerialClassDescImpl("com.github.kmpp.jsonrpc.Response")
    .apply {
        addElement("jsonrpc")
        addElement("result")
        addElement("error")
        addElement("id")
    }

internal class ResultSaver<R>(
    private val resultSaver: KSerialSaver<R>
) : KSerialSaver<Result<R>> {
    override fun save(output: KOutput, obj: Result<R>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 1, resultSaver, obj.result)
        output.writeSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer, obj.id
        )
        output.writeEnd(serialClassDesc)
    }
}

internal object JsonResultSaver : KSerialSaver<Result<JsonElement>> by ResultSaver(JsonSaver)

internal object ErrorJsonSaver : KSerialSaver<Error<JsonElement>> by ErrorSaver(JsonSaver)

internal object StringErrorSaver :
    KSerialSaver<Error<String>> by ErrorSaver(
        StringSerializer
    )

internal class ErrorSaver<E>(
    errorDataSaver: KSerialSaver<E>
) : KSerialSaver<Error<E>> {
    private val errorObjectSaver =
        ErrorObjectSaver(errorDataSaver)

    override fun save(output: KOutput, obj: Error<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 2, errorObjectSaver, obj.error)
        output.writeSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer, obj.id
        )
        output.writeEnd(serialClassDesc)
    }
}

internal class ResponseSaver<R, E>(
    private val resultSaver: KSerialSaver<Result<R>>,
    private val errorSaver: KSerialSaver<Error<E>>
) : KSerialSaver<Response<R, E>> {
    override fun save(output: KOutput, obj: Response<R, E>) {
        when (obj) {
            is Result<R> -> resultSaver.save(output, obj)
            is Error<E> -> errorSaver.save(output, obj)
        }
    }
}

internal object ResponseLoader : KSerialLoader<Response<JsonElement, JsonElement>> {
    override fun load(input: KInput): Response<JsonElement, JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val id = tree.readJsonRpcID()

        return when {
            "result" in tree -> Result(tree["result"]!!, id).coerceErrorType()
            "error" in tree -> {
                val errorObjectTree = tree.getRequired<JsonObject>("error", JsonObject::getAsObject)
                val error = errorObjectTree.toErrorObject()
                Error(error, id).coerceResultType()
            }
            else -> throw SerializationException("$tree did not contain \"result\" or \"error\"")
        }
    }

    override fun update(
        input: KInput,
        old: Response<JsonElement, JsonElement>
    ): Response<JsonElement, JsonElement> =
        throw UpdateNotSupportedException("Update not supported")

    internal fun <R, E> withReaders(
        resultReader: (JsonElement) -> R,
        errorDataReader: (JsonElement) -> E
    ): KSerialLoader<Response<R, E>> =
        ResponseJsonRpcLoaderWithResultAndErrorParsers(
            resultReader,
            errorDataReader
        )
}

internal class ResponseJsonRpcLoaderWithResultAndErrorParsers<R, E>(
    private val resultParser: (JsonElement) -> R,
    private val errorDataParser: (JsonElement) -> E
) : KSerialLoader<Response<R, E>> {

    override fun load(input: KInput): Response<R, E> {
        val rawResponse = getResponseLoader().load(input)
        return when (rawResponse) {
            is Result<JsonElement> -> {
                @Suppress("UNCHECKED_CAST")
                Result(resultParser(rawResponse.result), rawResponse.id) as Response<R, E>
            }
            is Error<JsonElement> -> {
                val rawError = rawResponse.error
                val error = ErrorObject(
                    rawError.code,
                    rawError.message,
                    rawError.data?.let(errorDataParser)
                )
                @Suppress("UNCHECKED_CAST")
                Error(error, rawResponse.id) as Response<R, E>
            }
        }
    }

    override fun update(input: KInput, old: Response<R, E>): Response<R, E> =
        throw UpdateNotSupportedException("Update not supported")
}
