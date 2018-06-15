package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonNull
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonPrimitive
import com.github.kmpp.jsonrpc.jsonast.JsonString
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.SerializationException

inline fun <reified E : JsonElement> JsonObject.getRequired(
    getElem: JsonObject.(String) -> JsonElement,
    key: String
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

fun JsonObject.checkJsonrpc() {
    this.getRequired<JsonString>(JsonObject::getAsValue, "jsonrpc").let { jsonString ->
        require(jsonString.str == JSON_RPC) {
            "$JSON_RPC is only supported non-null value for \"jsonrpc\""
        }
    }
}

inline fun <reified E : JsonElement> KInput.to(): E {
    val jsonReader = this as? JSON.JsonInput
            ?: throw SerializationException("This class can be loaded only by JSON")
    return jsonReader.readAsTree() as? E
            ?: throw SerializationException("Expected ${E::class} but had ${jsonReader.readAsTree()}")
}

internal class JsonParseException(msg: String) : SerializationException(msg)
internal class InvalidRequestException(msg: String) : SerializationException(msg)
internal class InvalidParamsException(msg: String) : SerializationException(msg)

private val JSON_TREE_MAPPER = JsonTreeMapper()

fun <T> KSerialLoader<T>.treeMapper(): (JsonElement) -> T = { jsonElement ->
    JSON_TREE_MAPPER.readTree(jsonElement, this)
}

fun <E> readArray(readElem: (JsonElement) -> E): (JsonElement) -> List<E> = { array ->
    (array as JsonArray).content.map(readElem)
}

fun <E> readNullableArray(readElem: (JsonElement) -> E): (JsonElement) -> List<E?> =
    readArray { elem ->
        when (elem) {
            JsonNull -> null
            else -> readElem(elem)
        }
    }

val PrimitiveConvert = { elem: JsonElement -> (elem as JsonPrimitive) }
val StringConvert = { elem: JsonElement -> PrimitiveConvert(elem).str }
val BooleanConvert = { elem: JsonElement -> PrimitiveConvert(elem).asBoolean }
val IntConvert = { elem: JsonElement -> PrimitiveConvert(elem).asInt }
val LongConvert = { elem: JsonElement -> PrimitiveConvert(elem).asLong }
val DoubleConvert = { elem: JsonElement -> PrimitiveConvert(elem).asDouble }
