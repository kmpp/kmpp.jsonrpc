package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.internal.readAndHandleRequest0

interface JsonRpcRequestHandler<P : Any, R : Any> {
    val requestReader: JsonReader<Request<P>>
    val resultWriter: JsonWriter<Response<R>>

    fun readAndHandleRequest(json: String): Response<R>? = readAndHandleRequest0(json)

    fun writeResponse(response: Response<R>): String = resultWriter.write(response).toString()

    fun readRequest(json: String): ReadOutcome<Request<P>> = requestReader.readOutcome(json)

    fun handleRequest(request: Request<P>): Response<R>?

    fun handleClientRequest(clientRequest: ClientRequest<P>): Response<R>

    fun handleNotification(notification: Notification<P>)

    // Override to implement any custom error handling or transformations to custom error objects
    fun handleError(error: Error): Error = error
}

interface JsonRpcRequestResponder<P : Any, R : Any> {
    val handler: JsonRpcRequestHandler<P, R>

    fun respondToRequest(json: String): String? {
        val response = handler.readAndHandleRequest(json)
        return response?.let { handler.writeResponse(it) }
    }
}
