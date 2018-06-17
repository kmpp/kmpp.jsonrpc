package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.toErrorObject
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.list
import kotlinx.serialization.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JsonRpcApiTest {
    private val intResultSaver = getResultSaver(IntSerializer)
    private val stringErrorSaver =
        getErrorSaver(StringSerializer)
    private val responseSaver: KSerialSaver<Response<Int, String>> =
        getResponseSaver(intResultSaver, stringErrorSaver)

    @Serializable
    data class IntInput(val input: Int = 0)

    @Serializable
    data class Beep(val s: String = "BEEEEEEEEEEEEEEEP.")

    @Test
    fun testApiExamples() {
        request(
            json = """{"jsonrpc":"2.0","method":"sum","params":[3,4],"id":"UUID-1"}""",
            expResponse = Result(7, JsonRpcID("UUID-1")).coerceErrorType()
        )
        request(
            json = """{"jsonrpc":"2.0","method":"square","params":{"input":3},"id":"UUID-2"}""",
            expResponse = Result(9, JsonRpcID("UUID-2")).coerceErrorType()
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
        expResponse: Response<R, String>? = null,
        expResponseJson: String? = null
    ) {
        println("--> CLIENT : $json")

        val parsed: ReadResult<Request<JsonElement>> =
            loadRequest(json)

        val response: String? = when (parsed) {
            is ReadError -> {
                expResponse?.let { assertEquals(it as Error<String>, parsed.stringError) }
                // Request was invalid JSON, run any needed error handling logic
                // Can construct and stringify a custom error object, or just use the
                // provided String-based default:
                parsed.stringErrorJson
            }
            is ReadSuccess<Request<JsonElement>> -> {
                val message: Request<JsonElement> = parsed.message
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
        expected: Response<R, String>? = null
    ): String {
        println("**> REQUEST: ${request.method} ${request.params}")
        return when (request.method) {
            "sum" -> {
                val sumResult = getResult(request, IntReader.array) { intList ->
                    intList?.sum() ?: 0
                }
                checkExpected(expected, sumResult)
                saveResponse(responseSaver, sumResult)
            }
            "subtract" -> {
                if (request.params == null) {
                    val error =
                        Error(
                            invalidParams(request.params.toString()),
                            request.id
                        )
                    checkExpected(expected, error.coerceResultType<Int>())
                    return saveResponse(
                        responseSaver,
                        error.coerceResultType()
                    )
                }

                val subtractResult: Response<Int, String> = when (request.params) {
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
                            invalidParams(request.params.toString()),
                            request.id
                        ).coerceResultType()
                    }
                }
                checkExpected(expected, subtractResult)
                saveResponse(responseSaver, subtractResult)
            }
            "square" -> {
                val squareResult =
                    getResult(request, IntInput.serializer().tree) { input ->
                        input?.let { it.input * it.input } ?: 0
                    }
                checkExpected(expected, squareResult)
                saveResponse(responseSaver, squareResult)
            }
            else -> {
                val errorObject = methodNotFound(request.method)
                println("<** ERROR  : $errorObject")
                val error = Error(errorObject, request.id)
                checkExpected(expected, error)
                saveResponse(
                    responseSaver,
                    error.coerceResultType()
                )
            }
        }
    }

    private fun <P, R> getResult(
        request: ClientRequest<JsonElement>,
        parser: (JsonElement) -> P,
        runCalc: (P?) -> R
    ): Response<R, String> {
        val paramsParseResult: ReadResult<ClientRequest<P>> =
            request.parseRequestParams(parser)
        return when (paramsParseResult) {
            is ReadError -> {
                // TODO: Investigate why can't use coerceResultType() here, it does the same thing
                @Suppress("UNCHECKED_CAST")
                paramsParseResult.stringError as Response<R, String>
            }
            is ReadSuccess<ClientRequest<P>> -> {
                val outcome = runCalc(paramsParseResult.message.params)
                println("<** RESULT : $outcome")
                Result(outcome, request.id).coerceErrorType()
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
                val withBeep = notification.parseNotificationParams(Beep.serializer().tree)
                when (withBeep) {
                    is ReadError -> "Error parsing notification params: ${notification.params}"
                    is ReadSuccess<Notification<Beep>> -> {
                        withBeep.message.params?.s ?: Beep().s
                    }
                }
            }
            "foobar" -> "Received \"foobar\" notification."
            else -> "I don't know what to do for method ${notification.method}."
        }
        println("**> NOTIFY : $toPrint")
    }

    private fun <R1, R2> checkExpected(
        expected: Response<R1, String>?,
        result: Response<R2, String>
    ) {
        expected?.let {
            @Suppress("UNCHECKED_CAST")
            val typeExpected = it as? Response<R2, String>?
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

    @Serializable
    data class Result(val r: Int = 0)

    @Test
    fun testGoodRoundtrip() {
        val request =
            ClientRequest(method = "sum", params = listOf(4, 5), id = JsonRpcID("UUID-123"))
        val requestSaver = getRequestSaver(IntSerializer.list)
        val requestJson = saveRequest(requestSaver, request)
        val rawRequest = loadRequest(requestJson)
        rawRequest as ReadSuccess<Request<JsonElement>>
        @Suppress("UNCHECKED_CAST")
        rawRequest as ReadSuccess<ClientRequest<JsonElement>>
        val typed = rawRequest.message.parseRequestParams(IntReader.array)
        typed as ReadSuccess<ClientRequest<List<Int>>>
        assertEquals(
            typed.message,
            ClientRequest("sum", listOf(4, 5), JsonRpcID("UUID-123"))
        )
        val params: List<Int> = typed.message.params!!
        val resultObject = Result(params[0] + params[1])
        val result: com.github.kmpp.jsonrpc.Result<Result> =
            Result(result = resultObject, id = typed.message.id)
        val resultSaver = getResultSaver(Result.serializer())
        val resultJson = saveResult(resultSaver, result)
        val resultLoader =
            getResponseLoaderWithReaders(
                Result.serializer().tree,
                StringReader
            )
        val loadedResult =
            loadResponse(resultLoader, resultJson)
        assertEquals(
            loadedResult,
            Result(result = Result(9), id = JsonRpcID("UUID-123")).coerceErrorType()
        )
    }


    @Serializable
    data class OtherParams(val s1: String = "can't", val s2: String = "add")

    @Test
    fun testBadRoundtrip() {
        val request =
            ClientRequest(method = "sum", params = OtherParams(), id = JsonRpcID("UUID-123"))
        val requestSaver =
            getRequestSaver(OtherParams.serializer())
        val requestJson = saveRequest(requestSaver, request)
        val rawRequestReadResult = loadRequest(requestJson)
        rawRequestReadResult as ReadSuccess<Request<JsonElement>>
        val rawRequest = rawRequestReadResult.message as ClientRequest<JsonElement>
        assertEquals(rawRequest.method, "sum")
        val typedRequestReadResult = rawRequest.parseRequestParams(Params.serializer().tree)
        typedRequestReadResult as ReadError
        val error = Error(typedRequestReadResult.toErrorObject(), rawRequest.id)
        val errorSaver = getErrorSaver(StringSerializer)
        val resultJson = saveError(errorSaver, error)
        val resultLoader =
            getResponseLoaderWithReaders(
                Result.serializer().tree,
                StringReader
            )
        val loadedResult =
            loadResponse(resultLoader, resultJson)
        assertEquals(
            loadedResult,
            Error(
                error = invalidParams("Unable to load params: Field i1 is required, but it was missing"),
                id = JsonRpcID("UUID-123")
            ).coerceResultType()
        )
    }

    private inline fun <reified T : JsonElement> expectValidRequest(
        method: String, id: JsonRpcID, json: String
    ) {
        val parsed = loadRequest(json)
        val result: Request<JsonElement>
        when (parsed) {
            is ReadError -> fail("Should have had valid result.")
            is ReadSuccess<Request<JsonElement>> -> result = parsed.message
        }

        result as ClientRequest<JsonElement>
        assertTrue { result.method == method && result.id == id }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : JsonElement> expectValidNotification(
        method: String, json: String
    ) {
        val parsed = loadRequest(json)
        val result: Request<JsonElement>
        when (parsed) {
            is ReadError -> fail("Should have had valid result.")
            is ReadSuccess<Request<JsonElement>> -> result = parsed.message
        }

        result as Notification<JsonElement>
        assertTrue { result.method == method }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : ReadError> expectError(json: String) {
        val parsed = loadRequest(json)
        when (parsed) {
            !is ReadError -> fail("Should have had error result")
            else -> assertTrue { parsed is T }
        }
    }

    private fun expectInvalidParamsError(loader: KSerialLoader<*>, json: String) {
        val parsed = loadRequest(json) as ReadSuccess<Request<JsonElement>>
        val parsedMessage = parsed.message as ClientRequest<JsonElement>
        val typed = parsedMessage.parseRequestParams(loader.tree)
        when (typed) {
            !is ReadError -> fail("Should have had error result")
            else -> assertTrue {
                @Suppress("USELESS_CAST")
                (typed as ReadError) is InvalidParams
            }
        }
    }
}

