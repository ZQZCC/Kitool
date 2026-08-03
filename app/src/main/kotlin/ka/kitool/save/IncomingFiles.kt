package ka.kitool.save

import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Build

object IncomingFiles {
    fun from(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        try {
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.let(uris::add)
                Intent.ACTION_SEND -> getUriExtra(intent, Intent.EXTRA_STREAM)?.let(uris::add)
                Intent.ACTION_SEND_MULTIPLE ->
                    getUriListExtra(intent, Intent.EXTRA_STREAM)?.let(uris::addAll)
                else -> return emptyList()
            }

            val clipData = intent.clipData
            if (clipData != null) {
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
            if (
                uri.scheme.equals("content", ignoreCase = true) &&
                    seen.add(uri)
            ) {
                result.add(uri)
            }
        }
        return result
    }

    private fun getUriExtra(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }

    private fun getUriListExtra(intent: Intent, key: String): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(key)
        }
}
