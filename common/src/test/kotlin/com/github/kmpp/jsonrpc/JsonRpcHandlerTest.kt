package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ResponseSaver
import com.github.kmpp.jsonrpc.internal.toStringJson
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.map
import kotlin.test.Test

class JsonRpcHandlerTest {

    @Serializable
    data class IntInput(val input: Int = 0)

    object MathOpsRpcRequestHandler : JsonRpcRequestHandler<JsonElement, Nothing> {
        private val intResultSaver = ResponseSaver(IntSerializer)

        var outcome = Int.MIN_VALUE

        override fun handleClientRequest(request: ClientRequest<JsonElement>): Output<Nothing> {
            println("**> REQUEST : ${request.method} ${request.params}")

            val id = request.id
            val params: JsonElement
            if (request.params == null) {
                val error = invalidParams(request.params.toStringJson()).toError(id)
                return handleError(error)
            } else {
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                params = request.params!!
            }

            val result: Result<Int> = when (request.method) {

                "subtract" -> when (params) {
                    is JsonArray -> {
                        val inputs: List<Int> = IntReader.array.invoke(params)
                        if (inputs.size != 2) {
                            val error = invalidParams(params.toStringJson()).toError(id)
                            return handleError(error)
                        } else {
                            Result(inputs[0] - inputs[1], id)
                        }
                    }
                    is JsonObject -> {
                        val inputs: Map<String, Int> =
                            (StringSerializer to IntSerializer).map.tree.invoke(params)
                        if (inputs["minuend"] == null || inputs["subtrahend"] == null) {
                            val error = invalidParams(params.toStringJson()).toError(id)
                            return handleError(error)
                        }

                        Result(inputs["minuend"]!! - inputs["subtrahend"]!!, id)
                    }
                    else -> {
                        return handleError(invalidParams(params.toStringJson()).toError(id))
                    }
                }

                "sum" -> {
                    Result(IntReader.array.invoke(params).sum(), id)
                }

                "square" -> {
                    val readParamsOutcome = request.parseParams(IntInput.serializer().tree)
                    when (readParamsOutcome) {
                        is ReadFailure -> {
                            return handleError(readParamsOutcome.errorJson)
                        }
                        is ReadSuccess<Request<IntInput>> -> {
                            val intInput = readParamsOutcome.parsed.params!! // null already handled
                            val input: Int = intInput.input
                            Result(input * input, id)
                        }
                    }
                }

                else -> {
                    return handleError(invalidParams(params.toStringJson()).toError(id))
                }
            }

            outcome = result.result
            println("<** RESULT  : ${result.result}")
            return Output(jsonResponse = saveResult(intResultSaver, result))
        }

        override fun handleNotification(notification: Notification<JsonElement>): Output<Nothing> {
            println("**> NOTIFY  : ${notification.method} ${notification.params}")
            return Output()
        }
    }

    @Test
    fun testMathOpsRpcRequestHandler() {
        val handler = MathOpsRpcRequestHandler
        handler.handle("""{"jsonrpc":"2.0","method":"sum","params":[1,2,3,4,5],"id":"UUID-1"}""")
    }

    private fun MathOpsRpcRequestHandler.handle(json: String) {
        println("--> REQUEST : $json")
        println("<-- RESPONSE: ${handleRequest(json)}")
    }
}
