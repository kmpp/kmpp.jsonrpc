package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.JsonRpcID
import com.github.kmpp.jsonrpc.JsonRpcNullID
import com.github.kmpp.jsonrpc.JsonRpcNumberID
import com.github.kmpp.jsonrpc.JsonRpcStringID
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.json.JSON
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonElement.readJsonRpcID(): JsonRpcID = when (this) {
    is JsonPrimitive -> readJsonRpcID()
    else -> throw SerializationException("Could not read $this as JsonRpcID")
}

private fun JsonPrimitive.readJsonRpcID(): JsonRpcID {
    println("Running readJsonRpcID on $this")
    return when (this) {
        is JsonNull -> JsonRpcNullID
        is JsonLiteral -> if (isString()) {
            println("reading as string")
            JsonRpcStringID(content)
        } else {
            println("reading as number")
            JsonRpcNumberID(long)
        }
    }
}

private fun JsonPrimitive.isString(): Boolean = toString().startsWith('"')

internal object JsonRpcIDSerializer : KSerializer<JsonRpcID> {
    override fun save(output: KOutput, obj: JsonRpcID) {
        when (obj) {
            is JsonRpcStringID -> output.writeStringValue(obj.id)
            is JsonRpcNumberID -> output.writeLongValue(obj.id)
            is JsonRpcNullID -> output.writeNullValue()
        }
    }

    override fun load(input: KInput): JsonRpcID {
        val jsonReader = input as? JSON.JsonInput
                ?: throw SerializationException("This class can be loaded only by JSON")
        return jsonReader.readAsTree().readJsonRpcID()
    }

    override val serialClassDesc: KSerialClassDesc =
        SerialClassDescImpl("com.github.kmpp.jsonrpc.JsonRpcID")
}
