package com.github.kmpp.jsonrpc.jsonast

actual fun CharArray.createString(length: Int): String =
    joinToString(separator = "", limit = length, truncated = "")
