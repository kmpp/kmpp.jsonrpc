package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializationTest {
    @Serializable
    private data class Data(
        val i: Int = 123,
        val d: Double = 123.456,
        @Optional val nullable: String? = null,
        val string: String = "string"
    )

    @Test
    fun testJsonRpcIDSerialization() {
        roundtrip(JsonRpcIDSerializer, JsonRpcNullID)
        roundtrip(JsonRpcIDSerializer, JsonRpcID(id = "id"))
        roundtrip(JsonRpcIDSerializer, JsonRpcID(id = "5"))
        roundtrip(JsonRpcIDSerializer, JsonRpcID(id = 5L))
        assertEquals(JsonRpcStringID(id = "id"), parse(JsonRpcIDSerializer, "\"id\""))
        assertFailsWith(SerializationException::class) { parse(JsonRpcIDSerializer, "1234.5678") }
    }

    @Test
    fun testClientJsonRpcSerialization() {
        val saver = ClientJsonRpcSerialSaver(Data.serializer())
        val loader = ClientJsonRpcSerialLoader.withParamsLoader(Data.serializer())

        roundtrip(saver, loader, RequestJsonRpc("method", Data(), JsonRpcID("id")))
        roundtrip(saver, loader, RequestJsonRpc("m", Data(nullable = "nullable"), JsonRpcNullID))
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","method":"m","params":{"i":123,"d":123.456,"string":"string"},"id":"id"}"""
            ),
            RequestJsonRpc("m", Data(), JsonRpcID("id"))
        )
        assertEquals(
            roundtrip(
                saver, loader, """{"jsonrpc":"2.0","method":"m","id":"id"}"""
            ),
            RequestJsonRpc<Data>("m", null, JsonRpcID("id"))
        )
        assertFailsWith(SerializationException::class) { parse(loader, """{"jsonrpc":"2.0",
            "method":"m","id":123.456}""") }

        roundtrip(saver, loader, NotificationJsonRpc("m", Data()))
        roundtrip(saver, loader, NotificationJsonRpc<Data>("m", params = null))
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","method":"m","params":{"i":123,"d":123.456,"string":"string"}}"""
            ),
            NotificationJsonRpc("m", Data())
        )
        assertEquals(
            roundtrip(saver, loader, """{"jsonrpc":"2.0","method":"m"}"""),
            NotificationJsonRpc<Data>("m", params = null)
        )
    }

    @Test
    fun testJsonRpcErrorObjectSerialization() {
        val saver = JsonRpcErrorObjectSerialSaver(Data.serializer())
        val loader = JsonRpcErrorObjectSerialLoader.withDataLoader(Data.serializer())

        roundtrip(saver, loader, JsonRpcErrorObject<Data>(42, message = "message", data = null))
        roundtrip(saver, loader, JsonRpcErrorObject(42, message = "message", data = Data()))
        assertEquals(
            roundtrip(saver, loader, """{"code":42,"message":"message"}"""),
            JsonRpcErrorObject<Data>(code = 42, message = "message", data = null)
        )
    }

    @Serializable
    private data class Result(
        val el: Long = 987L,
        val strList: List<String> = emptyList()
    )

    @Test
    fun testServerJsonRpcSerialization() {

    }

    private fun <T> roundtrip(serializer: KSerializer<T>, obj: T) =
        roundtrip(serializer, serializer, obj)

    private fun <T> roundtrip(saver: KSerialSaver<T>, loader: KSerialLoader<T>, obj: T) {
        val str = JSON.stringify(saver, obj)
        println(str)
        val parsed = JSON.parse(loader, str)
        assertEquals(obj, parsed)
    }

    private fun <T> roundtrip(serializer: KSerializer<T>, json: String) =
        roundtrip(serializer, serializer, json)

    private fun <T> roundtrip(saver: KSerialSaver<T>, loader: KSerialLoader<T>, json: String): T {
        val obj = parse(loader, json)
        val stringified = JSON.stringify(saver, obj)
        assertEquals(json, stringified)
        return obj
    }

    private fun <T> parse(loader: KSerialLoader<T>, json: String): T {
        println(json)
        return JSON.parse(loader, json)
    }
}
