package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonLiteral
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UpdateNotSupportedException
import kotlinx.serialization.internal.SerialClassDescImpl

private val serialClassDesc: KSerialClassDesc =
    SerialClassDescImpl("com.github.kmpp.JsonRpcErrorObject").apply {
        addElement("code")
        addElement("message")
        addElement("data")
    }

class JsonRpcErrorObjectSerialSaver<E>(
    private val eSaver: KSerialSaver<E>
) : KSerialSaver<JsonRpcErrorObject<E>> {
    override fun save(output: KOutput, obj: JsonRpcErrorObject<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeIntElementValue(serialClassDesc, 0, obj.code)
        output.writeStringElementValue(serialClassDesc, 1, obj.message)
        obj.data?.let { output.writeSerializableElementValue(serialClassDesc, 2, eSaver, it) }

        output.writeEnd(serialClassDesc)
    }
}

object JsonRpcErrorObjectSerialLoader : KSerialLoader<JsonRpcErrorObject<JsonElement>> {
    override fun load(input: KInput): JsonRpcErrorObject<JsonElement> {
        val tree = input.to<JsonObject>()

        return tree.toJsonRpcErrorObject()
    }

    override fun update(
        input: KInput,
        old: JsonRpcErrorObject<JsonElement>
    ): JsonRpcErrorObject<JsonElement> = throw UpdateNotSupportedException("Update not supported")

    fun <E> withDataLoader(loader: KSerialLoader<E>): KSerialLoader<JsonRpcErrorObject<E>> =
        JsonRpcErrorObjectSerialLoaderForDataType(loader)
}

internal fun JsonObject.toJsonRpcErrorObject(): JsonRpcErrorObject<JsonElement> {
    val codeElem = this.getRequired<JsonLiteral>(JsonObject::getAsValue, "code")
    val code = try {
        codeElem.asInt
    } catch (e: NumberFormatException) {
        throw SerializationException(e.toString())
    }

    val message = this.getRequired<JsonString>(JsonObject::getAsValue, "message").str

    val data = this["data"]

    return JsonRpcErrorObject(code, message, data)
}

internal class JsonRpcErrorObjectSerialLoaderForDataType<E>(
    private val dataConverter: (JsonElement) -> E
) : KSerialLoader<JsonRpcErrorObject<E>> {
    constructor(serialLoader: KSerialLoader<E>) : this({ jsonElement ->
        JsonTreeMapper().readTree(jsonElement, serialLoader)
    })

    override fun load(input: KInput): JsonRpcErrorObject<E> {
        val untyped = JsonRpcErrorObjectSerialLoader.load(input)
        return JsonRpcErrorObject(untyped.code, untyped.message, untyped.data?.let(dataConverter))
    }

    override fun update(input: KInput, old: JsonRpcErrorObject<E>): JsonRpcErrorObject<E> =
        throw UnsupportedOperationException("Update not supported")
}

/*
open class JsonRpcErrorObjectKSerializer<E>(
    private val eSerializer: KSerializer<E>
) : KSerializer<JsonRpcErrorObject<E>> {
    override fun save(output: KOutput, obj: JsonRpcErrorObject<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeIntElementValue(serialClassDesc, 0, obj.code)
        output.writeStringElementValue(serialClassDesc, 1, obj.message)
        obj.data?.let { output.writeSerializableElementValue(serialClassDesc, 2, eSerializer, it) }

        output.writeEnd(serialClassDesc)
    }

    override fun load(input: KInput): JsonRpcErrorObject<E> {
        @Suppress("NAME_SHADOWING")
        val input = input.readBegin(serialClassDesc)

        var readElement = input.readElement(serialClassDesc)
        check(readElement == 0) { "'code' read element $readElement, should be 0" }
        val code = input.readIntElementValue(serialClassDesc, 0)

        readElement = input.readElement(serialClassDesc)
        check(readElement == 1) { "'message' read element $readElement, should be 1" }
        val message = input.readStringElementValue(serialClassDesc, 1)

        readElement = input.readElement(serialClassDesc)
        val data: E? = if (readElement == 2) { // 'data' property was present
            input.readSerializableElementValue(serialClassDesc, 2, eSerializer)
        } else {
            null
        }

        input.readEnd(serialClassDesc)

        return JsonRpcErrorObject(code, message, data)
    }

    override val serialClassDesc: KSerialClassDesc =
        SerialClassDescImpl("com.blubber.message.JsonRpcErrorObject").apply {
            addElement("code")
            addElement("message")
            addElement("data")
        }
}

object JsonRpcErrorObjectSerializer : JsonRpcErrorObjectKSerializer<String>(StringSerializer)
*/
