

package com.mugames.vidsnapkit.extractor

import com.mugames.vidsnapkit.*
import com.mugames.vidsnapkit.dataholders.*
import com.mugames.vidsnapkit.network.HttpRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern


class Instagram internal constructor(url: String) : Extractor(url) {
    companion object {
        const val TAG: String = Statics.TAG.plus(":Instagram")
        const val STORIES_URL = "https://www.instagram.com/stories/%s/?__a=1&__d=dis"
        const val STORIES_API = "https://i.instagram.com/api/v1/feed/user/%s/story/"
        const val PROFILE_API = "https://www.instagram.com/%s/?__a=1&__d=dis"
        const val POST_API = "https://i.instagram.com/api/v1/media/%s/info/"
        const val NO_VIDEO_STATUS_AVAILABLE = "No video Status available"
        const val NO_STATUS_AVAILABLE = "No stories Available to Download"
        const val HIGHLIGHTS_API = "https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=highlight%s"
    }

    private val formats = Formats()

    private fun getMediaId(page: String): String? {
        val matcher = Pattern.compile("\"media_id\":\"(.*?)\"").matcher(page)
        return if (matcher.find()) matcher.group(1) else null
    }


    private fun isProfileUrl(): Boolean {
        if (inputUrl.contains("/p/")) return false
        return !inputUrl.contains("(/reel/|/tv/|/reels/)[\\w-]{11}".toRegex())
    }

    private fun isHighlightsPost(): Boolean {
        return inputUrl.contains("stories/highlights/".toRegex())
    }

    private fun getHighlightsId(): String? {
        val matcher =
            Pattern.compile("(?:https|http)://(?:www\\.|.*?)instagram.com/(?:stories/highlights/|)([A-Za-z0-9_.]+)")
                .matcher(inputUrl)
        return if (matcher.find()) matcher.group(1)
        else null
    }

    private fun isAccessible(jsonObject: JSONObject): Boolean {
        val user = jsonObject.getJSONObject("graphql")
            .getJSONObject("user")
        val isBlocked = user.getBoolean("has_blocked_viewer")
        val isPrivate = user.getBoolean("is_private")
        val followedByViewer = user.getBoolean("followed_by_viewer")
        return ((isPrivate && followedByViewer) || !isPrivate) && !isBlocked
    }

    private fun getUserName(): String? {
        val matcher = Pattern
            .compile("(?:https|http)://(?:www\\.|.*?)instagram.com/(?:stories/|)([A-Za-z0-9_.]+)")
            .matcher(inputUrl)
        return if (matcher.find()) matcher.group(1)
        else null
    }

    private suspend fun getUserID(): String? = cookies?.let { _ ->

        getUserName()?.let {
            val response = HttpRequest(String.format(PROFILE_API, it), headers).getResponse()?.toJSONObject() ?: run {
                clientRequestError()
                return null
            }
            if (response.toString() == "{}") {
                clientRequestError()
                return null
            }
            if (!isAccessible(response)) {
                onProgress(Result.Failed(Error.InvalidCookies))
                return null
            }
            return response.getJSONObject("graphql")?.getJSONObject("user")?.getString("id")
        } ?: run {
            onProgress(Result.Failed(Error.InvalidUrl))
        }
        return null
    } ?: run {
        onProgress(Result.Failed(Error.LoginRequired))
        null
    }

    override suspend fun analyze() {
        formats.src = "Instagram"
        inputUrl = inputUrl.replace("/reels/", "/reel/")
        formats.url = inputUrl
        if (!isProfileUrl())
            extractInfoShared(HttpRequest(inputUrl, getHeadersWithUserAgent()).getResponse() ?: run {
                clientRequestError()
                return
            })
        else if (isHighlightsPost()) {
            val highlightsId = getHighlightsId()
            highlightsId?.let {
                extractHighlights(it)
            }
        } else {
            val userId = getUserID()
            userId?.let {
                extractStories(it)
            }
        }
    }

