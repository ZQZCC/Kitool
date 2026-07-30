package ka.kitool.search

import ka.kitool.R

enum class SearchEngine(
    val id: String,
    val titleRes: Int,
    val template: String?,
) {
    GOOGLE("google", R.string.engine_google, "https://www.google.com/search?q={query}"),
    BING("bing", R.string.engine_bing, "https://www.bing.com/search?q={query}"),
    DUCK_DUCK_GO(
        "duckduckgo",
        R.string.engine_duckduckgo,
        "https://duckduckgo.com/?q={query}",
    ),
    BAIDU("baidu", R.string.engine_baidu, "https://www.baidu.com/s?wd={query}"),
    SOGOU("sogou", R.string.engine_sogou, "https://www.sogou.com/web?query={query}"),
    SO("so", R.string.engine_so, "https://www.so.com/s?q={query}"),
    YAHOO("yahoo", R.string.engine_yahoo, "https://search.yahoo.com/search?p={query}"),
    YANDEX("yandex", R.string.engine_yandex, "https://yandex.com/search/?text={query}"),
    BRAVE("brave", R.string.engine_brave, "https://search.brave.com/search?q={query}"),
    CUSTOM("custom", R.string.engine_custom, null),
    ;

    companion object {
        internal val all = values()

        fun fromId(id: String?): SearchEngine {
            for (engine in all) {
                if (engine.id == id) return engine
            }
            return GOOGLE
        }
    }
}
