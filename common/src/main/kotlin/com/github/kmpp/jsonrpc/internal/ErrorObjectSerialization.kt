package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ErrorObject
import com.github.kmpp.jsonrpc.InternalError
import com.github.kmpp.jsonrpc.InvalidParams
import com.github.kmpp.jsonrpc.InvalidRequest
import com.github.kmpp.jsonrpc.ParseError
import com.github.kmpp.jsonrpc.ReadError
import com.github.kmpp.jsonrpc.internalError
import com.github.kmpp.jsonrpc.invalidParams
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
    SerialClassDescImpl("com.github.kmpp.jsonrpc.ErrorObject").apply {
        addElement("code")
        addElement("message")
        addElement("data")
    }

internal class ErrorObjectSaver<E>(
    private val dataSaver: KSerialSaver<E>
) : KSerialSaver<ErrorObject<E>> {
    override fun save(output: KOutput, obj: ErrorObject<E>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeIntElementValue(serialClassDesc, 0, obj.code)
        output.writeStringElementValue(serialClassDesc, 1, obj.message)
        obj.data?.let { output.writeSerializableElementValue(serialClassDesc, 2, dataSaver, it) }

        output.writeEnd(serialClassDesc)
    }
}

internal object ErrorObjectLoader : KSerialLoader<ErrorObject<JsonElement>> {
    override fun load(input: KInput): ErrorObject<JsonElement> {
        val tree = input.to<JsonObject>()

        return tree.toErrorObject()
    }

    override fun update(input: KInput, old: ErrorObject<JsonElement>): ErrorObject<JsonElement> =
        throw UpdateNotSupportedException("Update not supported")

    internal fun <E> withDataReader(reader: (JsonElement) -> E): KSerialLoader<ErrorObject<E>> =
        ErrorObjectLoaderWithDataReader(reader)
}

internal fun JsonObject.toErrorObject(): ErrorObject<JsonElement> {
    val codeElem = this.getRequired<JsonLiteral>("code", JsonObject::getAsValue)
    val code = try {
        codeElem.asInt
    } catch (e: NumberFormatException) {
        throw SerializationException(e.toString())
    }

    val message = this.getRequired<JsonString>("message", JsonObject::getAsValue).str

    val data = this["data"]

    return ErrorObject(code, message, data)
}

internal class ErrorObjectLoaderWithDataReader<E>(
    private val dataReader: (JsonElement) -> E
) : KSerialLoader<ErrorObject<E>> {
    override fun load(input: KInput): ErrorObject<E> {
        val rawErrorObject = ErrorObjectLoader.load(input)
        return ErrorObject(
            rawErrorObject.code,
            rawErrorObject.message,
            rawErrorObject.data?.let(dataReader)
        )
    }

    override fun update(input: KInput, old: ErrorObject<E>): ErrorObject<E> =
        throw UnsupportedOperationException("Update not supported")
}

private fun <E> parseError(data: E? = null): ErrorObject<E> =
    ErrorObject(code = -32700, message = "Parse error", data = data)

private fun <E> invalidRequest(data: E? = null): ErrorObject<E> =
    ErrorObject(code = -32600, message = "Invalid Request", data = data)

internal fun ReadError.toErrorObject(): ErrorObject<JsonElement> = when (this) {
    is ParseError -> parseError(jsonStringFromNullable(details))
    is InvalidRequest -> invalidRequest(jsonStringFromNullable(details))
    is InvalidParams -> invalidParams(jsonStringFromNullable(details))
    is InternalError -> internalError(jsonStringFromNullable(details))
}
