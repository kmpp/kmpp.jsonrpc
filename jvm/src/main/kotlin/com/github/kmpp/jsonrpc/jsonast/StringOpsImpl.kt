package com.github.kmpp.jsonrpc.jsonast

actual fun CharArray.createString(length: Int): String = String(this, 0, length)
