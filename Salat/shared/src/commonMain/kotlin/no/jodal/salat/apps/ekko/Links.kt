package no.jodal.salat.apps.ekko

/** En lenke Ekko kan hente lyd fra. */
sealed interface EkkoLink {
    val url: String

    data class YouTube(val videoId: String, override val url: String) : EkkoLink

    /** En post på X (Twitter) med video. Videoen hentes fra den samme åpne adressen som innebygde poster bruker. */
    data class X(val postId: String, override val url: String) : EkkoLink {
        /** Adressen som gir posten som JSON, uten innlogging. */
        val apiUrl: String get() = "https://cdn.syndication.twimg.com/tweet-result?id=$postId&lang=no&token=${xToken(postId)}"
    }

    /** En annen nettside, f.eks. Snapchat Spotlight. Ekko leter etter en video på siden. */
    data class Web(override val url: String) : EkkoLink {
        val isSnapchat: Boolean get() = hostOf(url).let { it == "snapchat.com" || it.endsWith(".snapchat.com") }
    }

    companion object {
        /**
         * Finner lenken i [text], som kan være mer enn bare lenken (f.eks. «Se denne! https://…» fra
         * delingsarket). Gir `null` hvis det ikke er noen lenke der.
         */
        fun parse(text: String): EkkoLink? {
            val url = findUrl(text) ?: return null
            youTubeId(url)?.let { return YouTube(it, url) }
            xPostId(url)?.let { return X(it, url) }
            return Web(url)
        }

        private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val bareYouTube = Regex("""(?:www\.|m\.)?(?:youtube\.com|youtu\.be)/[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val bareX = Regex("""(?:www\.|mobile\.)?(?:x\.com|twitter\.com)/[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val idPattern = Regex("""^[A-Za-z0-9_-]{11}$""")

        private fun findUrl(text: String): String? {
            urlPattern.find(text)?.let { return it.value.trimEnd('.', ',', ')', '!', '?') }
            return (bareYouTube.find(text) ?: bareX.find(text))?.let { "https://" + it.value.trimEnd('.', ',', ')', '!', '?') }
        }

        /** IDen til en YouTube-video, fra vanlige lenker, Shorts, youtu.be og innebygde videoer. */
        fun youTubeId(url: String): String? {
            val host = hostOf(url)
            val path = url.substringAfter("://").substringAfter('/', "").substringBefore('#')
            val pathOnly = path.substringBefore('?')
            val query = path.substringAfter('?', "")
            val candidate = when (host) {
                "youtu.be" -> pathOnly.substringBefore('/')
                "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> {
                    val parts = pathOnly.split('/')
                    when (parts.firstOrNull()) {
                        "watch" -> query.split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                        "shorts", "embed", "live", "v" -> parts.getOrNull(1)
                        else -> null
                    }
                }
                else -> null
            }
            return candidate?.takeIf { idPattern.matches(it) }
        }

        private val xHosts = setOf(
            "x.com", "mobile.x.com", "twitter.com", "mobile.twitter.com",
            "fxtwitter.com", "vxtwitter.com", "fixupx.com", "fixvx.com",
        )
        private val postIdPattern = Regex("""^[0-9]{1,20}$""")

        /** IDen til en post på X, fra f.eks. `x.com/navn/status/123`, `twitter.com/i/status/123` eller `.../status/123/video/1`. */
        fun xPostId(url: String): String? {
            if (hostOf(url) !in xHosts) return null
            val parts = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#').split('/')
            val index = parts.indexOfFirst { it == "status" || it == "statuses" }
            return parts.getOrNull(index + 1)?.takeIf { index >= 0 && postIdPattern.matches(it) }
        }

        /**
         * Nøkkelen den åpne adressen til X vil ha, regnet ut som i nettsidene som bygger inn poster:
         * `(id / 1e15 * π)` skrevet med grunntall 36, uten nuller og punktum.
         */
        internal fun xToken(postId: String): String {
            val value = (postId.toDoubleOrNull() ?: return "0") / 1e15 * kotlin.math.PI
            val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
            var whole = value.toLong()
            var fraction = value - whole
            val out = StringBuilder()
            if (whole == 0L) out.append('0')
            while (whole > 0) {
                out.insert(0, digits[(whole % 36).toInt()])
                whole /= 36
            }
            out.append('.')
            repeat(12) {
                fraction *= 36
                val digit = fraction.toInt()
                out.append(digits[digit])
                fraction -= digit
            }
            return out.toString().replace(Regex("0+|\\."), "")
        }

        internal fun hostOf(url: String): String =
            url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase().removePrefix("www.")
    }
}
