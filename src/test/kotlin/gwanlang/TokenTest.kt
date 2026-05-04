package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenTest {

    @Test
    fun `Token은 type, lexeme, literal, line 필드를 가진다`() {
        val token = Token(TokenType.IDENTIFIER, "foo", null, 1)

        assertEquals(TokenType.IDENTIFIER, token.type)
        assertEquals("foo", token.lexeme)
        assertNull(token.literal)
        assertEquals(1, token.line)
    }

    @Test
    fun `Token은 literal 값으로 임의 타입을 가질 수 있다`() {
        val stringToken = Token(TokenType.STRING, "\"hi\"", "hi", 2)
        val numberToken = Token(TokenType.NUMBER, "3.14", 3.14, 3)

        assertEquals("hi", stringToken.literal)
        assertEquals(3.14, numberToken.literal)
    }

    @Test
    fun `TokenType은 스펙에 정의된 모든 토큰 종류를 포함한다`() {
        // 단일 문자 (11종)
        TokenType.LEFT_PAREN; TokenType.RIGHT_PAREN
        TokenType.LEFT_BRACE; TokenType.RIGHT_BRACE
        TokenType.COMMA; TokenType.DOT
        TokenType.MINUS; TokenType.PLUS
        TokenType.SEMICOLON; TokenType.SLASH; TokenType.STAR

        // 1~2 문자 (14종)
        TokenType.BANG; TokenType.BANG_EQUAL
        TokenType.EQUAL; TokenType.EQUAL_EQUAL
        TokenType.GREATER; TokenType.GREATER_EQUAL
        TokenType.LESS; TokenType.LESS_EQUAL
        TokenType.PERCENT
        TokenType.PLUS_EQUAL; TokenType.MINUS_EQUAL
        TokenType.STAR_EQUAL; TokenType.SLASH_EQUAL; TokenType.PERCENT_EQUAL

        // 리터럴 (3종)
        TokenType.IDENTIFIER; TokenType.STRING; TokenType.NUMBER

        // 키워드 (16종)
        TokenType.AND; TokenType.CLASS; TokenType.ELSE; TokenType.FALSE
        TokenType.FUN; TokenType.FOR; TokenType.IF; TokenType.NIL
        TokenType.OR; TokenType.PRINT; TokenType.RETURN; TokenType.SUPER
        TokenType.THIS; TokenType.TRUE; TokenType.VAR; TokenType.WHILE

        TokenType.EOF

        // 총 개수 검증: 11 + 14 + 3 + 16 + 1 = 45
        assertEquals(45, TokenType.values().size)
    }
}
