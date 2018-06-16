package com.github.kmpp.jsonrpc

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
    private val intResultSaver = ResultJsonRpcSaver(IntSerializer)
    private val stringErrorSaver = ErrorJsonRpcSerialSaver(StringSerializer)
    private val responseSaver: KSerialSaver<ResponseJsonRpc<Int, String>> =
        ResponseJsonRpcSaver(intResultSaver, stringErrorSaver)

    @Serializable
    data class IntInput(val input: Int = 0)

    @Serializable
    data class Beep(val s: String = "BEEEEEEEEEEEEEEEP.")

    @Test
    fun testApiExamples() {
        request(
            json = """{"jsonrpc":"2.0","method":"sum","params":[3,4],"id":"UUID-1"}""",
            expResponse = ResultJsonRpc(7, JsonRpcID("UUID-1")).coerceErrorType()
        )
        request(
            json = """{"jsonrpc":"2.0","method":"square","params":{"input":3},"id":"UUID-2"}""",
            expResponse = ResultJsonRpc(9, JsonRpcID("UUID-2")).coerceErrorType()
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
        expResponse: ResponseJsonRpc<R, String>? = null,
        expResponseJson: String? = null
    ) {
        println("--> CLIENT : $json")

        // Parse JSON-RPC from the Client
        val parsed: ParsingResult<RequestJsonRpc<JsonElement>> = loadRequestJsonRpc(json)

        // Determine response given the input request
        val response: String? = when (parsed) {
            is ParsingError -> {
                expResponse?.let { assertEquals(it as ErrorJsonRpc<String>, parsed.stringError) }
                // Request was invalid JSON, run any error handling logic
                // Can construct and stringify a custom error object, or just use the
                // provided String-based default:
                parsed.stringErrorJson
            }
            is ParsingSuccess<RequestJsonRpc<JsonElement>, *> -> {
                val message: RequestJsonRpc<JsonElement> = parsed.message
                when (message) {
                    is ClientRequestJsonRpc<JsonElement> -> handleRequest(message, expResponse)
                    is NotificationJsonRpc<JsonElement> -> handleNotification(message)
                }
            }
        }

        expResponseJson?.let { assertEquals(it, response) }
        println("<-- SERVER : $response\n")
    }

    private fun <R> handleRequest(
        request: ClientRequestJsonRpc<JsonElement>,
        expected: ResponseJsonRpc<R, String>? = null
    ): String {
        println("**> REQUEST: ${request.method} ${request.params}")
        return when (request.method) {
            "sum" -> {
                val sumResult = getResult(request, IntParser.array) { intList ->
                    intList?.sum() ?: 0
                }
                checkExpected(expected, sumResult)
                saveResponseJsonRpc(responseSaver, sumResult)
            }
            "subtract" -> {
                if (request.params == null) {
                    val error =
                        ErrorJsonRpc(
                            JsonRpcErrorObject.invalidParams(request.params.toString()),
                            request.id
                        )
                    checkExpected(expected, error.coerceResultType<Int>())
                    return saveResponseJsonRpc(responseSaver, error.coerceResultType())
                }

                val subtractResult: ResponseJsonRpc<Int, String> = when (request.params) {
                    is JsonArray -> {
                        getResult(request, IntParser.array) { intList ->
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
                        ErrorJsonRpc(
                            JsonRpcErrorObject.invalidParams(request.params.toString()),
                            request.id
                        ).coerceResultType()
                    }
                }
                checkExpected(expected, subtractResult)
                saveResponseJsonRpc(responseSaver, subtractResult)
            }
            "square" -> {
                val squareResult =
                    getResult(request, IntInput.serializer().tree) { input ->
                        input?.let { it.input * it.input } ?: 0
                    }
                checkExpected(expected, squareResult)
                saveResponseJsonRpc(responseSaver, squareResult)
            }
            else -> {
                val errorObject = JsonRpcErrorObject.methodNotFound(request.method)
                println("<-- ERROR  : $errorObject")
                val error = ErrorJsonRpc(errorObject, request.id)
                checkExpected(expected, error)
                saveResponseJsonRpc(responseSaver, error.coerceResultType())
            }
        }
    }

    private fun <P, R> getResult(
        request: ClientRequestJsonRpc<JsonElement>,
        parser: (JsonElement) -> P,
        runCalc: (P?) -> R
    ): ResponseJsonRpc<R, String> {
        val paramsParseResult: ParsingResult<ClientRequestJsonRpc<P>> =
            request.parseRequestParams(parser)
        return when (paramsParseResult) {
            is ParsingError -> {
                // TODO: Investigate why can't use coerceResultType() here, it does the same thing
                @Suppress("UNCHECKED_CAST")
                paramsParseResult.stringError as ResponseJsonRpc<R, String>
            }
            is ParsingSuccess<ClientRequestJsonRpc<P>, *> -> {
                val outcome = runCalc(paramsParseResult.message.params)
                println("<** RESULT : $outcome")
                ResultJsonRpc(outcome, request.id).coerceErrorType()
            }
        }
    }

    // We should not reply to a well-constructed notification Request object (even one with an
    // unexpected params Object or Array), so this method returns Nothing?
    private fun handleNotification(notification: NotificationJsonRpc<JsonElement>): Nothing? {
        val toPrint: String = when (notification.method) {
            "print_params", "update" -> {
                "${notification.method}: ${notification.params?.toString() ?: "<null>"}"
            }
            "beep" -> {
                val withBeep = notification.parseNotificationParams(Beep.serializer().tree)
                when (withBeep) {
                    is ParsingError -> "Error parsing notification params: ${notification.params}"
                    is ParsingSuccess<NotificationJsonRpc<Beep>, *> -> {
                        withBeep.message.params?.s ?: Beep().s
                    }
                }
            }
            "foobar" -> "Received \"foobar\" notification."
            else -> "I don't know what to do for method ${notification.method}."
        }
        println("**> NOTIFY : $toPrint")
        return null
    }

    private fun <R1, R2> checkExpected(
        expected: ResponseJsonRpc<R1, String>?,
        result: ResponseJsonRpc<R2, String>
    ) {
        expected?.let {
            @Suppress("UNCHECKED_CAST")
            val typeExpected = it as? ResponseJsonRpc<R2, String>?
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
            ClientRequestJsonRpc(method = "sum", params = listOf(4, 5), id = JsonRpcID("UUID-123"))
        val requestSaver = RequestJsonRpcSaver(IntSerializer.list)
        val requestJson = saveRequestJsonRpc(requestSaver, request)
        val untyped = loadRequestJsonRpc(requestJson)
        untyped as ParsingSuccess<RequestJsonRpc<JsonElement>, *>
        @Suppress("UNCHECKED_CAST")
        untyped as ParsingSuccess<ClientRequestJsonRpc<JsonElement>, *>
        val typed = untyped.message.parseRequestParams(IntParser.array)
        typed as ParsingSuccess<ClientRequestJsonRpc<List<Int>>, *>
        assertEquals(
            typed.message,
            ClientRequestJsonRpc("sum", listOf(4, 5), JsonRpcID("UUID-123"))
        )
        val params: List<Int> = typed.message.params!!
        val resultObject = Result(params[0] + params[1])
        val result: ResultJsonRpc<Result> =
            ResultJsonRpc(result = resultObject, id = typed.message.id)
        val resultSaver = ResultJsonRpcSaver(Result.serializer())
        val resultJson = saveResultJsonRpc(resultSaver, result)
        val resultLoader =
            ResponseJsonRpcLoader.withParsers(
                Result.serializer().tree,
                StringParser
            )
        val loadedResult = loadResponseJsonRpc(resultLoader, resultJson)
        assertEquals(
            loadedResult,
            ResultJsonRpc(result = Result(9), id = JsonRpcID("UUID-123")).coerceErrorType()
        )
    }


    @Serializable
    data class OtherParams(val s1: String = "can't", val s2: String = "add")

    @Test
    fun testBadRoundtrip() {
        val request =
            ClientRequestJsonRpc(method = "sum", params = OtherParams(), id = JsonRpcID("UUID-123"))
        val requestSaver = RequestJsonRpcSaver(OtherParams.serializer())
        val requestJson = saveRequestJsonRpc(requestSaver, request)
        val untyped = loadRequestJsonRpc(requestJson)
        untyped as ParsingSuccess<RequestJsonRpc<JsonElement>, *>
        val untypedResult = untyped.message as ClientRequestJsonRpc<JsonElement>
        assertEquals(untypedResult.method, "sum")
        val typed = untypedResult.parseRequestParams(Params.serializer().tree)
        typed as ParsingError
        val error = ErrorJsonRpc(typed.toErrorObject(), untypedResult.id)
        val errorSaver = ErrorJsonRpcSerialSaver(StringSerializer)
        val resultJson = saveErrorJsonRpc(errorSaver, error)
        val resultLoader =
            ResponseJsonRpcLoader.withParsers(
                Result.serializer().tree,
                StringParser
            )
        val loadedResult = loadResponseJsonRpc(resultLoader, resultJson)
        assertEquals(
            loadedResult,
            ErrorJsonRpc(
                error = JsonRpcErrorObject.invalidParams("Unable to load params: Field i1 is required, but it was missing"),
                id = JsonRpcID("UUID-123")
            ).coerceResultType()
        )
    }

    private inline fun <reified T : JsonElement> expectValidRequest(
        method: String, id: JsonRpcID, json: String
    ) {
        val parsed = loadRequestJsonRpc(json)
        val result: RequestJsonRpc<JsonElement>
        when (parsed) {
            is ParsingError -> fail("Should have had valid result.")
            is ParsingSuccess<RequestJsonRpc<JsonElement>, *> -> result = parsed.message
        }

        result as ClientRequestJsonRpc<JsonElement>
        assertTrue { result.method == method && result.id == id }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : JsonElement> expectValidNotification(
        method: String, json: String
    ) {
        val parsed = loadRequestJsonRpc(json)
        val result: RequestJsonRpc<JsonElement>
        when (parsed) {
            is ParsingError -> fail("Should have had valid result.")
            is ParsingSuccess<RequestJsonRpc<JsonElement>, *> -> result = parsed.message
        }

        result as NotificationJsonRpc<JsonElement>
        assertTrue { result.method == method }
        assertTrue { result.params is T }
    }

    private inline fun <reified T : ParsingError> expectError(json: String) {
        val parsed = loadRequestJsonRpc(json)
        when (parsed) {
            !is ParsingError -> fail("Should have had error result")
            else -> assertTrue { parsed is T }
        }
    }

    private fun expectInvalidParamsError(loader: KSerialLoader<*>, json: String) {
        val parsed = loadRequestJsonRpc(json) as ParsingSuccess<RequestJsonRpc<JsonElement>, *>
        val parsedMessage = parsed.message as ClientRequestJsonRpc<JsonElement>
        val typed = parsedMessage.parseRequestParams(loader.tree)
        when (typed) {
            !is ParsingError -> fail("Should have had error result")
            else -> assertTrue {
                @Suppress("USELESS_CAST")
                (typed as ParsingError) is InvalidParams
            }
        }
    }
}

