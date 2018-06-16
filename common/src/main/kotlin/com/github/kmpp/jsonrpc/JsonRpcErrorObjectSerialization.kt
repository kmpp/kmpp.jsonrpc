package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonLiteral
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
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

class JsonRpcErrorObjectSaver<E>(
    private val dataSaver: KSerialSaver<E>
) : KSerialSaver<JsonRpcErrorObject<E>> {
    override fun save(output: KOutput, obj: JsonRpcErrorObject<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeIntElementValue(serialClassDesc, 0, obj.code)
        output.writeStringElementValue(serialClassDesc, 1, obj.message)
        obj.data?.let { output.writeSerializableElementValue(serialClassDesc, 2, dataSaver, it) }

        output.writeEnd(serialClassDesc)
    }
}

object JsonRpcErrorObjectLoader : KSerialLoader<JsonRpcErrorObject<JsonElement>> {
    override fun load(input: KInput): JsonRpcErrorObject<JsonElement> {
        val tree = input.to<JsonObject>()

        return tree.toJsonRpcErrorObject()
    }

    override fun update(
        input: KInput,
        old: JsonRpcErrorObject<JsonElement>
    ): JsonRpcErrorObject<JsonElement> = throw UpdateNotSupportedException("Update not supported")

    fun <E> withDataParser(parser: (JsonElement) -> E): KSerialLoader<JsonRpcErrorObject<E>> =
        JsonRpcErrorObjectLoaderWithDataParser(parser)
}

internal fun JsonObject.toJsonRpcErrorObject(): JsonRpcErrorObject<JsonElement> {
    val codeElem = this.getRequired<JsonLiteral>("code", JsonObject::getAsValue)
    val code = try {
        codeElem.asInt
    } catch (e: NumberFormatException) {
        throw SerializationException(e.toString())
    }

    val message = this.getRequired<JsonString>("message", JsonObject::getAsValue).str

    val data = this["data"]

    return JsonRpcErrorObject(code, message, data)
}

internal class JsonRpcErrorObjectLoaderWithDataParser<E>(
    private val dataParser: (JsonElement) -> E
) : KSerialLoader<JsonRpcErrorObject<E>> {
    override fun load(input: KInput): JsonRpcErrorObject<E> {
        val untyped = JsonRpcErrorObjectLoader.load(input)
        return JsonRpcErrorObject(untyped.code, untyped.message, untyped.data?.let(dataParser))
    }

    override fun update(input: KInput, old: JsonRpcErrorObject<E>): JsonRpcErrorObject<E> =
        throw UnsupportedOperationException("Update not supported")
}
