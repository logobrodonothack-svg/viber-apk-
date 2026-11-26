package com.example

import com.example.data.network.AnalyzeResult
import com.example.data.network.ResolutionTier
import com.example.data.network.VideoLinkAnalyzer
import com.example.util.Formatters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipVaultUnitTest {

    @Test
    fun `empty or keyword input returns NoResults with helpful title`() = runTest {
        val analyzer = VideoLinkAnalyzer()
        val emptyResult = analyzer.analyze("   ")
        assertTrue(emptyResult is AnalyzeResult.NoResults)
        assertEquals("No results found", (emptyResult as AnalyzeResult.NoResults).title)

        val keywordResult = analyzer.analyze("funny cat videos")
        assertTrue(keywordResult is AnalyzeResult.NoResults)
        assertEquals("No results found", (keywordResult as AnalyzeResult.NoResults).title)
    }

    @Test
    fun `youtube and instagram urls return policy compliant NoResults explanations`() = runTest {
        val analyzer = VideoLinkAnalyzer()
        val ytResult = analyzer.analyze("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(ytResult is AnalyzeResult.NoResults)
        assertEquals("No results found", (ytResult as AnalyzeResult.NoResults).title)
        assertTrue(ytResult.reason.contains("YouTube", ignoreCase = true))

        val igResult = analyzer.analyze("https://www.instagram.com/reel/C123456789/")
        assertTrue(igResult is AnalyzeResult.NoResults)
        assertEquals("No results found", (igResult as AnalyzeResult.NoResults).title)
        assertTrue(igResult.reason.contains("Instagram", ignoreCase = true))
    }

    @Test
    fun `resolution tiers include 1080p 720p 640p 480p 360p and 144p`() {
        val labels = ResolutionTier.entries.map { it.shortLabel }
        assertEquals(listOf("1080p", "720p", "640p", "480p", "360p", "144p"), labels)
    }

    @Test
    fun `formatters format bytes durations and filenames`() {
        assertEquals("1.0 MB", Formatters.formatBytes(1024L * 1024L))
        assertEquals("01:05", Formatters.formatDuration(65_000L))
        assertEquals("my_video.mp4", Formatters.sanitizeFileName("my:video", "mp4"))
    }
}
