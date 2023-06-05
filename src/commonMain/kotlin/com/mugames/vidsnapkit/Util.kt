

package com.mugames.vidsnapkit

import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


class Util {
    companion object {
        /**
         * Used to decode HTML
         *
         * @param text encoded HTML
         * @return decoded HTML and `null` if encoding fails
         */
        fun decodeHTML(text: String?): String? {
            if (text == null) return null
            val decoder = HtmlDecoderFactory.createDecoderFactory()
            val decoded = decoder.decodeHtml(text)
//            decoded = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            try {
                return URLDecoder.decode(decoded, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
            return null
        }

        /**
         * To find resolution from given picture URL
         *
         * @param url image URL
         * @return resolution in `width x height` format or `--`
         */
        fun getResolutionFromUrl(url: String): String {
            val matcher = Pattern.compile("([\\d ]{2,5}[x][\\d ]{2,5})").matcher(url)
            if (matcher.find()) return matcher.group(1) ?: "--"
            return "--"
        }

        /**
         * Replaces all white spaces and tabs from title
         *
         * @param name title of video needed to be filtered
         * @return title without newline and white tabs
         */
        fun filterName(name: String) = name.replace("[\n.\t]".toRegex(), "")

        /**
         * Joins two URLs together and returns the result as a String.
         * If an error occurs during the process, the function returns null.
         *
         * @param url the base URL to join with the other URL
         * @param uRL the URL to join with the base URL
         * @return the joined URL as a String, or null if an error occurs
         */
        fun joinURL(url: String?, uRL: String): String? {
            return try {
                val uri = url?.let { URI(it) }
                val joined = uri?.resolve(uRL.trim { it <= ' ' })?.toURL()
                joined?.toString()
            } catch (e: Exception) {
                println("[join] $e")
                null
            }
        }
    }
}

fun String.count(substring: String): Int {
    var count = 0
    var index = 0
    while (index != -1) {
        index = this.indexOf(substring, index)
        if (index != -1) {
            count++
            index += substring.length
        }
    }
    return count
}

fun String.sanitizeAsHeaderValue() = replace("['\n]+".toRegex(), "")

fun <K, V> Map<K, V>.toHashtable(): Hashtable<K, V> {
    val table = Hashtable<K, V>()
    for ((k, v) in this) {
        table[k] = v
    }
    return table
}

fun <K, V> Hashtable<K, V>.toJsonString() = JSONObject(toMap()).toString()

fun Matcher.tryGroup(group: Int) = try {
    group(group)
} catch (e: IndexOutOfBoundsException) {
    null
}

fun Matcher.tryGroup(name: String) = try {
    group(name)
} catch (e: IndexOutOfBoundsException) {
    null
}
