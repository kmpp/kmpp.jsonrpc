package com.github.kmpp.jsonrpc

import com.github.kmpp.jsonrpc.jsonast.JSON
import com.github.kmpp.jsonrpc.jsonast.JsonElement
import com.github.kmpp.jsonrpc.jsonast.JsonObject
import com.github.kmpp.jsonrpc.jsonast.JsonString
import kotlinx.serialization.KInput
import kotlinx.serialization.SerializationException

inline fun <reified E : JsonElement> JsonObject.getRequired(
    getElem: JsonObject.(String) -> JsonElement,
    key: String
): E {
    if (key !in this) {
        throw SerializationException("Did not find \"$key\" in tree")
    }

    val elem = this.getElem(key)
    return when (elem) {
        is E -> elem
        else -> throw SerializationException("$elem JSON type did not match expected ${E::class}")
    }
}

fun JsonObject.checkJsonrpc() {
    this.getRequired<JsonString>(JsonObject::getAsValue, "jsonrpc").let { jsonString ->
        require(jsonString.str == JSON_RPC) {
            "$JSON_RPC is only supported non-null value for \"jsonrpc\""
        }
    }
}

inline fun <reified E : JsonElement> KInput.to(): E {
    val jsonReader = this as? JSON.JsonInput
            ?: throw SerializationException("This class can be loaded only by JSON")
    return jsonReader.readAsTree() as? E
            ?: throw SerializationException("Expected ${E::class} but had ${jsonReader.readAsTree()}")
}

/*
internal fun String.unstringify(portion: String) =
    replace(JSON.stringify(StringSerializer, portion), portion)

internal fun String.prestringify(unquotedName: String): String {
    val afterName = substringAfter("\"$unquotedName\"")
    if (afterName == this) { // There is no section to pre-stringify
        return this
    }

    var section = afterName.substringAfter(':').trim()
    val sectionStartIdx = this.length - section.length
    val sectionLength = countJsonChars(section)
    section = section.take(sectionLength)
    val toBeReplaced = section.trim()
    val sectionEndIdx = sectionStartIdx + sectionLength

    return this.take(sectionStartIdx) +
            this.substring(sectionStartIdx, sectionEndIdx)
                .replace(toBeReplaced, JSON.stringify(StringSerializer, toBeReplaced)) +
            this.substring(sectionEndIdx)
}

private fun countJsonChars(json: String): Int {
    val first = json.first()
    if (first != '"' && first != '{' && first != '[') {
        return json.substringBefore(',').trim().length
    }

    var isEscaped = false
    var isInString = false
    var openArrays = 0
    var openObjects = 0
    var sectionLength = 0
    for ((idx, c) in json.withIndex()) {
        if (isEscaped) {
            isEscaped = false
            continue
        }

        if (c == ' ') {
            continue
        }

        if (isInString) {
            when (c) {
                '"' -> isInString = false
                '\\' -> isEscaped = true
            }
        } else {
            when (c) {
                '"' -> isInString = true
                '{' -> openObjects++
                '}' -> openObjects--
                '[' -> openArrays++
                ']' -> openArrays--
            }
        }

        if (!isInString && openObjects == 0 && openArrays == 0) {
            sectionLength = idx + 1
            break
        }
    }

    return sectionLength
}

internal fun String.isJson() = isStructuredJson() || isNonStructuredJson()

internal fun String.isStructuredJson() = boundedBy('[', ']') || boundedBy('{', '}')

internal fun String.isNonStructuredJson() =
    isQuotedString() || isJsonBoolean() || isJsonNull() || isJsonNumber()

internal fun String.boundedBy(f: Char, l: Char) = length >= 2 && first() == f && last() == l

internal fun String.isQuotedString() = boundedBy('"', '"')

internal fun String.isJsonBoolean() = this == "true" || this == "false"

internal fun String.isJsonNull() = this == "null"

internal fun String.isJsonNumber(): Boolean {
    val asDoubleOrNull = toDoubleOrNull()
    return asDoubleOrNull?.isFinite() ?: false
}

internal fun String.isJsonInteger(): Boolean = toLongOrNull() != null

internal fun String.getRawID() =
    this.substringAfterLast(',')
        .substringAfter("\"id\"")
        .substringAfter(':')
        .substringBeforeLast('}')
        .trim()
        */
