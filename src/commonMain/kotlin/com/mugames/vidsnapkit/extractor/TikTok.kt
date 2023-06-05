

package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.MimeType
import com.mugames.vidsnapkit.dataholders.*
import com.mugames.vidsnapkit.network.HttpRequest
import com.mugames.vidsnapkit.toJSONObject
import java.util.regex.Pattern




class TikTok internal constructor(url: String) : Extractor(url) {

    private val localFormats = Formats()


    override suspend fun analyze() {
        localFormats.src = "TikTok"
        localFormats.url = inputUrl
        val response = HttpRequest(inputUrl).getResponse()
        response?.let {
            extractFromWebPage(response)
        }
    }

    private suspend fun extractFromWebPage(webpage: String) {
        val matcher =
            Pattern.compile("<script id=\"SIGI_STATE\" type=\"application/json\">(\\{.*?\\})</script>").matcher(webpage)
        if (matcher.find()) {
            val json = matcher.group(1).toJSONObject()
            val itemList = json.getJSONObject("ItemList").getJSONObject("video").getJSONArray("list")
            val itemModule = json.getJSONObject("ItemModule")
            for (i in 0 until itemList.length()) {
                val videoId = itemModule.getJSONObject(itemList.getString(i))
                val formats = localFormats.copy(
                    title = "",
                    audioData = mutableListOf(),
                    videoData = mutableListOf(),
                    imageData = mutableListOf()
                )

                formats.title = videoId.getString("desc")
                val video = videoId.getJSONObject("video")
                formats.imageData.add(
                    ImageResource(
                        video.getString("cover")
                    )
                )
                formats.videoData.add(
                    VideoResource(
                        video.getString("playAddr"),
                        MimeType.fromCodecs(video.getString("format")),
                        quality = video.getString("definition")
                    )
                )
                videoFormats.add(formats)
            }
            finalize()
        } else
            onProgress(Result.Failed(Error.MethodMissingLogic))
    }

    suspend fun testWithWebPage(string: String){
        onProgress = {
            println(it)
        }
        extractFromWebPage(string)
    }
}
