package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.ResponseSaver
import com.github.kmpp.jsonrpc.internal.toStringJson
import com.github.kmpp.jsonrpc.jsonast.JsonArray
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver
import kotlinx.serialization.Serializable
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.map
import kotlin.test.Test

class JsonRpcHandlerTest {

    @Serializable
    data class IntInput(val input: Int = 0)

    object MathOpsRpcRequestHandler : JsonRpcRequestHandler<JsonElement, Int> {
        override val requestReader: KSerialLoader<Request<JsonElement>>
                = getRequestLoader { it -> it }
        override val resultWriter: KSerialSaver<Response<Int>> = ResponseSaver(IntSerializer)

        override fun handleRequest(request: Request<JsonElement>): Response<Int>? {
            return when (request) {
                is ClientRequest<JsonElement> -> handleClientRequest(request)
                is Notification<JsonElement> -> {
                    handleNotification(request)
                    return null
                }
            }
        }

        override fun handleClientRequest(clientRequest: ClientRequest<JsonElement>): Response<Int> {
            println("**> REQUEST : ${clientRequest.method} ${clientRequest.params}")

            val id = clientRequest.id
            val params: JsonElement
            if (clientRequest.params == null) {
                val error = invalidParams(clientRequest.params.toStringJson()).toError(id)
                return handleError(error)
            } else {
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                params = clientRequest.params!!
            }

            val result: Result<Int> = when (clientRequest.method) {

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
                    val readParamsOutcome = clientRequest.parseParams(IntInput.serializer().tree)
                    when (readParamsOutcome) {
                        is ReadFailure -> {
                            return handleError(readParamsOutcome.failure.error)
                        }
                        is ReadSuccess<Request<IntInput>> -> {
                            val intInput = readParamsOutcome.success.params!! // null handled above
                            val input: Int = intInput.input
                            Result(input * input, id)
                        }
                    }
                }

                else -> {
                    return handleError(invalidParams(params.toStringJson()).toError(id))
                }
            }

            val outcome = result.result
            println("<** RESULT  : ${result.result}")
            return Result(outcome, id)
        }

        override fun handleNotification(notification: Notification<JsonElement>) {
            println("**> NOTIFY  : ${notification.method} ${notification.params}")
        }
    }

    @Test
    fun testMathOpsRpcRequestHandler() {
        val handler = MathOpsRpcRequestHandler
        handler.handle("""{"jsonrpc":"2.0","method":"sum","params":[1,2,3,4,5],"id":"UUID-1"}""")
    }

    private fun MathOpsRpcRequestHandler.handle(json: String) {
        println("--> REQUEST : $json")
        println("<-- RESPONSE: ${readAndHandleRequest(json)}")
    }
}
