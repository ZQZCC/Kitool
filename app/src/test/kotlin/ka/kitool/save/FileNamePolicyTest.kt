package ka.kitool.save

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamePolicyTest {
    @Test
    fun `unsafe separators and controls are replaced`() {
        assertEquals(
            "a_b_c_.txt",
            FileNamePolicy.sanitize("a/b\\c\u0000.txt", "文件"),
        )
    }

    @Test
    fun `empty names use fallback`() {
        assertEquals("文件", FileNamePolicy.sanitize("...", "文件"))
    }

    @Test
    fun `long names keep a short extension`() {
        val result = FileNamePolicy.sanitize("a".repeat(300) + ".pdf", "文件")
        assertTrue(result.length <= FileNamePolicy.MAX_LENGTH)
        assertTrue(result.endsWith(".pdf"))
    }

    @Test
    fun `multibyte names stay within filesystem byte budget`() {
        val result = FileNamePolicy.sanitize("文".repeat(180) + ".pdf", "文件")
        assertTrue(result.toByteArray(StandardCharsets.UTF_8).size <= FileNamePolicy.MAX_UTF8_BYTES)
        assertTrue(result.endsWith(".pdf"))
    }
}
