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
        val loader = ClientJsonRpcSerialLoader.withParamsConverter(Data.serializer().treeMapper())

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
        assertFailsWith(SerializationException::class) {
            parse(
                loader, """{"jsonrpc":"2.0",
            "method":"m","id":123.456}"""
            )
        }

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
        val loader =
            JsonRpcErrorObjectSerialLoader.withDataConverter(Data.serializer().treeMapper())

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
        val strList: List<String?> = emptyList()
    )

    @Test
    fun testServerJsonRpcSerialization() {
        val saver = ServerJsonRpcSerialSaver(
            resultSaver = Result.serializer(),
            errorDataSaver = Data.serializer()
        )
        val loader = ServerJsonRpcSerialLoader.withConverters(
            resultConverter = Result.serializer().treeMapper(),
            errorDataConverter = Data.serializer().treeMapper()
        )

        roundtrip(
            saver, loader, ResultJsonRpc(
                result = Result(123L, listOf("hi", null, "whoa")), id = JsonRpcID("id")
            ).withErrorType()
        )
        roundtrip(
            saver, loader, ErrorJsonRpc(
                error = JsonRpcErrorObject(42, "error!", Data()), id = JsonRpcNullID
            ).withResultType()
        )

        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","result":{"el":10000,"strList":["heynow"]},"id":"id"}"""
            ),
            ResultJsonRpc(
                result = Result(10_000L, listOf("heynow")),
                id = JsonRpcID("id")
            ).withErrorType()
        )
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","error":{"code":42,"message":"eek","data":{"i":42,"d":0.0,"nullable":"present","string":"o"}},"id":"id"}"""
            ),
            ErrorJsonRpc(
                error = JsonRpcErrorObject(
                    code = 42,
                    message = "eek",
                    data = Data(42, 0.0, nullable = "present", string = "o")
                ),
                id = JsonRpcID("id")
            ).withResultType()
        )
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","error":{"code":42,"message":"eek"},"id":"id"}"""
            ),
            ErrorJsonRpc(
                error = JsonRpcErrorObject<Data>(code = 42, message = "eek", data = null),
                id = JsonRpcID("id")
            ).withResultType()
        )

        assertFailsWith(SerializationException::class) {
            parse(loader, """{"jsonrpc":"2.0","id":"id"}""")
        }
    }

    private fun <T> roundtrip(serializer: KSerializer<T>, obj: T) =
        roundtrip(serializer, serializer, obj)

    private fun <T> roundtrip(saver: KSerialSaver<T>, loader: KSerialLoader<T>, obj: T) {
        val str = JSON.stringify(saver, obj)
        val parsed = JSON.parse(loader, str)
        assertEquals(obj, parsed)
    }

    private fun <T> roundtrip(saver: KSerialSaver<T>, loader: KSerialLoader<T>, json: String): T {
        val obj = parse(loader, json)
        val stringified = JSON.stringify(saver, obj)
        assertEquals(json, stringified)
        return obj
    }

    private fun <T> parse(loader: KSerialLoader<T>, json: String): T {
        return JSON.parse(loader, json)
    }
}
