package com.github.kmpp.jsonrpc

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

private val serialClassDesc = SerialClassDescImpl("com.github.kmpp.jsonrpc.ServerJsonRpc")
    .apply {
        addElement("jsonrpc")
        addElement("result")
        addElement("error")
        addElement("id")
    }

object StringErrorJsonRpcSerialSaver :
    KSerialSaver<ErrorJsonRpc<String>> by ErrorJsonRpcSerialSaver(StringSerializer)

class ErrorJsonRpcSerialSaver<E>(errorDataSaver: KSerialSaver<E>) : KSerialSaver<ErrorJsonRpc<E>> {
    private val errorObjectSaver = JsonRpcErrorObjectSerialSaver(errorDataSaver)

    override fun save(output: KOutput, obj: ErrorJsonRpc<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)
        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeSerializableElementValue(serialClassDesc, 2, errorObjectSaver, obj.error)
        output.writeSerializableElementValue(serialClassDesc, 3, JsonRpcIDSerializer, obj.id)
        output.writeEnd(serialClassDesc)
    }
}

class ResultJsonRpcSerialSaver<R>(
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

object ServerJsonRpcSerialLoader : KSerialLoader<ServerJsonRpc<JsonElement, JsonElement>> {
    override fun load(input: KInput): ServerJsonRpc<JsonElement, JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val id = tree.parseJsonRpcID()

        return when {
            "result" in tree -> ResultJsonRpc(tree["result"]!!, id).withErrorType()
            "error" in tree -> {
                val errorObjectTree = tree.getRequired<JsonObject>("error", JsonObject::getAsObject)
                val error = errorObjectTree.toJsonRpcErrorObject()
                ErrorJsonRpc(error, id).withResultType()
            }
            else -> throw SerializationException("$tree did not contain \"result\" or \"error\"")
        }
    }

    override fun update(
        input: KInput,
        old: ServerJsonRpc<JsonElement, JsonElement>
    ): ServerJsonRpc<JsonElement, JsonElement> =
        throw UpdateNotSupportedException("Update not supported")

    fun <R, E> withParsers(
        resultParser: (JsonElement) -> R,
        errorDataParser: (JsonElement) -> E
    ): KSerialLoader<ServerJsonRpc<R, E>> = ServerJsonRpcSerialLoaderForResultAndErrorTypes(
        resultParser,
        errorDataParser
    )
}

internal class ServerJsonRpcSerialLoaderForResultAndErrorTypes<R, E>(
    private val resultParser: (JsonElement) -> R,
    private val errorDataParser: (JsonElement) -> E
) : KSerialLoader<ServerJsonRpc<R, E>> {

    override fun load(input: KInput): ServerJsonRpc<R, E> {
        val untyped = ServerJsonRpcSerialLoader.load(input)
        return when (untyped) {
            is ResultJsonRpc<JsonElement> -> {
                @Suppress("UNCHECKED_CAST")
                ResultJsonRpc(resultParser(untyped.result), untyped.id) as ServerJsonRpc<R, E>
            }
            is ErrorJsonRpc<JsonElement> -> {
                val untypedErr = untyped.error
                val error = JsonRpcErrorObject(
                    untypedErr.code,
                    untypedErr.message,
                    untypedErr.data?.let(errorDataParser)
                )
                @Suppress("UNCHECKED_CAST")
                ErrorJsonRpc(error, untyped.id) as ServerJsonRpc<R, E>
            }
        }
    }

    override fun update(input: KInput, old: ServerJsonRpc<R, E>): ServerJsonRpc<R, E> =
        throw UpdateNotSupportedException("Update not supported")
}
