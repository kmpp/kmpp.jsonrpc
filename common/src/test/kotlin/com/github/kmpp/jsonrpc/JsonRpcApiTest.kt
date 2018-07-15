package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.json
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JsonRpcApiTest {
    private val responseSaver = getResponseSaver(IntSerializer)

    @Serializable
    data class IntInput(val input: Int = 0)

    @Serializable
    data class Beep(val s: String = "BEEEEEEEEEEEEEEEP.")

    @Test
    fun testApiExamples() {
        request(
            json = """{"jsonrpc":"2.0","method":"sum","params":[3,4],"id":"UUID-1"}""",
            expResponse = Result(7, JsonRpcID("UUID-1"))
        )
        request(
            json = """{"jsonrpc":"2.0","method":"square","params":{"input":3},"id":"UUID-2"}""",
            expResponse = Result(9, JsonRpcID("UUID-2"))
        )
        request<Nothing>("""{"jsonrpc":"2.0","method":"print_params","params":["hello kotlin"]}""")
        request<Nothing>("""{"jsonrpc":"2.0","method":"beep"}""")
        request<Nothing>("""{"jsonrpc":"2.0","method":"beep","params":{"s":"beep."}}""")
    }

    @Test
    fun testJsonSpecExamples() {
        request<Int>(
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": [42, 23], "id": 1}""",
            expResponseJson = """{"jsonrpc":"2.0","result":19,"id":1}"""
        )
        request<Int>(
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": [23, 42], "id": 1}""",
            expResponseJson = """{"jsonrpc":"2.0","result":-19,"id":1}"""
        )
        request<Int>(
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": {"subtrahend": 23, "minuend": 42}, "id": 3}""",
            expResponseJson = """{"jsonrpc":"2.0","result":19,"id":3}"""
        )
        request<Int>(
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": {"minuend": 42, "subtrahend": 23}, "id": 3}""",
            expResponseJson = """{"jsonrpc":"2.0","result":19,"id":3}"""
        )
        request<Nothing>(
            json = """{"jsonrpc": "2.0", "method": "update", "params": [1,2,3,4,5]}"""
        )
        request<Nothing>(
            json = """{"jsonrpc": "2.0", "method": "foobar"}"""
        )
        request<Nothing>(
            json = """{"jsonrpc": "2.0", "method": "foobar", "id": "1"}""",
            expResponseJson = """{"jsonrpc":"2.0","error":{"code":-32601,"message":"Method not found","data":"foobar"},"id":"1"}"""
        )
        request<Nothing>(
            json = """{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]""",
            expResponseJson = """{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error","data":"Error parsing JSON to tree: JSON at 45: Expected ':'"},"id":null}"""
        )
        request<Nothing>(
            json = """{"jsonrpc": "2.0", "method": 1, "params": "bar"}""",
            expResponseJson = """{"jsonrpc":"2.0","error":{"code":-32600,"message":"Invalid Request","data":"Invalid JSON-RPC Request: element[1] JSON type did not match expected class JsonString"},"id":null}"""
        )
    }

    private fun <R> request(
        json: String,
        expResponse: Response<R>? = null,
        expResponseJson: String? = null
    ) {
        println("--> CLIENT : $json")

        val readOutcome: ReadOutcome<Request<JsonElement>> = loadRawRequest(json)

        val response: String? = when (readOutcome) {
            is ReadFailure -> {
                expResponse?.let { assertEquals(it as Error, readOutcome.failure.error) }
                // Request was invalid JSON, run any needed error handling logic
                // Can construct and stringify a custom error object, or just use the
                // provided String-based default:
                readOutcome.failure.errorJsonString
            }
            is ReadSuccess<Request<JsonElement>> -> {
                val message: Request<JsonElement> = readOutcome.success
                when (message) {
                    is ClientRequest<JsonElement> -> handleRequest(message, expResponse)
                    is Notification<JsonElement> -> {
                        handleNotification(message)
                        null
                    }
                }
            }
        }

        expResponseJson?.let { assertEquals(it, response) }
        println("<-- SERVER : $response\n")
    }

    private fun <R> handleRequest(
        request: ClientRequest<JsonElement>,
        expected: Response<R>? = null
    ): String {
        println("**> REQUEST: ${request.method} ${request.params}")
        return when (request.method) {
            "sum" -> {
                val sumResult = getResult(request, IntReader.array) { intList ->
                    intList?.sum() ?: 0
                }
                checkExpected(expected, sumResult)
                responseSaver.save(sumResult)
            }
            "subtract" -> {
                if (request.params == null) {
                    val error =
                        Error(
                            invalidParams(request.params.toString().json()),
                            request.id
                        )
                    checkExpected(expected, error)
                    return responseSaver.save(error)
                }

                val subtractResult: Response<Int> = when (request.params) {
                    is JsonArray -> {
                        getResult(request, IntReader.array) { intList ->
                            intList!!
                            assertTrue { intList.size == 2 }
                            intList[0] - intList[1]
                        }
                    }
                    is JsonObject -> {
                        val parser: (JsonElement) -> Map<String, Int?> =
                            (StringSerializer to IntSerializer).map.tree
                        getResult(request, parser) { stringIntMap ->
                            stringIntMap!!
                            stringIntMap["minuend"]!! - stringIntMap["subtrahend"]!!
                        }
                    }
                    else -> {
                        Error(
                            invalidParams(request.params.toString().json()),
                            request.id
                        )
                    }
                }
                checkExpected(expected, subtractResult)
                responseSaver.save(subtractResult)
            }
            "square" -> {
                val squareResult =
                    getResult(request, IntInput.serializer().tree) { input ->
                        input?.let { it.input * it.input } ?: 0
                    }
                checkExpected(expected, squareResult)
                responseSaver.save(squareResult)
            }
            else -> {
                val errorObject = methodNotFound(request.method.json())
                println("<** ERROR  : $errorObject")
                val error = Error(errorObject, request.id)
                checkExpected(expected, error)
                responseSaver.save(error)
            }
        }
    }

    private fun <P, R> getResult(
        request: ClientRequest<JsonElement>,
        parser: (JsonElement) -> P,
        runCalc: (P?) -> R
    ): Response<R> {
        val paramsParseOutcome: ReadOutcome<Request<P>> = request.parseParams(parser)
        return when (paramsParseOutcome) {
            is ReadFailure -> paramsParseOutcome.failure.error
            is ReadSuccess<Request<P>> -> {
                val outcome = runCalc(paramsParseOutcome.success.params)
                println("<** RESULT : $outcome")
                Result(outcome, request.id)
            }
        }
    }

    // We should not reply to a well-constructed notification Request object (even one with an
    // unexpected params Object or Array), so this method should not return a result
    private fun handleNotification(notification: Notification<JsonElement>) {
        val toPrint: String = when (notification.method) {
            "print_params", "update" -> {
                "${notification.method}: ${notification.params?.toString() ?: "<null>"}"
            }
            "beep" -> {
                val withBeep = notification.parseParams(Beep.serializer().tree)
                when (withBeep) {
                    is ReadFailure -> "Error parsing notification params: ${notification.params}"
                    is ReadSuccess<Request<Beep>> -> {
                        withBeep.success.params?.s ?: Beep().s
                    }
                }
            }
            "foobar" -> "Received \"foobar\" notification."
            else -> "I don't know what to do for method ${notification.method}."
        }
        println("**> NOTIFY : $toPrint")
    }

    private fun <R1, R2> checkExpected(
        expected: Response<R1>?,
        result: Response<R2>
    ) {
        expected?.let {
            @Suppress("UNCHECKED_CAST")
            val typeExpected = it as? Response<R2>?
            assertEquals(typeExpected, result)
        }
    }

    @Test
    fun resultsMatchSpecExamples() {
        expectValidRequest<JsonArray>(
            method = "subtract", id = JsonRpcID(1L),
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": [42, 23], "id": 1}"""
        )
        expectValidRequest<JsonArray>(
            method = "subtract", id = JsonRpcID(2L),
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": [23, 42], "id": 2}"""
        )
        expectValidRequest<JsonObject>(
            method = "subtract", id = JsonRpcID(3L),
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": {"subtrahend": 23, "minuend": 42}, "id": 3}"""
        )
        expectValidRequest<JsonObject>(
            method = "subtract", id = JsonRpcID(4L),
            json = """{"jsonrpc": "2.0", "method": "subtract", "params": {"minuend": 42, "subtrahend": 23}, "id": 4}"""
        )
        expectValidNotification<JsonArray>(
            method = "update",
            json = """{"jsonrpc": "2.0", "method": "update", "params": [1,2,3,4,5]}"""
        )

        expectError<ParseError>("""{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]""")
        expectError<InvalidRequest>("""{"jsonrpc": "2.0", "method": 1, "params": "bar"}""")
    }

    @Serializable
    data class Params(val i1: Int = 0, val i2: Int = 0)

    @Test
    fun testBadParamsLoading() {
        expectInvalidParamsError(
            loader = Params.serializer(),
            json = """{"jsonrpc": "2.0", "method": "foobar", "params":{"input":3.1}, "id":10}"""
        )
    }

    private inline fun <reified T : JsonElement> expectValidRequest(
        method: String, id: JsonRpcID, json: String
    ) {
        val readOutcome = loadRawRequest(json)
        val result: Request<JsonElement>
        when (readOutcome) {
            is ReadFailure -> fail("Should have had valid result.")
            is ReadSuccess<Request<JsonElement>> -> result = readOutcome.success
        }

        result as ClientRequest<JsonElement>
        assertTrue { result.method == method && result.id == id }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : JsonElement> expectValidNotification(
        method: String, json: String
    ) {
        val readOutcome = loadRawRequest(json)
        val result: Request<JsonElement>
        when (readOutcome) {
            is ReadFailure -> fail("Should have had valid result.")
            is ReadSuccess<Request<JsonElement>> -> result = readOutcome.success
        }

        result as Notification<JsonElement>
        assertTrue { result.method == method }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : RequestError> expectError(json: String) {
        val parsed = loadRawRequest(json)
        when (parsed) {
            !is ReadFailure -> fail("Should have had error result")
            else -> assertTrue("parsed $parsed should match type") { parsed.failure is T }
        }
    }

    private fun <T> expectInvalidParamsError(loader: KSerialLoader<T>, json: String) {
        val readOutcome = loadRawRequest(json) as ReadSuccess<Request<JsonElement>>
        val parsedMessage = readOutcome.success as ClientRequest<JsonElement>
        val typed = parsedMessage.parseParams(loader.tree)
        when (typed) {
            !is ReadFailure -> fail("Should have had error result")
            else -> assertTrue {
                @Suppress("USELESS_CAST")
                (typed.failure is InvalidParams)
            }
        }
    }
}

