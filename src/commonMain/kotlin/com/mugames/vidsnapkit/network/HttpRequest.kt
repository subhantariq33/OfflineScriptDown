

package com.mugames.vidsnapkit.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*



/**
 * Used to make HTTP request
 *
 * @param url Endpoint URL to make HTTP request
 * @param headers Key and Value a pair of Header for HTTP request(optional).
 */
class HttpRequest(
    private val url: String,
    private val headers: Hashtable<String, String>? = null,
) {
    private companion object {
        fun createClient(requiresRedirection: Boolean = true): HttpInterface {
            return HttpInterfaceImpl(HttpClient(Android) {
                followRedirects = requiresRedirection
            })
        }
    }

    /**
     * Fetches plain text for given url
     *
     * @return Text format of entire webpage for given [url]
     */
    suspend fun getResponse(needsRedirection: Boolean = true): String? = withContext(Dispatchers.IO) { createClient(needsRedirection).getData(url, headers) }

    /**
     * Used to estimate size of given url in bytes
     *
     * @return bytes count of given [url]
     */
    suspend fun getSize() = createClient().getSize(url)

    suspend fun postRequest(postData: Hashtable<String, Any>? = null): String =
        withContext(Dispatchers.IO) { createClient().postData(url, postData, headers) }

    suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) { createClient(false).checkWebPage(url, headers) }

}
