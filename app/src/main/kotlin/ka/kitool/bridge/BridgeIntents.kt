package ka.kitool.bridge

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build
import java.util.regex.Pattern
import ka.kitool.search.SearchUrl

object BridgeIntents {
    fun shareToOpen(context: Context, source: Intent): Intent? {
        if (source.action != Intent.ACTION_SEND) return null

        val stream =
            try {
                getUriExtra(source, Intent.EXTRA_STREAM)
            } catch (_: BadParcelableException) {
                return null
            } catch (_: ClassCastException) {
                return null
            }
        if (stream != null) {
            if (!stream.scheme.equals("content", ignoreCase = true)) return null
            val normalizedStream = stream.normalizeScheme()
            val clipUris = clipContentUris(source)
            if (
                clipUris.size > 1 ||
                    (clipUris.isNotEmpty() && clipUris[0] != normalizedStream)
            ) {
                return null
            }
            return openIntent(
                normalizedStream,
                resolveMimeType(context, normalizedStream, source.type),
            )
        }

        val sharedText =
            runCatching { source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }
                .getOrNull()
                ?.trim()
        val webUrl = if (sharedText != null) SearchUrl.directHttpUrl(sharedText) else null
        val webIntent =
            if (webUrl != null) {
                openIntent(Uri.parse(webUrl).normalizeScheme(), null)
            } else {
                null
            }
        if (source.type?.startsWith("text/", ignoreCase = true) == true && webIntent != null) {
            return webIntent
        }

        val clipUris = clipContentUris(source)
        if (clipUris.size > 1) return null
        if (clipUris.size == 1) {
            val uri = clipUris[0]
            return openIntent(uri, resolveMimeType(context, uri, source.type))
        }
        return webIntent
    }

    private fun openIntent(uri: Uri, mimeType: String?): Intent =
        if (mimeType != null) {
            val normalizedUri = uri.normalizeScheme()
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(normalizedUri, mimeType)
                clipData = ClipData.newRawUri("", normalizedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri.normalizeScheme()).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }

    fun openToShare(context: Context, source: Intent): Intent? {
        if (source.action != Intent.ACTION_VIEW) return null
        val uri = source.data?.normalizeScheme() ?: return null
        return when {
            uri.scheme.equals("content", ignoreCase = true) -> {
                val mimeType = resolveMimeType(context, uri, source.type)
                Intent(Intent.ACTION_SEND).apply {
                    setTypeAndNormalize(mimeType)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true) -> {
                val url = SearchUrl.directHttpUrl(uri.toString()) ?: return null
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            }
            else -> null
        }
    }

    fun hasExternalHandler(context: Context, target: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(target, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName != context.packageName }

    private fun clipContentUris(source: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        try {
            source.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(uris::add)
                }
            }
        } catch (_: BadParcelableException) {
            return emptyList()
        } catch (_: ClassCastException) {
            return emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        }
        val result = ArrayList<Uri>(uris.size)
        val seen = HashSet<Uri>(uris.size)
        for (uri in uris) {
            if (!uri.scheme.equals("content", ignoreCase = true)) continue
            val normalized = uri.normalizeScheme()
            if (seen.add(normalized)) result.add(normalized)
        }
        return result
    }

    private fun resolveMimeType(
        context: Context,
        uri: Uri,
        incomingType: String?,
    ): String {
        val resolved = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (resolved != null && isSafeMimeType(resolved)) return resolved
        if (incomingType != null && isSafeMimeType(incomingType)) return incomingType
        return DEFAULT_MIME_TYPE
    }

    private fun getUriExtra(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }

    private fun isSafeMimeType(value: String): Boolean =
        value.length <= 127 && MIME_TYPE.matcher(value).matches()

    private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    private val MIME_TYPE =
        Pattern.compile(
            "(?:\\*/\\*|[A-Za-z0-9!#$%&'*+.^_`|~\\-]+/" +
                "(?:\\*|[A-Za-z0-9!#$%&'*+.^_`|~\\-]+))"
        )
}
