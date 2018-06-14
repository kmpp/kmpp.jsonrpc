package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UpdateNotSupportedException
import kotlinx.serialization.internal.SerialClassDescImpl

private val serialClassDesc = SerialClassDescImpl("com.github.kmpp.jsonrpc.ServerJsonRpc")
    .apply {
        addElement("jsonrpc")
        addElement("result")
        addElement("error")
        addElement("id")
    }

class ServerJsonRpcSerialSaver<R, E>(
    private val resultSaver: KSerialSaver<R>,
    private val errorSaver: JsonRpcErrorObjectSerialSaver<E>
) : KSerialSaver<ServerJsonRpc<R, E>> {
    override fun save(output: KOutput, obj: ServerJsonRpc<R, E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        when (obj) {
            is ResultJsonRpc<R> -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 1, resultSaver, obj.result
                )
            }
            is ErrorJsonRpc<E> -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 2, errorSaver, obj.error
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

object ServerJsonRpcSerialLoader : KSerialLoader<ServerJsonRpc<JsonElement, JsonElement>> {
    override fun load(input: KInput): ServerJsonRpc<JsonElement, JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val id = tree.getAsValue("id").readJsonRpcID()

        return when {
            "result" in tree -> ResultJsonRpc(tree["result"]!!, id).withErrorType()
            "error" in tree -> {
                val errorObjectTree = tree.getRequired<JsonObject>(JsonObject::getAsObject, "error")
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

    fun <R, E> withResultAndErrorLoaders(
        resultLoader: KSerialLoader<R>,
        errorLoader: KSerialLoader<E>
    ): KSerialLoader<ServerJsonRpc<R, E>> = ServerJsonRpcSerialLoaderForResultAndErrorTypes(
        resultLoader,
        errorLoader
    )
}

internal class ServerJsonRpcSerialLoaderForResultAndErrorTypes<R, E>(
    private val resultConverter: (JsonElement) -> R,
    private val errorDataConverter: (JsonElement) -> E
) : KSerialLoader<ServerJsonRpc<R, E>> {
    constructor(resultLoader: KSerialLoader<R>, errorDataLoader: KSerialLoader<E>) : this(
        { jsonElement -> JsonTreeMapper().readTree(jsonElement, resultLoader) },
        { jsonElement -> JsonTreeMapper().readTree(jsonElement, errorDataLoader) }
    )

    override fun load(input: KInput): ServerJsonRpc<R, E> {
        val untyped = ServerJsonRpcSerialLoader.load(input)
        return when (untyped) {
            is ResultJsonRpc<JsonElement> -> {
                @Suppress("UNCHECKED_CAST")
                ResultJsonRpc(resultConverter(untyped.result), untyped.id) as ServerJsonRpc<R, E>
            }
            is ErrorJsonRpc<JsonElement> -> {
                val untypedErr = untyped.error
                val error = JsonRpcErrorObject(
                    untypedErr.code,
                    untypedErr.message,
                    untypedErr.data?.let(errorDataConverter)
                )
                @Suppress("UNCHECKED_CAST")
                ErrorJsonRpc(error, untyped.id) as ServerJsonRpc<R, E>
            }
        }
    }

    override fun update(input: KInput, old: ServerJsonRpc<R, E>): ServerJsonRpc<R, E> =
        throw UpdateNotSupportedException("Update not supported")
}

/*
class ServerJsonRpcKSerializer<R, E>(
    private val resultSerializer: KSerializer<R>,
    private val errorSerializer: JsonRpcErrorObjectKSerializer<E>
) : KSerializer<ServerJsonRpc<R, E>> {
    override fun save(output: KOutput, obj: ServerJsonRpc<R, E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        when (obj) {
            is ResultJsonRpc<R> -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 1, resultSerializer, obj.result
                )
            }
            is ErrorJsonRpc<E> -> {
                output.writeSerializableElementValue(
                    serialClassDesc, 2, errorSerializer, obj.error
                )
            }
        }
        output.writeSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer, obj.id
        )

        output.writeEnd(serialClassDesc)
    }

    override fun load(input: KInput): ServerJsonRpc<R, E> {
        @Suppress("NAME_SHADOWING")
        val input = input.readBegin(serialClassDesc)

        var readElement = input.readElement(serialClassDesc)
        check(readElement == 0) { "'jsonrpc' read element $readElement, should be 0" }
        val jsonrpc = input.readStringElementValue(serialClassDesc, 0)
        readElement = input.readElement(serialClassDesc)
        val (result, error) = when (readElement) {
            1 -> input.readSerializableElementValue(serialClassDesc, 1, resultSerializer) to null
            2 -> null to input.readSerializableElementValue(serialClassDesc, 2, errorSerializer)
            else -> throw IllegalStateException(
                "'result' or 'error' read " +
                        "element $readElement should be 1 or 2"
            )
        }
        readElement = input.readElement(serialClassDesc)
        check(readElement == 3) { "'id' read element $readElement should be 3" }
        val id = input.readSerializableElementValue(
            serialClassDesc, 3,
            JsonRpcIDSerializer
        )

        input.readEnd(serialClassDesc)

        @Suppress("UNCHECKED_CAST")
        return result?.let {
            ResultJsonRpc(
                result,
                id,
                jsonrpc
            ) as ServerJsonRpc<R, E>
        }
                ?: error?.let {
                    ErrorJsonRpc(
                        error,
                        id,
                        jsonrpc
                    ) as ServerJsonRpc<R, E>
                }
                ?: throw IllegalStateException("'result' or 'error' should be non-null")
    }

    override val serialClassDesc =
        SerialClassDescImpl("com.blubber.message.ServerJsonRpc")
            .apply {
                addElement("jsonrpc")
                addElement("result")
                addElement("error")
                addElement("id")
            }
}

object ResponseJsonRpcSerializer : JsonRpcSerializer<ServerJsonRpc<String, String>>(
    ServerJsonRpcKSerializer(
        StringSerializer,
        JsonRpcErrorObjectSerializer
    ),
    postStringify = { json ->
        when (this) {
            is ResultJsonRpc<String> -> json.unstringify(this.result)
            is ErrorJsonRpc<String> ->
                json.unstringify(JSON.stringify(JsonRpcErrorObjectSerializer, this.error))
        }
    },
    preParse = { prestringify("result").prestringify("data") },
    postParse = { json -> substituteNumericIDIfPresentIn(json) }
)

private fun <R, E> ServerJsonRpc<R, E>.substituteNumericIDIfPresentIn(rawJson: String):
        ServerJsonRpc<R, E> {
    val id = when (this) { // Get value from the data class val to allow smart casts
        is ResultJsonRpc -> this.id
        is ErrorJsonRpc -> this.id
    }

    return if (id !is JsonRpcStringID || !id.id.isJsonInteger()) {
        this
    } else {
        rawJson.getRawID().toLongOrNull()?.let { rawId ->
            this.copyWithId(id = JsonRpcNumberID(rawId))
        } ?: this
    }
}
*/
