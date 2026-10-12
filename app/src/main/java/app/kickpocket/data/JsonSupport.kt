package app.kickpocket.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

val AppJson = Json { ignoreUnknownKeys = true }
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject
internal fun JsonElement.decodedObject(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonPrimitive -> if (this != JsonNull) runCatching { AppJson.parseToJsonElement(content).asObject() }.getOrNull() else null
    else -> null
}

