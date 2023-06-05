

package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.MimeType
import com.mugames.vidsnapkit.dataholders.Error
import com.mugames.vidsnapkit.dataholders.Formats
import com.mugames.vidsnapkit.dataholders.Result
import com.mugames.vidsnapkit.dataholders.VideoResource
import com.mugames.vidsnapkit.getNullableString
import com.mugames.vidsnapkit.network.HttpRequest
import com.mugames.vidsnapkit.toJSONObject
import com.mugames.vidsnapkit.toJSONObjectOrNull
import org.json.JSONObject
import java.util.regex.Pattern




class Periscope internal constructor(url: String) : Extractor(url) {

    private val localFormats = Formats()

    override suspend fun analyze() {
        TODO("Not yet implemented")
    }

    var manifest: ArrayList<ArrayList<String>>? = ArrayList()


    var data: JSONObject? = null


    private fun getID(s: String?): String? {
        val matcher = Pattern.compile("https?://(?:www\\.)?(?:periscope|pscp)\\.tv/[^/]+/([^/?#]+)").matcher(s)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    suspend fun extractInfo(): Formats {
        val id = getID(inputUrl)
        id?.let {
            val response =
                HttpRequest("https://api.periscope.tv/api/v2/accessVideoPublic?broadcast_id=$it").getResponse()
            response?.let {
                val stream = response.toJSONObject()
                val broadcast = stream.getJSONObject("broadcast")
                data = extractData(broadcast)
                val videUrls = mutableListOf<String>()
                for (formatId in arrayOf(
                    "replay",
                    "rtmp",
                    "hls",
                    "https_hls",
                    "lhls",
                    "lhlsweb"
                )) {
                    val videoUrl = stream.getNullableString(formatId + "_url")
                    if (videoUrl.isNullOrEmpty() || videUrls.contains(videoUrl)) continue
                    localFormats.videoData.add(VideoResource(videoUrl, MimeType.VIDEO_MP4))
                    if (formatId != "rtmp") {
                        onProgress(Result.Failed(Error.MethodMissingLogic))
                        break
                    }
                }
            } ?: run {
                clientRequestError("Unable to get response for url https://api.periscope.tv/api/v2/accessVideoPublic?broadcast_id=$it")
            }
        } ?: run {
            clientRequestError("Unable to find id from url $inputUrl")
        }
        return localFormats
    }


    private fun extractData(broadcast: JSONObject): JSONObject? {
        var title = broadcast.getNullableString("status")
        var thumbnail: String? = null
        if (title.isNullOrEmpty()) title = "Periscope Broadcast"
        var uploader = broadcast.getNullableString("user_display_name")
        if (uploader.isNullOrEmpty()) uploader = broadcast.getString("username")
        title = String.format("%s - %s", uploader, title)
        for (img in arrayOf("image_url_medium", "image_url_small", "image_url")) {
            thumbnail = broadcast.getString(img)
            if (!thumbnail.isNullOrEmpty()) break
        }
        val isLive = !"ENDED".equals(broadcast.getString("state"), ignoreCase = true)
        val resolution: String = broadcast.getString("width") + "x" + broadcast.getString("height")
        val js = String.format(
            "{\"title\":\"%s\",\"thumbNailURL\":\"%s\",\"isLive\":\"%s\",\"resolution\":\"%s\"}",
            title,
            thumbnail,
            isLive,
            resolution
        )
        return js.toJSONObjectOrNull()
    }

}
