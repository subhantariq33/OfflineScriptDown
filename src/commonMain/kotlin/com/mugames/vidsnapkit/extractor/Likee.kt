

package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.MimeType
import com.mugames.vidsnapkit.dataholders.*
import com.mugames.vidsnapkit.network.HttpRequest
import com.mugames.vidsnapkit.toHashtable
import com.mugames.vidsnapkit.toJSONObject
import org.json.JSONArray
import java.util.regex.Pattern


/**
 * @author Udhaya
 * Created on 13-02-2023
 */

class Likee internal constructor(url: String) : Extractor(url) {

    companion object {
        private const val GET_VIDEO_INFO = "https://api.like-video.com/likee-activity-flow-micro/videoApi/getVideoInfo"
    }

    private val formats = Formats()

    override suspend fun analyze() {
        formats.src = "Likee"
        formats.url = inputUrl
        val postId = getPostId() ?: run {
            clientRequestError()
            return
        }
        val response = HttpRequest(GET_VIDEO_INFO).postRequest(hashMapOf("postIds" to postId).toHashtable())
        val responseData =
            response.toJSONObject()
                .getJSONObject("data")
        responseData.getJSONArray("videoList")?.let {
            extractVideoList(it)
        } ?: run {
            onProgress(Result.Failed(Error.MethodMissingLogic))
        }
    }

    private suspend fun extractVideoList(jsonArray: JSONArray) {
        for (i in 0 until jsonArray.length()) {
            val localFormats = formats.copy(title = "", videoData = mutableListOf(), imageData = mutableListOf())
            val currentObj = jsonArray.getJSONObject(i)
            localFormats.videoData.add(
                VideoResource(
                    currentObj.getString("videoUrl"),
                    MimeType.VIDEO_MP4,
                    "${currentObj.getInt("videoWidth")}x${currentObj.getInt("videoHeight")}"
                )
            )

            localFormats.title =
                currentObj.getString("title")?.ifEmpty { currentObj.getString("msgText") } ?: "Likee_Video"
            localFormats.imageData.add(
                ImageResource(
                    currentObj.getString("coverUrl")
                )
            )
            videoFormats.add(localFormats)
        }
        finalize()
    }

    private suspend fun getPostId(): String? {
        try {
            val page = HttpRequest(inputUrl).getResponse()
            Pattern.compile("<meta property=\"og:url\"\\W+content=\".*?postId=(.*?)\"").matcher(page).apply {
                return if (find()) group(1) else null
            }
        } catch (e: kotlin.Error) {
            return null
        }
    }
}