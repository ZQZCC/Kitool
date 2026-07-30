package ka.kitool.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test
    fun `all built-in templates are valid HTTPS search URLs`() {
        SearchEngine.all
            .filter { it != SearchEngine.CUSTOM }
            .forEach { engine ->
                assertTrue(engine.id, SearchUrl.isValidTemplate(engine.template.orEmpty()))
            }
    }
}
