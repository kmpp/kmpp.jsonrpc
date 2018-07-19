package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.Error
import com.github.kmpp.jsonrpc.ErrorObject
import com.github.kmpp.jsonrpc.Response
import com.github.kmpp.jsonrpc.Result
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UpdateNotSupportedException
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val serialClassDesc = SerialClassDescImpl("com.github.kmpp.jsonrpc.Response")
    .apply {
        addElement("jsonrpc")
        addElement("result")
        addElement("error")
        addElement("id")
    }

internal class ResponseSaver<R>(
    private val internalResultSaver: KSerialSaver<R>
) : KSerialSaver<Response<R>> {
    override fun save(output: KOutput, obj: Response<R>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)

        when (obj) {
            is Result<R> -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 1, internalResultSaver, obj.result
                )
            }
            is Error -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 2, ErrorObjectSaver, obj.error
                )
            }
        }

        output.writeSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer, obj.id
        )

        output.writeEnd(serialClassDesc)
    }
}

internal object ErrorSaver : KSerialSaver<Error> {
    override fun save(output: KOutput, obj: Error) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 2, ErrorObjectSaver, obj.error)
        output.writeSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer, obj.id
        )
        output.writeEnd(serialClassDesc)
    }
}

internal object JsonResponseSaver : KSerialSaver<Response<JsonElement>> by ResponseSaver(JsonSaver)

internal object RawResponseLoader : KSerialLoader<Response<JsonElement>> {
    override fun load(input: KInput): Response<JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val id = tree.readJsonRpcID()

        return when {
            "result" in tree -> Result(tree["result"], id)
            "error" in tree -> {
                val errorObjectTree = tree.getObject("error")
                val error = errorObjectTree.toErrorObject()
                Error(error, id)
            }
            else -> throw SerializationException("$tree did not contain \"result\" or \"error\"")
        }
    }

    override fun update(input: KInput, old: Response<JsonElement>): Response<JsonElement> =
        throw UpdateNotSupportedException("Update not supported")
}

internal class ResponseLoader<R>(
    private val resultParser: (JsonElement) -> R
) : KSerialLoader<Response<R>> {

    override fun load(input: KInput): Response<R> {
        val rawResponse = RawResponseLoader.load(input)
        return when (rawResponse) {
            is Result<JsonElement> -> {
                Result(resultParser(rawResponse.result), rawResponse.id)
            }
            is Error -> {
                val rawError = rawResponse.error
                val error = ErrorObject(
                    rawError.code,
                    rawError.message,
                    rawError.data
                )
                Error(error, rawResponse.id)
            }
        }
    }

    override fun update(input: KInput, old: Response<R>): Response<R> =
        throw UpdateNotSupportedException("Update not supported")
}
