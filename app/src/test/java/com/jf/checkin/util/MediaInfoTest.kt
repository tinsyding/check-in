package com.jf.checkin.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaInfoTest {

    @Test
    fun extensionFor_prefersMimeType() {
        assertEquals("jpg", extensionFor("image/jpeg", "photo.heic"))
        assertEquals("jpg", extensionFor("image/jpg", null))
        assertEquals("png", extensionFor("IMAGE/PNG", null))
        assertEquals("webp", extensionFor("image/webp", null))
        assertEquals("heic", extensionFor("image/heic", null))
        assertEquals("gif", extensionFor("image/gif", null))
    }

    @Test
    fun extensionFor_fallsBackToDisplayName() {
        assertEquals("HEIC", extensionFor(null, "IMG_0001.HEIC").uppercase())
        assertEquals("gz", extensionFor("application/octet-stream", "backup.tar.gz"))
    }

    @Test
    fun extensionFor_defaultsToJpg() {
        assertEquals("jpg", extensionFor(null, null))
        assertEquals("jpg", extensionFor("application/octet-stream", "no-extension"))
        assertEquals("jpg", extensionFor(null, "weird.name.!!!"))
    }
}
