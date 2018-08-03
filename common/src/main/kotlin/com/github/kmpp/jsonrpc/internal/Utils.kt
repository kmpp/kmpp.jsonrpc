package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.JSON_RPC_2_0
import com.github.kmpp.jsonrpc.JsonReader
import com.github.kmpp.jsonrpc.JsonRpcID
import com.github.kmpp.jsonrpc.JsonWriter
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JSON
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTreeMapper
import kotlinx.serialization.json.content

internal fun JsonObject.checkJsonrpc() {
    this["jsonrpc"].let { jsonString ->
        require(jsonString.content == JSON_RPC_2_0) {
            "\"$JSON_RPC_2_0\" is only supported non-null value for \"jsonrpc\""
        }
    }
}

internal inline fun <reified E : JsonElement> KInput.to(): E {
    val jsonReader = this as? JSON.JsonInput
            ?: throw SerializationException("This class can be loaded only by JSON")
    return jsonReader.readAsTree() as? E
            ?: throw SerializationException("Expected ${E::class} but had ${jsonReader.readAsTree()}")
}

internal fun String.json(): JsonElement = JsonLiteral(this)

internal object JsonSaver : KSerialSaver<JsonElement> {
    override fun save(output: KOutput, obj: JsonElement) {
        val jsonWriter = output as? JSON.JsonOutput
                ?: throw SerializationException("This class can be saved only by JSON")
        jsonWriter.writeTree(obj)
    }
}

internal fun jsonStringFromNullable(content: String?): JsonLiteral? =
    content?.let { JsonLiteral(it) }

internal class JsonParseException(msg: String) : SerializationException(msg)

internal class InvalidRequestException(msg: String, val id: JsonRpcID?) :
    SerializationException(msg)

// non-null id: if we receive invalid params on a Notification we don't send an error response
internal class InvalidParamsException(msg: String, val id: JsonRpcID) : SerializationException(msg)

internal fun <T> T?.toStringJson(): JsonElement =
    this?.let { JsonLiteral(it.toString()) } ?: JsonNull

internal val JSON_TREE_MAPPER = JsonTreeMapper()

internal object IntWriter : JsonWriter<Int>() {
    override fun write(value: Int): JsonElement = JsonLiteral(value)
}

internal object IntReader : JsonReader<Int>() {
    override fun read(json: JsonElement): Int = json.primitive.int
}

internal object JsonElementReader : JsonReader<JsonElement>() {
    override fun read(json: JsonElement): JsonElement = json
}
