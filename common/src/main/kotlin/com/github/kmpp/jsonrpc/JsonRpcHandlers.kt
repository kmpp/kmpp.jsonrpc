package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.readAndHandleRequest0
import kotlinx.serialization.KSerialLoader
import kotlinx.serialization.KSerialSaver

interface JsonRpcRequestHandler<P, R> {
    val requestReader: KSerialLoader<Request<P>>
    val resultWriter: KSerialSaver<Response<R>>

    fun readAndHandleRequest(json: String): Response<R>? = readAndHandleRequest0(json)

    fun writeResponse(response: Response<R>): String = resultWriter.save(response)

    fun readRequest(json: String): ReadOutcome<Request<P>> = requestReader.load(json)

    fun handleRequest(request: Request<P>): Response<R>?

    fun handleClientRequest(clientRequest: ClientRequest<P>): Response<R>

    fun handleNotification(notification: Notification<P>)

    // Override to implement any custom error handling or transformations to custom error objects
    fun handleError(error: Error): Error = error
}

interface JsonRpcRequestResponder<P, R> {
    val handler: JsonRpcRequestHandler<P, R>

    fun respondToRequest(json: String): String? {
        val response = handler.readAndHandleRequest(json)
        return response?.let { handler.writeResponse(it) }
    }
}
