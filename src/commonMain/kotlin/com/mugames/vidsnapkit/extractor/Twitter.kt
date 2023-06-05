

package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.*
import com.mugames.vidsnapkit.dataholders.Formats
import com.mugames.vidsnapkit.dataholders.ImageResource
import com.mugames.vidsnapkit.dataholders.VideoResource
import com.mugames.vidsnapkit.network.HttpRequest
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Matcher
import java.util.regex.Pattern




class Twitter internal constructor(url: String) : Extractor(url) {
    var TAG = Statics.TAG + ":Twitter"

    var base_url = "https://api.twitter.com/1.1/"
    var query =
        "?cards_platform=Web-12&include_cards=1&include_reply_count=1&include_user_entities=0&tweet_mode=extended"

    private var tweetID: String? = null

    var localFormats = Formats()


    var auth =
        "Bearer AAAAAAAAAAAAAAAAAAAAAPYXBAAAAAAACLXUNDekMxqa8h%2F40K4moUkGsoc%3DTYfbDKbT3jJPCEVnMYqilB28NHfOPqkca3qaAxGfsyKCs0wRbw"


    var info: JSONObject? = null


    override suspend fun analyze() {
        localFormats.url = inputUrl
        localFormats.src = "Twitter"
        headers["Authorization"] = auth
        tweetID = getTweetID()
        tweetID?.let {
            getToken()
        } ?: run {
            clientRequestError("Sorry!! Tweet doesn't exist")
        }
    }

    private fun getTweetID(url: String = inputUrl): String? {
        var matcher = Pattern.compile("(?:(?:i/web|[^/]+)/status|statuses)/(\\d+)").matcher(url)
        if (matcher.find())
            return matcher.group(1)
        matcher = Pattern.compile("i/broadcasts/([0-9a-zA-Z]{13})").matcher(url)
        return if (matcher.find())
            matcher.group(1)
        else null
    }


    private suspend fun getToken() {
        val response = HttpRequest(base_url + "guest/activate.json", headers).postRequest()
        headers["x-guest-token"] = response.toJSONObject().getString("guest_token")
        if (inputUrl.contains("status")) extractVideo()
        if (inputUrl.contains("broadcasts")) extractBroadcasts()
    }

    private suspend fun extractVideo() {
        val response = HttpRequest("${base_url}statuses/show/${tweetID}.json$query", headers).getResponse()
        response?.let {
            identifyDownloader(it)
        } ?: run {
            clientRequestError("Fetch failed for ${base_url}statuses/show/${tweetID}.json$query")
        }
    }

    private suspend fun identifyDownloader(response: String) {
        val jsonObject = response.toJSONObjectOrNull()
        if (jsonObject == null) {
            internalError("Can't convert $response as JSONObject at identifyDownloader")
            return
        }
        localFormats.title = jsonObject.getString("full_text").split("[→]")[0]
        val extendedEntities = jsonObject.getNullableJSONObject("extended_entities")
        extendedEntities?.let {
            val media: JSONObject = it.getJSONArray("media").getJSONObject(0)
            fromVideoInfo(media)
            finalize()
        } ?: run {
            val card = jsonObject.getNullableJSONObject("card")
            card?.let {
                val bindingValues: JSONObject = it.getJSONObject("binding_values")
                fun getBindingValue(filed: String?): String? {
                    val jsonField = filed?.let { it1 -> bindingValues.getNullableJSONObject(it1) }
                    return jsonField?.let {
                        jsonField.getNullableString("type")?.lowercase()?.plus("_value")
                            ?.let { it1 -> jsonField.getNullableString(it1) }
                    }
                }

                val strings = it.getString("name").split(":")
                when (val cardName = strings[strings.size - 1]) {
                    "player" -> getBindingValue("player_url")?.let { it1 -> Twitch(it1).extractURLFromClips() }

                    "periscope_broadcast" -> {
                        var periscopeUrl = getBindingValue("url")
                        if (periscopeUrl.isNullOrBlank())
                            periscopeUrl = getBindingValue("player_url")
                        periscopeUrl?.let {
                            localFormats = Periscope(periscopeUrl).extractInfo()
                            finalize()
                        }
                    }

                    "broadcast" -> {
                        tweetID = getBindingValue("broadcast_url")?.let { it1 -> getTweetID(it1) }
                        extractBroadcasts()
                    }

                    "unified_card" -> {
                        val unifiedCard = JSONObject(getBindingValue("unified_card"))
                        val mediaEntities: JSONObject = unifiedCard.getJSONObject("media_entities")
                        val media: JSONObject = mediaEntities.getJSONObject(mediaEntities.names().getString(0))
                        fromVideoInfo(media)
                        finalize()
                    }

                    else -> {
                        val isAmplify = cardName == "amplify"
                        val vmapUrl: String? = if (isAmplify) getBindingValue("amplify_url_vmap")
                        else getBindingValue("player_stream_url")
                        for (s1 in arrayOf("_original", "_x_large", "_large", "", "_small")) {
                            val jsonString = getBindingValue("player_image$s1")
                            if (jsonString == null) {
                                clientRequestError("This media can't be downloaded. It may be a retweet so paste URL of main tweet")
                                return
                            }
                            val imgUrl = jsonString.toJSONObject().getNullableString("url")
                            if (imgUrl != null && !imgUrl.contains("/player-placeholder")) {
                                localFormats.imageData.add(ImageResource(imgUrl))
                                break
                            }
                        }
                        vmapUrl?.let { it1 -> fromVMap(it1) }
                    }
                }
            } ?: run {
                clientRequestError("This media can't be downloaded. It may be a retweet so paste URL of main tweet")
            }
        }
    }

