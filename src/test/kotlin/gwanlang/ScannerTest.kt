package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScannerTest {

    private fun scan(source: String): List<Token> = Scanner(source).scanTokens()

    @Test
    fun `빈 입력은 EOF 토큰 하나만 반환한다`() {
        val tokens = scan("")

        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
        assertEquals("", tokens[0].lexeme)
        assertEquals(1, tokens[0].line)
    }
}
