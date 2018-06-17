package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.JSON_RPC
import com.github.kmpp.jsonrpc.JsonRpcID
import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.SerializationException

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
    this.getRequired<JsonString>("jsonrpc", JsonObject::getAsValue).let { jsonString ->
        require(jsonString.str == JSON_RPC) {
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

internal class JsonParseException(msg: String) : SerializationException(msg)

internal class InvalidRequestException(msg: String, val id: JsonRpcID?) :
    SerializationException(msg)

// non-null id: if we receive invalid params on a Notification we don't send an error response
internal class InvalidParamsException(msg: String, val id: JsonRpcID) : SerializationException(msg)

internal val JSON_TREE_MAPPER = JsonTreeMapper()