    private suspend fun fromVMap(url: String) {
        val response = HttpRequest(url).getResponse();
        response?.let {
            var matcher: Matcher =
                Pattern.compile("(?<=<tw:videoVariants>)[\\s\\S]*(?=</tw:videoVariants>)")
                    .matcher(response)
            if (matcher.find()) {
                var grp = matcher.group(0)
                grp = grp.trim { it <= ' ' }
                for (s in grp.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    matcher = Pattern.compile("(?<=url=\")(.*?)\".*(?<=content_type=\")(.*?)\"")
                        .matcher(s)
                    if (matcher.find()) {
                        if (matcher.group(2).equals(MimeType.VIDEO_MP4)) {
                            val u = Util.decodeHTML(matcher.group(1))!!
                            localFormats.videoData.add(
                                VideoResource(
                                    u,
                                    MimeType.VIDEO_MP4,
                                    resolution(u)
                                )
                            )
                        }
                    }
                }
                finalize()
            }
        } ?: run {
            clientRequestError("Url unable to fetch for url $url")
        }

    }

    private fun fromVideoInfo(media: JSONObject) {
        val videoInfo = media.getNullableJSONObject("video_info")
        val variants = videoInfo?.getJSONArray("variants")
        variants?.let {
            for (i in 0 until variants.length()) {
                val data: JSONObject = variants.getJSONObject(i)
                if (data.getString("content_type").equals(MimeType.VIDEO_MP4)) {
                    val s: String = data.getString("url")
                    localFormats.videoData.add(
                        VideoResource(
                            s,
                            MimeType.VIDEO_MP4,
                            quality = resolution(s)
                        )
                    )
                }
            }
        }
        try {

            var thumbNailURL: String = media.getString("media_url")
            if (thumbNailURL.isEmpty()) thumbNailURL = media.getString("media_url_https")
            if (thumbNailURL.startsWith("http:/")) thumbNailURL =
                thumbNailURL.replace("http:/".toRegex(), "https:/")
            localFormats.imageData.add(ImageResource(thumbNailURL))
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override suspend fun finalize() {
        videoFormats.add(localFormats)
        super.finalize()
    }


    fun resolution(url: String?): String {
        val pattern: Pattern = Pattern.compile("/(\\d+x\\d+)/")
        val matcher: Matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else "--"
    }

    private suspend fun extractBroadcasts() {
        val response = HttpRequest("${base_url}broadcasts/show.json?ids=$tweetID").getResponse()
        response?.let {
            parseBroadcastResponse(response)
        } ?: run {
            clientRequestError("Error while ${base_url}broadcasts/show.json?ids=$tweetID")
        }
    }

    private suspend fun parseBroadcastResponse(response: String) {
        val broadcasts: JSONObject = response.toJSONObject().getJSONObject("broadcasts").getJSONObject(tweetID)
        info = extractInfo(broadcasts)
        val mediaKey: String = broadcasts.getString("media_key")
        val res = HttpRequest("${base_url}live_video_stream/status/$mediaKey", headers).getResponse()
        res?.let {
            val matcher: Matcher = Pattern.compile("\\{[\\s\\S]+\\}").matcher(res)
            matcher.find()
            val response1 = matcher.group(0)
            val source: JSONObject = JSONObject(response1).getJSONObject("source")
            var m3u8Url = source.getNullableString("noRedirectPlaybackUrl")
            if (!m3u8Url.isNullOrBlank()) m3u8Url = source.getString("location")
            if (m3u8Url.toString().contains("/live_video_stream/geoblocked/")) {
                clientRequestError("Geo restricted")
            }
        } ?: run {
            clientRequestError("Unable to get response to ${base_url}live_video_stream/status/$mediaKey")
        }
    }

    private fun extractInfo(broadcast: JSONObject): JSONObject? {
        return try {
            var title = broadcast.getNullableString("status")
            val uploader = broadcast.getNullableString("user_display_name")
            title = String.format("%s - %s", uploader, title)
            var thumbnail: String? = null
            for (img in arrayOf("image_url_medium", "image_url_small", "image_url")) {
                thumbnail = broadcast.getString(img)
                if (thumbnail.isNotEmpty()) break
            }
            val resolution = "${broadcast["width"]}x${broadcast["height"]}"
            val isLive = "ENDED" != broadcast.getString("state")
            val js = String.format(
                "{\"title\":\"%s\",\"thumbNailURL\":\"%s\",\"isLive\":\"%s\",\"resolution\":\"%s\"}",
                title,
                thumbnail,
                isLive,
                resolution
            )
            js.toJSONObject()
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }
}