    private suspend fun extractHighlights(highlightsId: String) {
        val highlights = HttpRequest(HIGHLIGHTS_API.format("%3A$highlightsId"), getHeadersWithUserAgent()).getResponse()
            ?.toJSONObjectOrNull()
        highlights?.let {
            if (it.getNullable("login_required") == "true") {
                onProgress(Result.Failed(Error.LoginRequired))
                return
            }
            val highlight = it.getJSONObject("reels").getJSONObject("highlight:$highlightsId")
            formats.title = highlight.getNullableString("title") ?: "highlight:$highlightsId"
            extractFromItems(highlight.getJSONArray("items"))
        } ?: clientRequestError()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun extractStories(userId: String) {
        val dupHeader = getHeadersWithUserAgent()
        val stories = HttpRequest(STORIES_API.format(userId), dupHeader).getResponse()
        val reel = JSONObject(stories).getNullableJSONObject("reel")
        reel?.let { extractFromItems(it.getJSONArray("items")) } ?: onProgress(
            Result.Failed(
                Error.NonFatalError(
                    NO_STATUS_AVAILABLE
                )
            )
        )
    }

    private fun getHeadersWithUserAgent(): Hashtable<String, String> {
        val dupHeader: Hashtable<String, String> = headers.clone() as Hashtable<String, String>
        dupHeader["User-Agent"] =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 12_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 105.0.0.11.118 (iPhone11,8; iOS 12_3_1; en_US; en-US; scale=2.00; 828x1792; 165586599)"
        return dupHeader
    }


    private suspend fun extractInfoShared(page: String) {
        suspend fun newApiRequest() {
            val mediaId = getMediaId(page)
            try {
                if (mediaId == null) throw JSONException("mediaId is null purposely thrown wrong error")
                val url = POST_API.format(mediaId)
                extractFromItems(
                    JSONObject(
                        HttpRequest(
                            url,
                            getHeadersWithUserAgent()
                        ).getResponse().toString()
                    ).getJSONArray("items")
                )
            } catch (e: JSONException) {
                onProgress(Result.Failed(Error.LoginRequired))
            }
        }

        onProgress(Result.Progress(ProgressState.Start))
        val pattern = Pattern.compile("window\\._sharedData\\s*=\\s*(\\{.+?\\});")
        val matcher = pattern.matcher(page)
        val jsonString = if (matcher.find()) {
            matcher.group(1)
        } else {
            val json = getFromBrutForcing(page)
            if (json != null) {
                brutForcedExtraction(json.toJSONObject())
                return
            } else newApiRequest()
            return
        }
        val jsonObject = JSONObject(jsonString)
        val postPage: JSONArray? = jsonObject.getNullableJSONObject("entry_data")
            ?.getNullableJSONArray("PostPage")

        postPage?.let { post ->
            val zero: JSONObject = post.getJSONObject(0)
            val graphql: JSONObject? = zero.getNullableJSONObject("graphql")
            val media = graphql?.getNullableJSONObject("shortcode_media")
                ?: zero.getNullableJSONObject("media")
            media?.let {
                setInfo(it)
            } ?: run {
                extractInfoAdd(page)
            }
        } ?: run {
            fun isObjectPresentInEntryData(objectName: String): Boolean {
                return jsonObject.getNullableJSONObject("entry_data")
                    ?.getNullableJSONArray(objectName) != null
            }
            if (isObjectPresentInEntryData("LoginAndSignupPage")) {
                onProgress(Result.Failed(Error.LoginRequired))
            } else if (isObjectPresentInEntryData("HttpErrorPage")) {
                onProgress(Result.Failed(Error.Instagram404Error(cookies != null)))
            } else {
                val user0 = jsonObject
                    .getJSONObject("entry_data")
                    .getNullableJSONArray("ProfilePage")
                    ?.getJSONObject(0) ?: run {
                    newApiRequest()
                    return
                }
                if (!isAccessible(user0))
                    onProgress(Result.Failed(Error.InvalidCookies))
                else onProgress(Result.Failed(Error.InternalError("can't find problem")))
            }
        }
    }

    private suspend fun brutForcedExtraction(jsonObject: JSONObject) {
        formats.title = jsonObject.getString("articleBody")
        var isMultiple = true
        val images = jsonObject.getJSONArray("image")
        for (i in 0 until images.length()) {
            val image = images.getJSONObject(0)
            if (images.length() == 1) {
                isMultiple = false
                formats.imageData.add(
                    ImageResource(
                        url = image.getString("url"),
                        resolution = image.getString("width") + "x" + image.getString("height")
                    )
                )
                continue
            }
            val localFormat = formats.copy(title = "", imageData = mutableListOf(), videoData = mutableListOf())
            localFormat.title = image.getString("caption")
            localFormat.imageData.add(
                ImageResource(
                    url = image.getString("url"),
                    resolution = image.getString("width") + "x" + image.getString("height")
                )
            )
            videoFormats.add(localFormat)
        }

        val videos = jsonObject.getJSONArray("video")
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(0)
            if (video.length() == 1) {
                isMultiple = false
                formats.videoData.add(
                    VideoResource(
                        url = video.getString("contentUrl"),
                        mimeType = MimeType.VIDEO_MP4,
                        quality = video.getString("width") + "x" + video.getString("height")
                    )
                )
                formats.imageData.add(ImageResource(url = video.getString("thumbnailUrl")))
                continue
            }
            val localFormat = formats.copy(title = "", imageData = mutableListOf(), videoData = mutableListOf())
            localFormat.title = video.getString("caption")
            localFormat.imageData.add(ImageResource(url = video.getString("thumbnailUrl")))
            localFormat.videoData.add(
                VideoResource(
                    url = video.getString("contentUrl"),
                    mimeType = MimeType.VIDEO_MP4,
                    quality = video.getString("width") + "x" + video.getString("height")
                )
            )
            videoFormats.add(localFormat)
        }

        if (!isMultiple) videoFormats.add(formats)
        finalize()
    }

