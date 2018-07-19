package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.JSON_RPC
import com.github.kmpp.jsonrpc.JsonRpcID
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

internal inline fun <reified E : JsonElement> JsonObject.getRequired(
    key: String,
    readElem: JsonObject.(String) -> JsonElement
): E {
    if (key !in this) {
        throw SerializationException("Did not find \"$key\" in tree")
    }

    val elem = this.readElem(key)
    return when (elem) {
        is E -> elem
        else -> throw SerializationException(
            "element[$elem] JSON type did not match expected ${E::class}"
        )
    }
}

internal fun JsonObject.checkJsonrpc() {
    this.getRequired<JsonLiteral>("jsonrpc", JsonObject::get).let { jsonString ->
        require(jsonString.content == JSON_RPC) {
            "\"$JSON_RPC\" is only supported non-null value for \"jsonrpc\""
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
