package ka.kitool.settings

import android.content.Context
import ka.kitool.search.searchTemplateFor

class SettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readSearchTemplate(): String {
        return searchTemplateFor(preferences.getString(KEY_SEARCH_ENGINE, null))
            ?: preferences.getString(
                KEY_CUSTOM_SEARCH_TEMPLATE,
                DEFAULT_CUSTOM_TEMPLATE,
            )
            ?: DEFAULT_CUSTOM_TEMPLATE
    }

    companion object {
        internal const val FILE_NAME = "settings"
        internal const val KEY_SEARCH_ENGINE = "search_engine"
        internal const val KEY_CUSTOM_SEARCH_TEMPLATE = "custom_search_template"
        internal const val DEFAULT_CUSTOM_TEMPLATE = "https://www.google.com/search?q={query}"
    }
}
