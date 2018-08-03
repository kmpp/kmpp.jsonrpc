package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.Error
import com.github.kmpp.jsonrpc.JsonReader
import com.github.kmpp.jsonrpc.JsonWriter
import com.github.kmpp.jsonrpc.Response
import com.github.kmpp.jsonrpc.Result
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.json

private const val JSONRPC = "jsonrpc"
private const val RESULT = "result"
private const val ERROR = "error"
private const val ID = "id"

internal class ResponseReader<R : Any>(private val internalResultReader: JsonReader<R>) :
    JsonReader<Response<R>>() {
    override fun read(json: JsonElement): Response<R> {
        val jsonObject = json.jsonObject
        jsonObject.checkJsonrpc()
        val id = JsonRpcIDReader.read(jsonObject[ID])
        val result = jsonObject.getOrNull(RESULT)?.let { internalResultReader.read(it) }
        return if (result != null) {
            Result(result, id)
        } else {
            val error = ErrorObjectReader.read(jsonObject[ERROR])
            Error(error, id)
        }
    }
}

internal class ResponseWriter<R : Any>(private val internalResultWriter: JsonWriter<R>) :
    JsonWriter<Response<R>>() {
    override fun write(value: Response<R>): JsonElement = when (value) {
        is Error -> ErrorWriter.write(value)
        is Result<R> -> json {
            JSONRPC to value.jsonrpc
            RESULT to internalResultWriter.write(value.result)
            ID to JsonRpcIDWriter.write(value.id)
        }
    }
}

internal object ErrorWriter : JsonWriter<Error>() {
    override fun write(value: Error): JsonElement = json {
        JSONRPC to value.jsonrpc
        ERROR to ErrorObjectWriter.write(value.error)
        ID to JsonRpcIDWriter.write(value.id)
    }
}
