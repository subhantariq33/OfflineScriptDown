

package com.mugames.vidsnapkit

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


fun JSONObject.getNullableJSONObject(name: String): JSONObject? = try {
    getJSONObject(name)
} catch (e: JSONException) {
    null
}

fun JSONArray.getNullableJSONObject(index: Int): JSONObject? = try {
    getJSONObject(index)
} catch (e: JSONException) {
    null
}

fun JSONArray.getNullableJSONArray(index: Int): JSONArray? = try {
    getJSONArray(index)
} catch (e: JSONException) {
    null
}

fun JSONObject.getNullableJSONArray(name: String): JSONArray? = try {
    getJSONArray(name)
} catch (e: JSONException) {
    null
}

fun JSONObject.getNullableString(name: String): String? = try {
    getString(name)
} catch (e: JSONException) {
    null
}

fun JSONObject.getNullable(name: String): String? = try {
    get(name).toString()
} catch (e: JSONException) {
    null
}

fun String.toJSONObject() = JSONObject(this.replace("\\x3C", "<"))

fun String.toJSONArray() = JSONArray(this)

fun String.toJSONObjectOrNull() = try {
    toJSONObject()
} catch (e: JSONException) {
    null
}

fun String.toJSONArrayOrNull() = try {
    toJSONArray()
} catch (e: JSONException) {
    null
}
