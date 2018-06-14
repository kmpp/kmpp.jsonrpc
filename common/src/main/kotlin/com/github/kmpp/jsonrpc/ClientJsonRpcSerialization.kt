package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
import com.github.kmpp.jsonrpc.jsonast.JsonTreeMapper
import kotlinx.serialization.KInput
import kotlinx.serialization.KOutput
import kotlinx.serialization.KSerialClassDesc
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.SerializationException
import kotlinx.serialization.internal.SerialClassDescImpl

private val serialClassDesc: KSerialClassDesc =
    SerialClassDescImpl("com.github.kmpp.jsonrpc.ClientJsonRpc").apply {
        addElement("jsonrpc")
        addElement("method")
        addElement("params")
        addElement("id")
    }

class ClientJsonRpcSerialSaver<P>(
    private val paramsSaver: KSerialSaver<P>
) : KSerialSaver<ClientJsonRpc<P>> {
    override fun save(output: KOutput, obj: ClientJsonRpc<P>) {
        @Suppress("NAME_SHADOWING")
        val output = output.writeBegin(serialClassDesc)

        output.writeStringElementValue(serialClassDesc, 0, obj.jsonrpc)
        output.writeStringElementValue(serialClassDesc, 1, obj.method)
        obj.params?.let { tParams ->
            output.writeSerializableElementValue(serialClassDesc, 2, paramsSaver, tParams)
        }
        if (obj is RequestJsonRpc<*>) {
            output.writeSerializableElementValue(
                serialClassDesc, 3,
                JsonRpcIDSerializer, obj.id
            )
        }

        output.writeEnd(serialClassDesc)
    }
}

object ClientJsonRpcSerialLoader : KSerialLoader<ClientJsonRpc<JsonElement>> {
    override fun load(input: KInput): ClientJsonRpc<JsonElement> {
        val tree = input.to<JsonObject>()

        tree.checkJsonrpc()

        val method = tree.getRequired<JsonString>(JsonObject::getAsValue, "method").str

        val paramsElement = tree["params"]
        val params = paramsElement?.let { elem ->
            when (elem) {
                is JsonObject, is JsonArray -> elem
                else -> throw SerializationException("\"params\" must contain JSON Object or Array")
            }
        }

        return if ("id" in tree) {
            val id = tree.getAsValue("id").readJsonRpcID()
            RequestJsonRpc(method, params, id)
        } else {
            NotificationJsonRpc(method, params)
        }
    }

    override fun update(
        input: KInput,
        old: ClientJsonRpc<JsonElement>
    ): ClientJsonRpc<JsonElement> = throw UnsupportedOperationException("Update not supported")

    fun <P> withParamsLoader(loader: KSerialLoader<P>): KSerialLoader<ClientJsonRpc<P>> =
        ClientJsonRpcSerialLoaderForParamsType(loader)
}

internal class ClientJsonRpcSerialLoaderForParamsType<P>(
    private val paramsConverter: (JsonElement) -> P
) : KSerialLoader<ClientJsonRpc<P>> {
    constructor(serialLoader: KSerialLoader<P>) : this({ jsonElement ->
        JsonTreeMapper().readTree(jsonElement, serialLoader)
    })

    override fun load(input: KInput): ClientJsonRpc<P> {
        val untyped = ClientJsonRpcSerialLoader.load(input)
        return when (untyped) {
            is RequestJsonRpc -> {
                RequestJsonRpc(
                    untyped.method,
                    untyped.params?.let(paramsConverter),
                    untyped.id
                )
            }
            is NotificationJsonRpc -> {
                NotificationJsonRpc(
                    untyped.method,
                    untyped.params?.let(paramsConverter)
                )
            }
        }
    }

    override fun update(input: KInput, old: ClientJsonRpc<P>): ClientJsonRpc<P> =
        throw UnsupportedOperationException("Update not supported")
}
