package ka.kitool.save

import java.nio.charset.StandardCharsets

object FileNamePolicy {
    const val MAX_LENGTH = 180
    const val MAX_UTF8_BYTES = 240

    fun sanitize(rawName: String?, fallback: String): String {
        val source = rawName.orEmpty()
        val builder = StringBuilder(source.length)
        for (character in source) {
            builder.append(
                when {
                    character == '/' || character == '\\' -> '_'
                    character.isISOControl() -> '_'
                    else -> character
                }
            )
        }
        val cleaned =
            builder
                .toString()
                .trim()
                .trim('.')
                .ifEmpty { fallback }

        if (cleaned.length <= MAX_LENGTH && cleaned.utf8Size() <= MAX_UTF8_BYTES) return cleaned

        val extensionStart = cleaned.lastIndexOf('.')
        val extension =
            if (extensionStart in 1 until cleaned.lastIndex &&
                cleaned.length - extensionStart <= 21 &&
                cleaned.substring(extensionStart).utf8Size() <= 32
            ) {
                cleaned.substring(extensionStart)
            } else {
                ""
            }
        val baseSource = if (extension.isEmpty()) cleaned else cleaned.substring(0, extensionStart)
        val base =
            baseSource
                .takeUtf8(
                    maxChars = MAX_LENGTH - extension.length,
                    maxBytes = MAX_UTF8_BYTES - extension.utf8Size(),
                )
                .trimEnd()
        return base.ifEmpty { fallback.takeUtf8(MAX_LENGTH, MAX_UTF8_BYTES) } + extension
    }

    private fun String.takeUtf8(maxChars: Int, maxBytes: Int): String {
        val result = StringBuilder()
        var index = 0
        var bytes = 0
        while (index < length && result.length < maxChars) {
            val codePoint = codePointAt(index)
            val characters = String(Character.toChars(codePoint))
            val nextBytes = characters.utf8Size()
            if (result.length + characters.length > maxChars || bytes + nextBytes > maxBytes) break
            result.append(characters)
            bytes += nextBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    private fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size
}
