package ka.kitool.search

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import ka.kitool.R
import ka.kitool.settings.SettingsActivity
import ka.kitool.settings.SettingsStore

class SearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchSearch(intent)
    }

    private fun dispatchSearch(source: Intent) {
        val text = normalizeText(extractText(source))
        if (text.isNullOrEmpty()) {
            showAndFinish(R.string.search_error_empty)
            return
        }

        val url =
            SearchUrl.build(
                text = text,
                template = SettingsStore(this).readSearchTemplate(),
            )
        if (url == null) {
            showAndFinish(R.string.search_error_template)
            return
        }

        try {
            startActivity(customTabIntent(url))
            finish()
        } catch (_: ActivityNotFoundException) {
            showAndFinish(R.string.search_error_browser)
        } catch (_: SecurityException) {
            showAndFinish(R.string.search_error_browser)
        }
    }

    private fun customTabIntent(url: String): Intent {
        val actionButton =
            Bundle().apply {
                putInt(CUSTOM_ACTION_ID, TOOLBAR_ACTION_BUTTON_ID)
                putParcelable(CUSTOM_ACTION_ICON, settingsIcon())
                putString(CUSTOM_ACTION_DESCRIPTION, getString(R.string.settings_title))
                putParcelable(CUSTOM_ACTION_PENDING_INTENT, settingsPendingIntent())
            }
        val session = Bundle().apply { putBinder(EXTRA_SESSION, null) }

        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            putExtras(session)
            putExtra(EXTRA_ACTION_BUTTON, actionButton)
            putExtra(EXTRA_TINT_ACTION_BUTTON, true)
            putExtra(EXTRA_COLOR_SCHEME, COLOR_SCHEME_SYSTEM)
            putExtra(EXTRA_ENABLE_URL_BAR_HIDING, true)
            putExtra(EXTRA_TITLE_VISIBILITY, SHOW_PAGE_TITLE)
            putExtra(EXTRA_SHARE_STATE, SHARE_STATE_OFF)
            putExtra(EXTRA_DEFAULT_SHARE_MENU_ITEM, false)
        }
    }

    @Suppress("DEPRECATION")
    private fun settingsPendingIntent(): PendingIntent {
        val options =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        if (Build.VERSION.SDK_INT >= 36) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        } else {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                    )
                    .toBundle()
            } else {
                null
            }
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options,
        )
    }

    private fun settingsIcon(): Bitmap {
        val drawable = requireNotNull(getDrawable(R.drawable.ic_settings))
        val bitmap =
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888,
            )
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun extractText(intent: Intent): CharSequence? =
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
            else -> null
        }

    private fun normalizeText(value: CharSequence?): String? {
        val text = value?.toString() ?: return null
        var sanitized: StringBuilder? = null
        for (index in text.indices) {
            val character = text[index]
            if (character == '\r' || character == '\n' || character == '\u0000') {
                if (sanitized == null) sanitized = StringBuilder(text)
                sanitized.setCharAt(index, ' ')
            }
        }
        return (sanitized?.toString() ?: text).trim()
    }

    private fun showAndFinish(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
        const val EXTRA_ACTION_BUTTON =
            "android.support.customtabs.extra.ACTION_BUTTON_BUNDLE"
        const val EXTRA_TINT_ACTION_BUTTON =
            "android.support.customtabs.extra.TINT_ACTION_BUTTON"
        const val EXTRA_COLOR_SCHEME = "androidx.browser.customtabs.extra.COLOR_SCHEME"
        const val EXTRA_ENABLE_URL_BAR_HIDING =
            "android.support.customtabs.extra.ENABLE_URLBAR_HIDING"
        const val EXTRA_TITLE_VISIBILITY =
            "android.support.customtabs.extra.TITLE_VISIBILITY"
        const val EXTRA_SHARE_STATE = "androidx.browser.customtabs.extra.SHARE_STATE"
        const val EXTRA_DEFAULT_SHARE_MENU_ITEM =
            "android.support.customtabs.extra.SHARE_MENU_ITEM"

        const val CUSTOM_ACTION_ID = "android.support.customtabs.customaction.ID"
        const val CUSTOM_ACTION_ICON = "android.support.customtabs.customaction.ICON"
        const val CUSTOM_ACTION_DESCRIPTION =
            "android.support.customtabs.customaction.DESCRIPTION"
        const val CUSTOM_ACTION_PENDING_INTENT =
            "android.support.customtabs.customaction.PENDING_INTENT"

        const val TOOLBAR_ACTION_BUTTON_ID = 0
        const val COLOR_SCHEME_SYSTEM = 0
        const val SHOW_PAGE_TITLE = 1
        const val SHARE_STATE_OFF = 2
    }
}
