package com.example.data.network

enum class ResolutionTier(val shortLabel: String, val displayLabel: String, val heightPx: Int) {
    R1080P("1080p", "1080p FHD", 1080),
    R720P("720p", "720p HD", 720),
    R640P("640p", "640p SD", 640),
    R480P("480p", "480p SD", 480),
    R360P("360p", "360p SD", 360),
    R144P("144p", "144p Low", 144)
}

data class VideoStreamCandidate(
    val title: String,
    val streamUrl: String,
    val pageUrl: String,
    val domain: String,
    val mimeType: String,
    val formatLabel: String,
    val qualityLabel: String,
    val resolutionTier: ResolutionTier? = null,
    val contentLengthBytes: Long,
    val posterUrl: String? = null
)

sealed interface AnalyzeResult {
    data object Idle : AnalyzeResult
    data class Loading(val url: String) : AnalyzeResult
    data class Found(
        val pageTitle: String,
        val sourceUrl: String,
        val candidates: List<VideoStreamCandidate>
    ) : AnalyzeResult
    data class NoResults(
        val query: String,
        val title: String,
        val reason: String,
        val suggestion: String
    ) : AnalyzeResult
    data class Error(
        val query: String,
        val message: String
    ) : AnalyzeResult
}

data class PublicSampleStream(
    val title: String,
    val subtitle: String,
    val url: String,
    val formatLabel: String,
    val qualityLabel: String
)

object PublicSampleCatalog {
    val samples = listOf(
        PublicSampleStream(
            title = "For Bigger Blazes",
            subtitle = "Chromecast Open Sample • MP4",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            formatLabel = "MP4",
            qualityLabel = "720p HD"
        ),
        PublicSampleStream(
            title = "For Bigger Escapes",
            subtitle = "Chromecast Open Sample • MP4",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            formatLabel = "MP4",
            qualityLabel = "720p HD"
        ),
        PublicSampleStream(
            title = "Big Buck Bunny",
            subtitle = "Blender Foundation Open Film • MP4",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            formatLabel = "MP4",
            qualityLabel = "720p HD"
        )
    )
}
