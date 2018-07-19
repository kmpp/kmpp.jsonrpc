package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.JsonRpcIDSerializer
import com.github.kmpp.jsonrpc.internal.RequestLoader
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTreeParser
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
        assertFailsWith(NumberFormatException::class) { parse(JsonRpcIDSerializer, "1234.5678") }
    }

    @Test
    fun testClientJsonRpcSerialization() {
        val saver: KSerialSaver<Request<Data>> = getRequestSaver(Data.serializer())
        val loader: KSerialLoader<Request<Data>> = RequestLoader(Data.serializer().tree)

        roundtrip(saver, loader, ClientRequest("method", Data(), JsonRpcID("id")))
        roundtrip(saver, loader, ClientRequest("m", Data(nullable = "nullable"), JsonRpcNullID))
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","method":"m","params":{"i":123,"d":123.456,"string":"string"},"id":"id"}"""
            ),
            ClientRequest("m", Data(), JsonRpcID("id"))
        )
        assertEquals(
            roundtrip(
                saver, loader, """{"jsonrpc":"2.0","method":"m","id":"id"}"""
            ),
            ClientRequest<Data>("m", null, JsonRpcID("id"))
        )
        assertFailsWith(NumberFormatException::class) {
            parse(
                loader, """{"jsonrpc":"2.0",
            "method":"m","id":123.456}"""
            )
        }

        roundtrip(saver, loader, Notification("m", Data()))
        roundtrip(saver, loader, Notification<Data>("m", params = null))
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","method":"m","params":{"i":123,"d":123.456,"string":"string"}}"""
            ),
            Notification("m", Data())
        )
        assertEquals(
            roundtrip(saver, loader, """{"jsonrpc":"2.0","method":"m"}"""),
            Notification<Data>("m", params = null)
        )
    }

    /*
    @Test
    fun testJsonRpcErrorObjectSerialization() {
        val saver = ErrorObjectSaver(Data.serializer())
        val loader =
            ErrorObjectLoader.withDataReader(Data.serializer().tree)

        roundtrip(saver, loader, ErrorObject<Data>(42, message = "message", data = null))
        roundtrip(saver, loader, ErrorObject(42, message = "message", data = Data()))
        assertEquals(
            roundtrip(saver, loader, """{"code":42,"message":"message"}"""),
            ErrorObject<Data>(code = 42, message = "message", data = null)
        )

        val serverErrorObject = serverError(-32099, Data())
        roundtrip(saver, loader, serverErrorObject)
    }
    */

    @Serializable
    private data class Result(
        val el: Long = 987L,
        val strList: List<String?> = emptyList()
    )

    /*
    @Test
    fun testServerJsonRpcSerialization() {
        val responseSaver = getResponseSaver(Result.serializer(), Data.serializer())
        val resultSaver: KSerialSaver<Result<Result>> =
            getResultSaver(internalResultSaver = Result.serializer())
        val errorSaver: KSerialSaver<Error<Data>> =
            getErrorSaver(errorDataSaver = Data.serializer())
        val loader = getResponseLoaderWithReaders(
            resultReader = Result.serializer().tree,
            errorDataReader = Data.serializer().tree
        )

        roundtrip(
            resultSaver, loader, Result(
                result = Result(123L, listOf("hi", null, "whoa")), id = JsonRpcID("id")
            )
        )
        roundtrip(
            errorSaver, loader, Error(
                error = ErrorObject(42, "error!", Data()), id = JsonRpcNullID
            )
        )

        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","result":{"el":10000,"strList":["heynow"]},"id":"id"}"""
            ),
            Result(
                result = Result(10_000L, listOf("heynow")),
                id = JsonRpcID("id")
            )
        )
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","error":{"code":42,"message":"eek","data":{"i":42,"d":0.0,"nullable":"present","string":"o"}},"id":"id"}"""
            ),
            Error(
                error = ErrorObject(
                    code = 42,
                    message = "eek",
                    data = Data(42, 0.0, nullable = "present", string = "o")
                ),
                id = JsonRpcID("id")
            )
        )
        assertEquals(
            parse(
                loader,
                """{"jsonrpc":"2.0","error":{"code":42,"message":"eek"},"id":"id"}"""
            ),
            Error(
                error = ErrorObject<Data>(code = 42, message = "eek", data = null),
                id = JsonRpcID("id")
            )
        )

        assertFailsWith(SerializationException::class) {
            parse(loader, """{"jsonrpc":"2.0","id":"id"}""")
        }
    }
    */

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

    private fun <R> roundtrip(
        saver: KSerialSaver<Request<R>>,
        loader: KSerialLoader<Request<R>>,
        obj: Request<R>
    ) {
        val str = saver.save(obj)
        val parsed = loader.load(str)
        assertEquals(obj, parsed.success!!)
    }

    private fun <T> parse(loader: KSerialLoader<T>, json: String): T {
        return JSON.parse(loader, json)
    }

    @Test
    fun testUtilityParsers() {
        val nullElem = JsonNull
        assertEquals("null", PrimitiveReader(nullElem).content)
        val stringElem = JsonTreeParser("\"meow\"").readFully()
        assertEquals("meow", StringReader(stringElem))
        val booleanElem = JsonTreeParser("true").readFully()
        assertEquals(true, BooleanReader(booleanElem))
        val intElem = JsonTreeParser("1066").readFully()
        assertEquals(1066, IntReader(intElem))
        val longElem = JsonTreeParser("10000000000000").readFully()
        assertEquals(10000000000000, LongReader(longElem))
        val doubleElem = JsonTreeParser("98.5").readFully()
        assertEquals(98.5, DoubleReader(doubleElem))
    }
}
