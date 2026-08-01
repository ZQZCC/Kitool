package ka.kitool.search

import ka.kitool.R

internal const val CUSTOM_SEARCH_ENGINE_ID = "custom"

internal fun searchTemplateFor(id: String?): String? =
    when (id) {
        "bing" -> "https://www.bing.com/search?q={query}"
        "duckduckgo" -> "https://duckduckgo.com/?q={query}"
        "baidu" -> "https://www.baidu.com/s?wd={query}"
        "sogou" -> "https://www.sogou.com/web?query={query}"
        "so" -> "https://www.so.com/s?q={query}"
        "yahoo" -> "https://search.yahoo.com/search?p={query}"
        "yandex" -> "https://yandex.com/search/?text={query}"
        "brave" -> "https://search.brave.com/search?q={query}"
        CUSTOM_SEARCH_ENGINE_ID -> null
        else -> "https://www.google.com/search?q={query}"
    }

internal fun searchEngineIds(): Array<String> =
    arrayOf(
        "google",
        "bing",
        "duckduckgo",
        "baidu",
        "sogou",
        "so",
        "yahoo",
        "yandex",
        "brave",
        CUSTOM_SEARCH_ENGINE_ID,
    )

internal fun searchEngineTitles(): IntArray =
    intArrayOf(
        R.string.engine_google,
        R.string.engine_bing,
        R.string.engine_duckduckgo,
        R.string.engine_baidu,
        R.string.engine_sogou,
        R.string.engine_so,
        R.string.engine_yahoo,
        R.string.engine_yandex,
        R.string.engine_brave,
        R.string.engine_custom,
    )