    private fun getFromBrutForcing(page: String): String? {
        val matcher = Pattern.compile("<script type=\"application\\/ld\\+json\".*?>(.*?)<\\/script>").matcher(page)
        if (matcher.find()) {
            val res = matcher.group(1)
            if (res.contains("articleBody")) {
                return res
            }
        }
        return null
    }

    private suspend fun setInfo(media: JSONObject) {
        onProgress(Result.Progress(ProgressState.Middle))
        var videoName: String? = media.getNullableString("title")

        if (videoName == null || videoName == "null" || videoName.isEmpty()) videoName =
            media
                .getNullableJSONObject("edge_media_to_caption")
                ?.getNullableJSONArray("edges")
                ?.getNullableJSONObject(0)
                ?.getJSONObject("node")
                ?.getString("text")
        if (videoName == null || videoName == "null" || videoName.isEmpty()) videoName =
            "instagram_video"
        formats.title = Util.filterName(videoName)
        val fileURL: String? = media.getNullableString("video_url")
        fileURL?.let { url ->
            formats.videoData.add(
                VideoResource(
                    url,
                    MimeType.VIDEO_MP4
                )
            )
            formats.imageData.add(
                ImageResource(
                    resolution = Util.getResolutionFromUrl(
                        media.getString(
                            "thumbnail_src"
                        )
                    ),
                    url = media.getString("thumbnail_src")
                )
            )
            videoFormats.add(formats)
        } ?: run {
            val edges: JSONArray? = media
                .getNullableJSONObject("edge_sidecar_to_children")
                ?.getNullableJSONArray("edges")

            edges?.let { edgesObj ->
                for (i in 0 until edgesObj.length()) {
                    val format = formats.copy(videoData = mutableListOf())
                    val node = edgesObj.getJSONObject(i).getJSONObject("node")
                    if (node.getBoolean("is_video")) {
                        format.imageData.add(
                            ImageResource(
                                resolution = Util.getResolutionFromUrl(node.getString("display_url")),
                                url = node.getString("display_url")
                            )
                        )
                        format.videoData.add(
                            VideoResource(
                                node.getString("video_url"),
                                MimeType.VIDEO_MP4
                            )
                        )
                    }
                    videoFormats.add(format)
                }
            } ?: run {
                onProgress(
                    Result.Failed(
                        Error.InternalError(
                            "Media not found",
                            Exception("$media")
                        )
                    )
                )
            }
        }

        finalize()
    }

