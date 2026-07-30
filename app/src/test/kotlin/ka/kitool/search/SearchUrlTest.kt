package ka.kitool.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUrlTest {
    @Test
    fun `complete web link opens directly`() {
        assertEquals(
            "https://example.com/a?b=1",
            SearchUrl.build(
                " https://example.com/a?b=1 ",
                "https://www.google.com/search?q={query}",
            ),
        )
    }

    @Test
    fun `direct web link accepts an uppercase scheme`() {
        assertEquals(
            "HTTP://example.com/path",
            SearchUrl.directHttpUrl("HTTP://example.com/path"),
        )
    }

    @Test
    fun `query is UTF-8 encoded without plus spaces`() {
        assertEquals(
            "https://example.com/?q=%E4%B8%AD%E6%96%87%20a%26b",
            SearchUrl.build(
                "中文 a&b",
                "https://example.com/?q={query}",
            ),
        )
    }

    @Test
    fun `template requires https and one placeholder`() {
        assertTrue(SearchUrl.isValidTemplate("https://example.com/?q={query}"))
        assertFalse(SearchUrl.isValidTemplate("http://example.com/?q={query}"))
        assertFalse(SearchUrl.isValidTemplate("https://example.com/"))
        assertFalse(
            SearchUrl.isValidTemplate("https://example.com/{query}?q={query}")
        )
    }

    @Test
    fun `blank query has no destination`() {
        assertNull(
            SearchUrl.build(
                " ",
                "https://example.com/?q={query}",
            )
        )
    }
}
