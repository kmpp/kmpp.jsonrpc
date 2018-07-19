package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ErrorObject
import com.github.kmpp.jsonrpc.InternalError
import com.github.kmpp.jsonrpc.InvalidParams
import com.github.kmpp.jsonrpc.InvalidRequest
import com.github.kmpp.jsonrpc.ParseError
import com.github.kmpp.jsonrpc.RequestError
import com.github.kmpp.jsonrpc.internalError
import com.github.kmpp.jsonrpc.invalidParams
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UpdateNotSupportedException
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonObject

private val serialClassDesc: KSerialClassDesc =
    SerialClassDescImpl("com.github.kmpp.jsonrpc.ErrorObject").apply {
        addElement("code")
        addElement("message")
        addElement("data")
    }

internal object ErrorObjectSaver : KSerialSaver<ErrorObject> {
    override fun save(output: KOutput, obj: ErrorObject) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeIntElementValue(serialClassDesc, 0, obj.code)
        output.writeStringElementValue(serialClassDesc, 1, obj.message)
        obj.data?.let { output.writeSerializableElementValue(serialClassDesc, 2, JsonSaver, it) }

        output.writeEnd(serialClassDesc)
    }
}

internal object ErrorObjectLoader : KSerialLoader<ErrorObject> {
    override fun load(input: KInput): ErrorObject {
        val tree = input.to<JsonObject>()

        return tree.toErrorObject()
    }

    override fun update(input: KInput, old: ErrorObject): ErrorObject =
        throw UpdateNotSupportedException("Update not supported")
}

internal fun JsonObject.toErrorObject(): ErrorObject {
    val codeElem = this.getRequired<JsonLiteral>("code", JsonObject::get)
    val code = try {
        codeElem.int
    } catch (e: NumberFormatException) {
        throw SerializationException(e.toString())
    }

    val message = this.getRequired<JsonLiteral>("message", JsonObject::get).content

    val data = this["data"]

    return ErrorObject(code, message, data)
}

private fun parseError(data: JsonElement? = null): ErrorObject =
    ErrorObject(code = -32700, message = "Parse error", data = data)

private fun invalidRequest(data: JsonElement? = null): ErrorObject =
    ErrorObject(code = -32600, message = "Invalid Request", data = data)

internal fun RequestError.toErrorObject(): ErrorObject = when (this) {
    is ParseError -> parseError(jsonStringFromNullable(details))
    is InvalidRequest -> invalidRequest(jsonStringFromNullable(details))
    is InvalidParams -> invalidParams(jsonStringFromNullable(details))
    is InternalError -> internalError(jsonStringFromNullable(details))
}
