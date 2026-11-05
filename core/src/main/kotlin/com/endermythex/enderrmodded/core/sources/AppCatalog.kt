package com.endermythex.enderrmodded.core.sources

/**
 * Applications connues : nom lisible et emplacement sur les sources.
 * Les apps absentes de ce catalogue sont recherchées automatiquement sur APKMirror par nom de paquet.
 */
object AppCatalog {
    data class KnownApp(
        val packageName: String,
        val name: String,
        val apkMirrorPath: String? = null,
        val uptodownSlug: String? = null,
    )

    val apps: Map<String, KnownApp> = listOf(
        KnownApp("com.google.android.youtube", "YouTube", "google-inc/youtube", "youtube"),
        KnownApp("com.google.android.apps.youtube.music", "YouTube Music", "google-inc/youtube-music", "youtube-music"),
        KnownApp("com.reddit.frontpage", "Reddit", "redditinc/reddit", "reddit-official-app"),
        KnownApp("com.google.android.apps.photos", "Google Photos", "google-inc/photos", "google-photos"),
        KnownApp("com.twitter.android", "X (Twitter)", "x-corp/twitter", "twitter"),
        KnownApp("com.zhiliaoapp.musically", "TikTok", "tiktok-pte-ltd/tik-tok-including-musical-ly", "tik-tok"),
        KnownApp("com.ss.android.ugc.trill", "TikTok (Asie)", "tiktok-pte-ltd/tik-tok", null),
        KnownApp("com.instagram.android", "Instagram", "instagram/instagram-instagram", "instagram"),
        KnownApp("com.spotify.music", "Spotify", "spotify-ab/spotify-music-and-podcasts-for-android", "spotify"),
        KnownApp("tv.twitch.android.app", "Twitch", "twitch-interactive-inc/twitch", "twitch"),
        KnownApp("com.duolingo", "Duolingo", "duolingo/duolingo-duolingo", "duolingo"),
        KnownApp("com.strava", "Strava", "strava-inc/strava-running-and-cycling-gps", "strava-running-and-cycling-gps"),
        KnownApp("com.myfitnesspal.android", "MyFitnessPal", "myfitnesspal-inc/calorie-counter-myfitnesspal", "myfitnesspal"),
        KnownApp("com.pandora.android", "Pandora", "pandora/pandora-music-podcasts", "pandora-radio"),
        KnownApp("com.amazon.avod.thirdpartyclient", "Prime Video", "amazon-mobile-llc/amazon-prime-video", "amazon-prime-video"),
        KnownApp("com.facebook.orca", "Messenger", "facebook-2/messenger", "facebook-messenger"),
        KnownApp("com.tumblr", "Tumblr", "tumblr-inc/tumblr", "tumblr"),
        KnownApp("com.google.android.apps.recorder", "Enregistreur Google", "google-inc/google-recorder", null),
        KnownApp("com.crunchyroll.crunchyroid", "Crunchyroll", "ellation-inc/crunchyroll", "crunchyroll"),
        KnownApp("ginlemon.flowerfree", "Smart Launcher", "smart-launcher-team/smart-launcher-5", "smart-launcher"),
    ).associateBy { it.packageName }

    fun nameOf(packageName: String): String =
        apps[packageName]?.name ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
