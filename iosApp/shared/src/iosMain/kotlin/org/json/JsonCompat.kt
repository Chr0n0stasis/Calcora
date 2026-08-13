package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

private fun Any?.asJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is JSONObject -> element()
    is JSONArray -> element()
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

class JSONObject internal constructor(private val values: MutableMap<String, JsonElement>) {
    constructor() : this(mutableMapOf())
    constructor(raw: String) : this(
        (parser.parseToJsonElement(raw) as JsonObject).toMutableMap()
    )

    internal fun element(): JsonObject = JsonObject(values)

    fun put(key: String, value: Any?): JSONObject = apply { values[key] = value.asJson() }
    fun optString(key: String, fallback: String = ""): String =
        (values[key] as? JsonPrimitive)?.contentOrNull ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean =
        (values[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
    fun optDouble(key: String, fallback: Double = Double.NaN): Double =
        (values[key] as? JsonPrimitive)?.doubleOrNull ?: fallback
    fun optInt(key: String, fallback: Int = 0): Int =
        (values[key] as? JsonPrimitive)?.intOrNull ?: fallback
    fun optLong(key: String, fallback: Long = 0L): Long =
        (values[key] as? JsonPrimitive)?.longOrNull ?: fallback
    fun optJSONArray(key: String): JSONArray? =
        (values[key] as? JsonArray)?.let(::JSONArray)
    fun optJSONObject(key: String): JSONObject? =
        (values[key] as? JsonObject)?.let { JSONObject(it.toMutableMap()) }
    override fun toString(): String = element().toString()
}

class JSONArray internal constructor(private val values: MutableList<JsonElement>) {
    internal constructor(value: JsonArray) : this(value.toMutableList())
    constructor() : this(mutableListOf())
    constructor(raw: String) : this(
        (parser.parseToJsonElement(raw) as JsonArray).toMutableList()
    )

    internal fun element(): JsonArray = JsonArray(values)
    fun length(): Int = values.size
    fun put(value: Any?): JSONArray = apply { values += value.asJson() }
    fun getJSONArray(index: Int): JSONArray = JSONArray(values[index] as JsonArray)
    fun getJSONObject(index: Int): JSONObject = JSONObject((values[index] as JsonObject).toMutableMap())
    fun optJSONObject(index: Int): JSONObject? =
        (values.getOrNull(index) as? JsonObject)?.let { JSONObject(it.toMutableMap()) }
    fun getDouble(index: Int): Double = (values[index] as JsonPrimitive).double
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double =
        (values.getOrNull(index) as? JsonPrimitive)?.doubleOrNull ?: fallback
    fun isNull(index: Int): Boolean = values.getOrNull(index) == null || values[index] is JsonNull
    override fun toString(): String = element().toString()
}
