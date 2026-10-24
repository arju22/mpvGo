package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/**
 * Converts an mpv node tree into a kotlinx [JsonElement] without going through a JSON string.
 * [MPVNode.toJson] only escapes quotes, so backslashes or control characters in track/chapter
 * titles or paths produce invalid JSON (#755).
 */
fun MPVNode.toJsonElement(): JsonElement =
  when (this) {
    is MPVNode.StringNode -> JsonPrimitive(value)
    is MPVNode.BooleanNode -> JsonPrimitive(value)
    is MPVNode.IntNode -> JsonPrimitive(value)
    is MPVNode.DoubleNode -> JsonPrimitive(value)
    is MPVNode.ByteArrayNode -> JsonPrimitive(Base64.encode(value))
    is MPVNode.ArrayNode -> JsonArray(value.map { it.toJsonElement() })
    is MPVNode.MapNode -> JsonObject(value.mapValues { it.value.toJsonElement() })
    else -> JsonNull
  }
