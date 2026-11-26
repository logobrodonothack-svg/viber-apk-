package com.example.data.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class VideoLinkAnalyzer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".mov", ".m4v", ".mkv")
        private val RESTRICTED_YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
        )
        private val RESTRICTED_INSTAGRAM_HOSTS = setOf(
            "instagram.com",
            "www.instagram.com",
            "m.instagram.com",
            "instagr.am"
        )
    }

    private data class ExtractedSource(
        val url: String,
        val explicitQualityHint: String? = null
    )

    suspend fun analyze(rawInput: String): AnalyzeResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext AnalyzeResult.NoResults(
                query = "",
                title = "No results found",
                reason = "Enter a video URL or import links to scan for downloadable media streams.",
                suggestion = "Paste a direct .mp4/.webm link, import a link list, or try one of the open sample streams below."
            )
        }

        val normalizedUrl = normalizeToUrlOrNull(trimmed)
        if (normalizedUrl == null) {
            return@withContext AnalyzeResult.NoResults(
                query = trimmed,
                title = "No results found",
                reason = "\"$trimmed\" is not a valid web or video link.",
                suggestion = "ClipVault scans direct video URLs (.mp4, .webm, .mov) and HTML5 video pages. Paste a full https:// link or use Import."
            )
        }

        val host = runCatching { URI(normalizedUrl).host?.lowercase() ?: "unknown" }.getOrDefault("unknown")
        if (host in RESTRICTED_YOUTUBE_HOSTS || host.endsWith(".youtube.com")) {
            return@withContext AnalyzeResult.NoResults(
                query = normalizedUrl,
                title = "No results found",
                reason = "YouTube links cannot be downloaded due to YouTube Terms of Service and Google Play policies.",
                suggestion = "ClipVault supports direct MP4/WebM links, open HTML5 video pages (1080p–144p), and imported link lists."
            )
        }
        if (host in RESTRICTED_INSTAGRAM_HOSTS || host.endsWith(".instagram.com")) {
            return@withContext AnalyzeResult.NoResults(
                query = normalizedUrl,
                title = "No results found",
                reason = "Instagram links cannot be scraped or downloaded due to Instagram Terms of Service and authentication restrictions.",
                suggestion = "ClipVault supports direct MP4/WebM links, open HTML5 video pages (1080p, 720p, 640p, 480p, 360p, 144p), and imported link lists."
            )
        }

        try {
            val headRequest = Request.Builder()
                .url(normalizedUrl)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()

            var contentType: String? = null
            var contentLength = -1L
            var finalUrl: String = normalizedUrl

            runCatching {
                client.newCall(headRequest).execute().use { resp ->
                    finalUrl = resp.request.url.toString()
                    if (resp.isSuccessful) {
                        contentType = resp.header("Content-Type")?.lowercase()
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            }

            val headContentType = contentType
            if (isDirectVideo(finalUrl, headContentType)) {
                val candidate = buildDirectCandidate(
                    streamUrl = finalUrl,
                    pageUrl = normalizedUrl,
                    mimeType = headContentType ?: inferMimeFromUrl(finalUrl),
                    contentLength = contentLength,
                    titleOverride = null,
                    posterUrl = null,
                    qualityHint = null
                )
                return@withContext AnalyzeResult.Found(
                    pageTitle = candidate.title,
                    sourceUrl = normalizedUrl,
                    candidates = listOf(candidate)
                )
            }

            val getRequest = Request.Builder()
                .url(normalizedUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(getRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext AnalyzeResult.NoResults(
                        query = normalizedUrl,
                        title = "No results found",
                        reason = "Server at $host returned HTTP ${resp.code}.",
                        suggestion = "Verify that the link is public, not expired, and accessible without login."
                    )
                }

                val resolvedFinalUrl = resp.request.url.toString()
                val respContentType = resp.header("Content-Type")?.lowercase().orEmpty()
                val respLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L

                if (isDirectVideo(resolvedFinalUrl, respContentType)) {
                    val candidate = buildDirectCandidate(
                        streamUrl = resolvedFinalUrl,
                        pageUrl = normalizedUrl,
                        mimeType = respContentType.ifBlank { inferMimeFromUrl(resolvedFinalUrl) },
                        contentLength = respLength,
                        titleOverride = null,
                        posterUrl = null,
                        qualityHint = null
                    )
                    return@withContext AnalyzeResult.Found(
                        pageTitle = candidate.title,
                        sourceUrl = normalizedUrl,
                        candidates = listOf(candidate)
                    )
                }

                if (!respContentType.contains("html") && !respContentType.contains("text")) {
                    return@withContext AnalyzeResult.NoResults(
                        query = normalizedUrl,
                        title = "No results found",
                        reason = "The link returned \"$respContentType\" instead of a playable video stream.",
                        suggestion = "Try copying the direct .mp4 link or a webpage containing an embedded HTML5 video."
                    )
                }

                val bodyString = resp.body?.source()?.let { source ->
                    source.request(1_048_576L)
                    source.buffer.clone().readUtf8()
                }.orEmpty()

                val pageTitle = extractHtmlTitle(bodyString) ?: host
                val posterUrl = extractMetaContent(bodyString, "og:image")
                    ?.let { resolveRelativeUrl(resolvedFinalUrl, it) }

                val extracted = extractVideoSourcesFromHtml(bodyString, resolvedFinalUrl)
                if (extracted.isEmpty()) {
                    return@withContext AnalyzeResult.NoResults(
                        query = normalizedUrl,
                        title = "No results found",
                        reason = "No direct MP4, WebM, or HTML5 <video> streams were detected on $host.",
                        suggestion = "Sites using DRM encryption or dynamic JavaScript players cannot be extracted directly. Try a direct .mp4 URL or open sample stream."
                    )
                }

                val candidates = extracted.take(10).mapIndexed { index, source ->
                    val probeLength = if (index < 5) probeContentLength(source.url) else -1L
                    buildDirectCandidate(
                        streamUrl = source.url,
                        pageUrl = normalizedUrl,
                        mimeType = inferMimeFromUrl(source.url),
                        contentLength = probeLength,
                        titleOverride = pageTitle,
                        posterUrl = posterUrl,
                        qualityHint = source.explicitQualityHint
                    )
                }.sortedByDescending { it.resolutionTier?.heightPx ?: 0 }

                return@withContext AnalyzeResult.Found(
                    pageTitle = pageTitle,
                    sourceUrl = normalizedUrl,
                    candidates = candidates
                )
            }
        } catch (e: Exception) {
            return@withContext AnalyzeResult.Error(
                query = normalizedUrl,
                message = e.localizedMessage ?: "Unable to connect to $host. Check your internet connection."
            )
        }
    }

    private fun normalizeToUrlOrNull(input: String): String? {
        val cleaned = input.trim()
        if (cleaned.contains(' ')) return null
        val candidate = if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
            cleaned
        } else if (cleaned.contains('.') && !cleaned.startsWith(".")) {
            "https://$cleaned"
        } else {
            return null
        }
        return runCatching {
            val uri = URI(candidate)
            if (uri.host.isNullOrBlank()) null else candidate
        }.getOrNull()
    }

    private fun isDirectVideo(url: String, contentType: String?): Boolean {
        if (contentType != null && contentType.startsWith("video/")) return true
        val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault(url.lowercase())
        return VIDEO_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun inferMimeFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".webm") -> "video/webm"
            lower.contains(".mov") -> "video/quicktime"
            lower.contains(".mkv") -> "video/x-matroska"
            lower.contains(".m4v") -> "video/x-m4v"
            else -> "video/mp4"
        }
    }

    private fun inferFormatLabel(mimeType: String, url: String): String {
        val lowerUrl = url.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.contains("webm") || lowerUrl.contains(".webm") -> "WEBM"
            lowerMime.contains("quicktime") || lowerUrl.contains(".mov") -> "MOV"
            lowerMime.contains("matroska") || lowerUrl.contains(".mkv") -> "MKV"
            else -> "MP4"
        }
    }

    private fun inferResolutionTier(url: String, qualityHint: String?): ResolutionTier? {
        val combined = "${qualityHint.orEmpty()} $url".lowercase()
        return when {
            "1080" in combined || "fhd" in combined -> ResolutionTier.R1080P
            "720" in combined || "_hd" in combined || "-hd" in combined || "720p" in combined -> ResolutionTier.R720P
            "640" in combined -> ResolutionTier.R640P
            "480" in combined -> ResolutionTier.R480P
            "360" in combined -> ResolutionTier.R360P
            "144" in combined -> ResolutionTier.R144P
            else -> null
        }
    }

    private fun inferQualityLabel(tier: ResolutionTier?, url: String, qualityHint: String?): String {
        if (tier != null) return tier.displayLabel
        val combined = "${qualityHint.orEmpty()} $url".lowercase()
        return when {
            "2160" in combined || "4k" in combined -> "4K UHD"
            "480" in combined -> "480p SD"
            else -> "Original"
        }
    }

    private fun buildDirectCandidate(
        streamUrl: String,
        pageUrl: String,
        mimeType: String,
        contentLength: Long,
        titleOverride: String?,
        posterUrl: String?,
        qualityHint: String?
    ): VideoStreamCandidate {
        val host = runCatching { URI(streamUrl).host ?: "web" }.getOrDefault("web")
        val rawName = runCatching {
            Uri.parse(streamUrl).lastPathSegment
                ?.substringBefore('?')
                ?.substringBefore('#')
                ?.substringBeforeLast('.')
        }.getOrNull().orEmpty()

        val cleanTitle = titleOverride?.takeIf { it.isNotBlank() }
            ?: rawName.replace('_', ' ').replace('-', ' ').trim().ifBlank { "Video from $host" }

        val tier = inferResolutionTier(streamUrl, qualityHint)
        val qualityLabel = inferQualityLabel(tier, streamUrl, qualityHint)

        return VideoStreamCandidate(
            title = cleanTitle,
            streamUrl = streamUrl,
            pageUrl = pageUrl,
            domain = host.removePrefix("www."),
            mimeType = mimeType.substringBefore(';').ifBlank { "video/mp4" },
            formatLabel = inferFormatLabel(mimeType, streamUrl),
            qualityLabel = qualityLabel,
            resolutionTier = tier,
            contentLengthBytes = contentLength,
            posterUrl = posterUrl
        )
    }

    private fun probeContentLength(url: String): Long {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.header("Content-Length")?.toLongOrNull() ?: -1L else -1L
            }
        }.getOrDefault(-1L)
    }

    private fun extractHtmlTitle(html: String): String? {
        val ogTitle = extractMetaContent(html, "og:title")
        if (!ogTitle.isNullOrBlank()) return decodeBasicEntities(ogTitle)
        val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
        return match?.groupValues?.getOrNull(1)?.trim()?.let(::decodeBasicEntities)
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val pattern1 = Regex(
            """(?is)<meta[^>]+(?:property|name)\s*=\s*["']${Regex.escape(key)}["'][^>]+content\s*=\s*["']([^"']+)["']"""
        )
        val pattern2 = Regex(
            """(?is)<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']${Regex.escape(key)}["']"""
        )
        return pattern1.find(html)?.groupValues?.getOrNull(1)
            ?: pattern2.find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractVideoSourcesFromHtml(html: String, baseUrl: String): List<ExtractedSource> {
        val results = LinkedHashMap<String, ExtractedSource>()

        listOf("og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream").forEach { meta ->
            extractMetaContent(html, meta)?.let { raw ->
                resolveRelativeUrl(baseUrl, decodeBasicEntities(raw))?.let { resolved ->
                    if (isLikelyPlayableStream(resolved)) {
                        results.putIfAbsent(resolved, ExtractedSource(resolved))
                    }
                }
            }
        }

        val fullTagRegex = Regex("""(?is)<(?:video|source)\b([^>]*)>""")
        val srcAttrRegex = Regex("""(?is)\bsrc\s*=\s*["']([^"']+)["']""")
        val qualityAttrRegex = Regex("""(?is)\b(?:size|res|data-res|label|title)\s*=\s*["']([^"']+)["']""")

        fullTagRegex.findAll(html).forEach { tagMatch ->
            val attrs = tagMatch.groupValues[1]
            val rawSrc = srcAttrRegex.find(attrs)?.groupValues?.getOrNull(1)?.let(::decodeBasicEntities)
                ?: return@forEach
            val hint = qualityAttrRegex.find(attrs)?.groupValues?.getOrNull(1)
            resolveRelativeUrl(baseUrl, rawSrc)?.let { resolved ->
                if (!resolved.startsWith("blob:", true)) {
                    results[resolved] = ExtractedSource(resolved, hint)
                }
            }
        }

        val directFileRegex = Regex("""(?i)https?://[^\s"'<>\\]+\.(?:mp4|webm|mov|m4v)(?:\?[^\s"'<>\\]*)?""")
        directFileRegex.findAll(html).forEach { match ->
            val url = decodeBasicEntities(match.value)
            results.putIfAbsent(url, ExtractedSource(url))
        }

        return results.values.toList()
    }

    private fun isLikelyPlayableStream(url: String): Boolean {
        val lower = url.lowercase()
        return VIDEO_EXTENSIONS.any { lower.contains(it) } || lower.contains("video")
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String? {
        val trimmed = relative.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:") || trimmed.startsWith("blob:")) return null
        return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrNull()
    }

    private fun decodeBasicEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
