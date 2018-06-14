package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonLiteral
import com.github.kmpp.jsonrpc.jsonast.JsonNull
import com.github.kmpp.jsonrpc.jsonast.JsonPrimitive
import com.github.kmpp.jsonrpc.jsonast.JsonString
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.internal.SerialClassDescImpl

private fun JsonElement.readJsonRpcID(): JsonRpcID = when (this) {
    is JsonPrimitive -> readJsonRpcID()
    else -> throw SerializationException("Could not parse $this to JsonRpcID")
}

internal fun JsonPrimitive.readJsonRpcID(): JsonRpcID = when (this) {
    is JsonString -> JsonRpcID(this.str)
    is JsonLiteral -> {
        if (this == JsonNull) {
            JsonRpcNullID
        } else {
            try {
                JsonRpcID(this.asLong)
            } catch (e: NumberFormatException) {
                throw SerializationException(e.toString())
            }
        }
    }
}

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
        SerialClassDescImpl("com.blubber.message.JsonRpcID")
}
