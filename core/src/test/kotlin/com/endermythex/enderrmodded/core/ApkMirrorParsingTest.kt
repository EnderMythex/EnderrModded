package com.endermythex.enderrmodded.core

import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkMirrorParsingTest {
    @Test
    fun ownPackageIsTheAccentColorLink() {
        val html = """
            <a href="https://play.google.com/store/apps/details?id=com.ss.android.ugc.trill">TikTok Asia</a>
            <a href="https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically" class="accent_color">Play Store</a>
        """
        assertEquals("com.zhiliaoapp.musically", ApkMirrorSource().pagePackage(html))
    }
}
