package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private val current = AppVersion(1, 0, 0)

    @Test
    fun `parses the single-object shape from the latest endpoint`() {
        val releases = ReleaseParser.parseReleases(LATEST_OBJECT)
        assertEquals(1, releases.size)
        assertEquals("v1.2.0", releases.first().tagName)
    }

    @Test
    fun `parses the array shape from the releases endpoint`() {
        val releases = ReleaseParser.parseReleases(TWO_RELEASES)
        assertEquals(2, releases.size)
        assertEquals("v1.2.0", releases[0].tagName)
        assertEquals("v1.1.0", releases[1].tagName)
    }

    @Test
    fun `malformed json yields no releases rather than throwing`() {
        assertTrue(ReleaseParser.parseReleases("{ not json").isEmpty())
        assertTrue(ReleaseParser.parseReleases("").isEmpty())
    }

    @Test
    fun `finds a newer release with an apk asset`() {
        val update = ReleaseParser.findUpdate(
            ReleaseParser.parseReleases(TWO_RELEASES), current
        )
        assertEquals(AppVersion(1, 2, 0), update?.version)
        assertEquals("https://example.com/pulsepad-1.2.0.apk", update?.apkUrl)
        assertEquals(1234L, update?.apkSizeBytes)
    }

    @Test
    fun `ignores a release that is not newer`() {
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(LATEST_OBJECT),
            AppVersion(1, 2, 0)))
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(LATEST_OBJECT),
            AppVersion(2, 0, 0)))
    }

    @Test
    fun `skips drafts`() {
        val draft = """
            [{"tag_name":"v9.0.0","draft":true,"assets":[
              {"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}]
        """.trimIndent()
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(draft), current))
    }

    @Test
    fun `skips prereleases unless they are allowed`() {
        val prerelease = """
            [{"tag_name":"v2.0.0-rc.1","prerelease":true,"assets":[
              {"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}]
        """.trimIndent()
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(prerelease), current))
        assertEquals(
            AppVersion(2, 0, 0, "rc.1"),
            ReleaseParser.findUpdate(
                ReleaseParser.parseReleases(prerelease), current, allowPrerelease = true
            )?.version
        )
    }

    @Test
    fun `ignores a release with no apk asset`() {
        val noAsset = """
            [{"tag_name":"v9.0.0","assets":[
              {"name":"source.zip","browser_download_url":"https://example.com/s.zip"}]}]
        """.trimIndent()
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(noAsset), current))
    }

    @Test
    fun `ignores an asset served over plain http`() {
        val insecure = """
            [{"tag_name":"v9.0.0","assets":[
              {"name":"a.apk","browser_download_url":"http://example.com/a.apk"}]}]
        """.trimIndent()
        assertNull(ReleaseParser.findUpdate(ReleaseParser.parseReleases(insecure), current))
    }

    @Test
    fun `picks the highest version when several are newer`() {
        val many = """
            [{"tag_name":"v1.1.0","assets":[{"name":"a.apk",
              "browser_download_url":"https://example.com/1.apk"}]},
             {"tag_name":"v1.10.0","assets":[{"name":"a.apk",
              "browser_download_url":"https://example.com/10.apk"}]},
             {"tag_name":"v1.2.0","assets":[{"name":"a.apk",
              "browser_download_url":"https://example.com/2.apk"}]}]
        """.trimIndent()
        val update = ReleaseParser.findUpdate(ReleaseParser.parseReleases(many), current)
        assertEquals(AppVersion(1, 10, 0), update?.version)
    }

    @Test
    fun `accepts an apk asset name case-insensitively`() {
        assertTrue(ReleaseParser.isInstallableApk(
            GitHubAsset("PulsePad.APK", "https://example.com/a.apk")))
        assertFalse(ReleaseParser.isInstallableApk(
            GitHubAsset("notes.txt", "https://example.com/n.txt")))
    }

    @Test
    fun `falls back to the tag when a release has no name`() {
        val unnamed = """
            [{"tag_name":"v1.3.0","name":"","assets":[
              {"name":"a.apk","browser_download_url":"https://example.com/a.apk"}]}]
        """.trimIndent()
        assertEquals("v1.3.0",
            ReleaseParser.findUpdate(ReleaseParser.parseReleases(unnamed), current)?.releaseName)
    }

    private companion object {
        val LATEST_OBJECT = """
            {
              "tag_name": "v1.2.0",
              "name": "PulsePad 1.2.0",
              "body": "Fixes and improvements.",
              "html_url": "https://github.com/Lynxifyy/PulsePad/releases/tag/v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "pulsepad-1.2.0.apk",
                  "browser_download_url": "https://example.com/pulsepad-1.2.0.apk",
                  "size": 1234
                }
              ]
            }
        """.trimIndent()

        val TWO_RELEASES = """
            [
              {
                "tag_name": "v1.2.0",
                "name": "PulsePad 1.2.0",
                "body": "Newest.",
                "html_url": "https://github.com/Lynxifyy/PulsePad/releases/tag/v1.2.0",
                "assets": [
                  {
                    "name": "pulsepad-1.2.0.apk",
                    "browser_download_url": "https://example.com/pulsepad-1.2.0.apk",
                    "size": 1234
                  }
                ]
              },
              {
                "tag_name": "v1.1.0",
                "name": "PulsePad 1.1.0",
                "body": "Older.",
                "html_url": "https://github.com/Lynxifyy/PulsePad/releases/tag/v1.1.0",
                "assets": [
                  {
                    "name": "pulsepad-1.1.0.apk",
                    "browser_download_url": "https://example.com/pulsepad-1.1.0.apk",
                    "size": 1200
                  }
                ]
              }
            ]
        """.trimIndent()
    }
}
