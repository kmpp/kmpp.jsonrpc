package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.JsonReader
import com.github.kmpp.jsonrpc.JsonRpcID
import com.github.kmpp.jsonrpc.JsonRpcNullID
import com.github.kmpp.jsonrpc.JsonRpcNumberID
import com.github.kmpp.jsonrpc.JsonRpcStringID
import com.github.kmpp.jsonrpc.JsonWriter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull

internal object JsonRpcIDWriter : JsonWriter<JsonRpcID>() {
    override fun write(value: JsonRpcID): JsonElement = when (value) {
        is JsonRpcStringID -> JsonLiteral(value.id)
        is JsonRpcNumberID -> JsonLiteral(value.id)
        is JsonRpcNullID -> JsonNull
    }
}

internal object JsonRpcIDReader : JsonReader<JsonRpcID>() {
    override fun read(json: JsonElement): JsonRpcID {
        val jsonPrimitive = json.primitive

        return when (jsonPrimitive) {
            is JsonLiteral -> {
                if (jsonPrimitive.toString().startsWith('"')) {
                    JsonRpcStringID(jsonPrimitive.content)
                } else {
                    JsonRpcNumberID(jsonPrimitive.long)
                }
            }
            is JsonNull -> JsonRpcNullID
        }
    }
}
