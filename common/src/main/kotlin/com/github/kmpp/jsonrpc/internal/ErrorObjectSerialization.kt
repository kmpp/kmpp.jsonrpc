package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ErrorObject
import com.github.kmpp.jsonrpc.InternalError
import com.github.kmpp.jsonrpc.InvalidParams
import com.github.kmpp.jsonrpc.InvalidRequest
import com.github.kmpp.jsonrpc.JsonReader
import com.github.kmpp.jsonrpc.JsonWriter
import com.github.kmpp.jsonrpc.ParseError
import com.github.kmpp.jsonrpc.RequestError
import com.github.kmpp.jsonrpc.internalError
import com.github.kmpp.jsonrpc.invalidParams
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.content
import kotlinx.serialization.json.int
import kotlinx.serialization.json.json

private const val CODE = "code"
private const val MESSAGE = "message"
private const val DATA = "data"

internal object ErrorObjectWriter : JsonWriter<ErrorObject>() {
    override fun write(value: ErrorObject): JsonElement = json {
        CODE to value.code
        MESSAGE to value.message
        value.data?.let { DATA to it }
    }
}

internal object ErrorObjectReader : JsonReader<ErrorObject>() {
    override fun read(json: JsonElement): ErrorObject {
        val jsonObject = json.jsonObject

        val code = jsonObject[CODE].int
        val message = jsonObject[MESSAGE].content
        val data = jsonObject.getOrNull(DATA)
        return ErrorObject(code, message, data)
    }
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
