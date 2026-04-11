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

    @Test
    fun `단일 문자 토큰을 각각 스캔한다`() {
        val cases = listOf(
            "(" to TokenType.LEFT_PAREN,
            ")" to TokenType.RIGHT_PAREN,
            "{" to TokenType.LEFT_BRACE,
            "}" to TokenType.RIGHT_BRACE,
            "," to TokenType.COMMA,
            "." to TokenType.DOT,
            "-" to TokenType.MINUS,
            "+" to TokenType.PLUS,
            ";" to TokenType.SEMICOLON,
            "*" to TokenType.STAR,
        )
        for ((src, expected) in cases) {
            val tokens = scan(src)
            assertEquals(2, tokens.size, "input='$src'")
            assertEquals(expected, tokens[0].type, "input='$src'")
            assertEquals(src, tokens[0].lexeme, "input='$src'")
            assertEquals(TokenType.EOF, tokens[1].type, "input='$src'")
        }
    }

    @Test
    fun `연속된 단일 문자 토큰을 순서대로 스캔한다`() {
        val tokens = scan("(){},.-+;*")

        val expected = listOf(
            TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN,
            TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE,
            TokenType.COMMA, TokenType.DOT,
            TokenType.MINUS, TokenType.PLUS,
            TokenType.SEMICOLON, TokenType.STAR,
            TokenType.EOF,
        )
        assertEquals(expected, tokens.map { it.type })
    }
}
