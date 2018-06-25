package com.github.kmpp.jsonrpc.internal

import com.github.kmpp.jsonrpc.ClientRequest
import com.github.kmpp.jsonrpc.JsonRpcRequestHandler
import com.github.kmpp.jsonrpc.Notification
import com.github.kmpp.jsonrpc.ReadFailure
import com.github.kmpp.jsonrpc.ReadOutcome
import com.github.kmpp.jsonrpc.ReadSuccess
import com.github.kmpp.jsonrpc.Request
import com.github.kmpp.jsonrpc.Response
import com.github.kmpp.jsonrpc.load

internal fun <P, R> JsonRpcRequestHandler<P, R>.readAndHandleRequest0(json: String): Response<R>? {
    val readOutcome: ReadOutcome<Request<P>> = requestReader.load(json)
    return when (readOutcome) {
        is ReadFailure -> {
            handleError(readOutcome.failure.error)
        }
        is ReadSuccess<Request<P>> -> {
            val request = readOutcome.success
            when (request) {
                is ClientRequest<P> -> handleClientRequest(request)
                is Notification<P> -> {
                    handleNotification(request); null
                }
            }
        }
    }
}
