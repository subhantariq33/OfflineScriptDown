

package com.mugames.vidsnapkit.network

import com.mugames.vidsnapkit.toJsonString
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import java.util.*
import java.util.regex.Pattern


interface HttpInterface {
    suspend fun getData(url: String, headers: Hashtable<String, String>? = null): String?
    suspend fun getSize(url: String, headers: Hashtable<String, String>? = null): Long

    suspend fun postData(
        url: String,
        postData: Hashtable<String, Any>? = null,
        headers: Hashtable<String, String>? = null
    ): String

    suspend fun checkWebPage(url: String, headers: Hashtable<String, String>?): Boolean
}

class HttpInterfaceImpl(
    private val client: HttpClient,
) : HttpInterface {

    private val redirectionStatusCode = setOf(
        HttpStatusCode.MovedPermanently,
        HttpStatusCode.Found,
        HttpStatusCode.TemporaryRedirect
    )

    override suspend fun postData(
        url: String,
        postData: Hashtable<String, Any>?,
        headers: Hashtable<String, String>?
    ): String {
        return try {
            client.post {
                url(url)
                headers?.let {
                    if (it.isNotEmpty())
                        headers {
                            for ((key, value) in it)
                                append(key, value)
                        }
                }
                postData?.let {
                    setBody(TextContent(it.toJsonString(), ContentType.Application.Json))
                }
            }.bodyAsText()
        } catch (e: Error) {
            throw e
        }
    }

    // Instagram Server crashes with 500 if we sent wrong cookies
    // So it is tackled by hardcoding and making it as true to prevent NonFatal Error
    override suspend fun checkWebPage(url: String, headers: Hashtable<String, String>?): Boolean {
        val acceptedStatusCode = setOf(
            HttpStatusCode.OK,
            HttpStatusCode.Accepted,
            HttpStatusCode.Created,
            HttpStatusCode.NonAuthoritativeInformation,
            HttpStatusCode.NoContent,
            HttpStatusCode.PartialContent,
            HttpStatusCode.ResetContent,
            HttpStatusCode.MultiStatus,
            if (url.contains("instagram")) HttpStatusCode.InternalServerError else HttpStatusCode.OK
        )

        return try {
            client.get {
                url(url)
                headers?.let {
                    if (it.isNotEmpty())
                        headers {
                            for ((key, value) in it)
                                append(key, value)
                        }
                }
            }.run {
                status in acceptedStatusCode || run {
                    if (status in redirectionStatusCode) {
                        val res = getLastPossibleRedirectedResponse(this, headers)
                        return res.status in acceptedStatusCode || res.status in redirectionStatusCode
                    }
                    false
                }
            }
        } catch (e: ClientRequestException) {
            false
        }
    }

    override suspend fun getData(url: String, headers: Hashtable<String, String>?): String? {
        return try {
            client.get {
                url(url)
                headers?.let {
                    if (it.isNotEmpty()) {
                        headers {
                            for ((key, value) in it)
                                append(key, value)
                        }
                    }
                }
            }.run {
                if (status == HttpStatusCode.OK)
                    body()
                else if (status in redirectionStatusCode) {
                    getLastPossibleRedirectedResponse(this, headers).body()
                } else if (url.contains("instagram") && status == HttpStatusCode.InternalServerError) "{error:\"Invalid Cookies\"}"
                else null
            }
        } catch (e: ClientRequestException) {
            null
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getSize(url: String, headers: Hashtable<String, String>?): Long {
        return client.request {
            method = HttpMethod.Head
            url(url)
        }.run {
            if (status == HttpStatusCode.OK)
                this.headers["content-length"]?.toLong() ?: Long.MIN_VALUE
            else Long.MIN_VALUE
        }
    }

    private suspend fun getLastPossibleRedirectedResponse(
        response: HttpResponse,
        headers: Hashtable<String, String>?
    ): HttpResponse {
        var cacheResponse = response
        do {
            var locationUrl = cacheResponse.headers[HttpHeaders.Location]!!

            val matcher = Pattern.compile("^(?:https?:\\/\\/)?(?:[^@\\n]+@)?(?:www\\.)?([^:\\/\\n?]+)")
                .matcher(locationUrl)
            if (!matcher.find())
                locationUrl = cacheResponse.request.url.protocolWithAuthority + locationUrl
            val nonRedirectingClient = HttpClient(Android) {
                followRedirects = false
            }
            val tempResponse = nonRedirectingClient.get(locationUrl) {
                this.headers {
                    headers?.let {
                        for ((key, value) in it)
                            append(key, value)
                    }
                }
            }
            if (cacheResponse.request.url == tempResponse.request.url)
                break
            cacheResponse = tempResponse
        } while (cacheResponse.status in redirectionStatusCode)
        return if (cacheResponse.request.url.host == "localhost") response else cacheResponse
    }
}
