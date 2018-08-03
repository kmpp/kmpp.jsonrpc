package com.github.kmpp.jsonrpc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTreeParser

/**
 * Lifted from source: https://github.com/Kotlin/kotlinx.serialization/blob/master/json/common/test/kotlinx/serialization/json/examples/Parsers.kt
 */

abstract class JsonWriter<T : Any> {
    abstract fun write(value: T): JsonElement

    fun writeNullable(value: T?): JsonElement {
        if (value == null) return JsonNull
        return write(value)
    }
}

abstract class JsonReader<T : Any> {
    abstract fun read(json: JsonElement): T

    fun readNullable(json: JsonElement): T? {
        if (json.isNull) return null
        return read(json)
    }

    fun read(string: String): T = read(JsonTreeParser(string).readFully())
}
