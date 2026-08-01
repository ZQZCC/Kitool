package ka.kitool.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test
    fun `all built-in templates are valid HTTPS search URLs`() {
        for (id in searchEngineIds()) {
            val template = searchTemplateFor(id) ?: continue
            assertTrue(id, SearchUrl.isValidTemplate(template))
        }
    }
}
