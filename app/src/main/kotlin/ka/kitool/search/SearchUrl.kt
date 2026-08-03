package ka.kitool.search

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

object SearchUrl {
    const val QUERY_PLACEHOLDER = "{query}"

    fun build(
        text: String,
        template: String,
    ): String? {
        val query = text.trim()
        if (query.isEmpty()) return null
        directHttpUrl(query)?.let { return it }
        if (!isValidTemplate(template)) return null
        return template.replace(QUERY_PLACEHOLDER, encodeQuery(query))
    }

    fun isValidTemplate(template: String): Boolean {
        val placeholderIndex = template.indexOf(QUERY_PLACEHOLDER)
        if (
            placeholderIndex < 0 ||
                template.indexOf(
                    QUERY_PLACEHOLDER,
                    placeholderIndex + QUERY_PLACEHOLDER.length,
                ) >= 0
        ) {
            return false
        }
        return try {
            val uri = URI(template.replace(QUERY_PLACEHOLDER, "query"))
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }

    fun directHttpUrl(text: String): String? {
        if (
            !text.startsWith("http://", ignoreCase = true) &&
                !text.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val uri = URI(text)
            if (!uri.host.isNullOrBlank()) uri.toASCIIString() else null
        } catch (_: URISyntaxException) {
            null
        }
    }

    internal fun encodeQuery(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
