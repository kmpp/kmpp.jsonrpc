package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonPrimitive
import com.github.kmpp.jsonrpc.jsonast.JsonString
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.SerializationException

internal inline fun <reified E : JsonElement> JsonObject.getRequired(
    key: String,
    getElem: JsonObject.(String) -> JsonElement
): E {
    if (key !in this) {
        throw SerializationException("Did not find \"$key\" in tree")
    }

    val elem = this.getElem(key)
    return when (elem) {
        is E -> elem
        else -> throw SerializationException("$elem JSON type did not match expected ${E::class}")
    }
}

internal fun JsonObject.checkJsonrpc() {
    this.getRequired<JsonString>("jsonrpc", JsonObject::getAsValue).let { jsonString ->
        require(jsonString.str == JSON_RPC) {
            "$JSON_RPC is only supported non-null value for \"jsonrpc\""
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

internal class InvalidParamsException(msg: String, val id: JsonRpcID) : SerializationException(msg)

private val JSON_TREE_MAPPER = JsonTreeMapper()

fun <T> KSerialLoader<T>.treeMapper(): (JsonElement) -> T = { jsonElement ->
    JSON_TREE_MAPPER.readTree(jsonElement, this)
}

val <E> ((JsonElement) -> E).array: (JsonElement) -> List<E>
    get() = { (it as JsonArray).content.map(this) }

val PrimitiveParser = { elem: JsonElement -> (elem as JsonPrimitive) }
val StringParser = { elem: JsonElement -> PrimitiveParser(elem).str }
val BooleanParser = { elem: JsonElement -> PrimitiveParser(elem).asBoolean }
val IntParser: (JsonElement) -> Int = { elem -> PrimitiveParser(elem).asInt }
val LongParser = { elem: JsonElement -> PrimitiveParser(elem).asLong }
val DoubleParser = { elem: JsonElement -> PrimitiveParser(elem).asDouble }