    private suspend fun extractInfoAdd(page: String) {
        val pattern =
            Pattern.compile("window\\.__additionalDataLoaded\\s*\\(\\s*[^,]+,\\s*(\\{.+?\\})\\s*\\)\\s*;")
        val matcher = pattern.matcher(page)
        val jsonString = if (matcher.find()) {
            matcher.group(1)
        } else null

        if (jsonString.isNullOrEmpty()) {
            onProgress(Result.Failed(Error.LoginRequired))
            return
        }
        val jsonObject = JSONObject(jsonString)
        val graphql: JSONObject? = jsonObject.getNullableJSONObject("graphql")
        graphql?.let {
            val media = it.getNullableJSONObject("shortcode_media")
            media?.let { mediaIt ->
                setInfo(mediaIt)
            } ?: run {
                onProgress(Result.Failed(Error.InternalError("MediaNotFound")))
            }

        } ?: run {
            extractFromItems(jsonObject.getJSONArray("items"))
        }
    }

    private suspend fun extractFromItems(items: JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)

            if (formats.title.isEmpty()) {
                onProgress(Result.Progress(ProgressState.Middle))
                val caption = item.getNullableJSONObject("caption")
                formats.title = Util.filterName(
                    caption?.getNullableString("text") ?: run {
                        item.getNullableString("caption")
                            ?: "Instagram_Reels"
                    }
                )
            }


            val videoVersion = item.getNullableJSONArray("video_versions")
            videoVersion?.let {
                val format = formats.copy(videoData = mutableListOf(), imageData = mutableListOf())
                for (j in 0 until it.length()) {
                    val video = it.getJSONObject(j)
                    format.videoData.add(
                        VideoResource(
                            video.getString("url"),
                            MimeType.VIDEO_MP4,
                            try {
                                video.getString("width")
                            } catch (e: JSONException) {
                                video.getInt("width").toString()
                            } + "x" + try {
                                video.getString("height")
                            } catch (e: JSONException) {
                                video.getInt("height").toString()
                            }
                        )
                    )
                }
                val imageVersion2 = item.getJSONObject("image_versions2")
                val candidates = imageVersion2.getJSONArray("candidates")
                val thumbnailUrl = candidates.getJSONObject(0).getString("url")
                format.imageData.add(
                    ImageResource(
                        resolution = Util.getResolutionFromUrl(thumbnailUrl),
                        url = thumbnailUrl
                    )
                )
                videoFormats.add(format)
            } ?: run {
                item.getNullableJSONArray("carousel_media")?.let {
                    extractFromItems(it)
                    return
                } ?: run {
                    val imageVersion2 = item.getNullableJSONObject("image_versions2")
                    imageVersion2?.let {
                        val format = formats.copy(imageData = mutableListOf())

                        val candidates = it.getJSONArray("candidates")
                        val thumbnailUrl = candidates.getJSONObject(0).getString("url")
                        format.imageData.add(
                            ImageResource(
                                resolution = Util.getResolutionFromUrl(thumbnailUrl),
                                url = thumbnailUrl
                            )
                        )
                        videoFormats.add(format)
                    }
                }
            }
        }
        if (isProfileUrl() && videoFormats.isEmpty()) {
            onProgress(Result.Failed(Error.NonFatalError(NO_VIDEO_STATUS_AVAILABLE)))
        } else
            finalize()
    }
}