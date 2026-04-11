package gwanlang

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerTest {

    @BeforeEach
    fun resetErrorState() {
        GwanLang.hadError = false
    }

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
    fun `슬래시는 SLASH 토큰이다`() {
        val tokens = scan("/")

        assertEquals(TokenType.SLASH, tokens[0].type)
        assertEquals("/", tokens[0].lexeme)
        assertEquals(TokenType.EOF, tokens[1].type)
    }

    @Test
    fun `단일 라인 주석은 개행까지 무시된다`() {
        val tokens = scan("// this is a comment")

        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }

    @Test
    fun `주석은 개행에서 종료되고 이후 토큰을 스캔한다`() {
        // 참고: 개행에 따른 line 증가는 Cycle 6(공백/줄번호)에서 테스트한다.
        val tokens = scan("// comment\n+")

        assertEquals(TokenType.PLUS, tokens[0].type)
        assertEquals(TokenType.EOF, tokens[1].type)
    }

    @Test
    fun `1에서 2문자 비교 및 대입 연산자를 스캔한다`() {
        val cases = listOf(
            "!" to TokenType.BANG,
            "!=" to TokenType.BANG_EQUAL,
            "=" to TokenType.EQUAL,
            "==" to TokenType.EQUAL_EQUAL,
            ">" to TokenType.GREATER,
            ">=" to TokenType.GREATER_EQUAL,
            "<" to TokenType.LESS,
            "<=" to TokenType.LESS_EQUAL,
        )
        for ((src, expected) in cases) {
            val tokens = scan(src)
            assertEquals(2, tokens.size, "input='$src'")
            assertEquals(expected, tokens[0].type, "input='$src'")
            assertEquals(src, tokens[0].lexeme, "input='$src'")
        }
    }

    @Test
    fun `연속된 2문자 연산자를 구분한다`() {
        val tokens = scan("!===>=<=")

        assertEquals(
            listOf(
                TokenType.BANG_EQUAL,
                TokenType.EQUAL_EQUAL,
                TokenType.GREATER_EQUAL,
                TokenType.LESS_EQUAL,
                TokenType.EOF,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun `공백 탭 캐리지리턴은 무시된다`() {
        val tokens = scan("+ \t\r+")

        assertEquals(
            listOf(TokenType.PLUS, TokenType.PLUS, TokenType.EOF),
            tokens.map { it.type },
        )
    }

    @Test
    fun `개행은 줄 번호를 증가시킨다`() {
        val tokens = scan("+\n+\n+")

        assertEquals(1, tokens[0].line)
        assertEquals(2, tokens[1].line)
        assertEquals(3, tokens[2].line)
        assertEquals(3, tokens[3].line) // EOF는 마지막 줄을 그대로 유지
    }

    @Test
    fun `주석 이후 개행에서도 줄 번호가 증가한다`() {
        val tokens = scan("// line1\n+")

        assertEquals(TokenType.PLUS, tokens[0].type)
        assertEquals(2, tokens[0].line)
    }

    @Test
    fun `문자열 리터럴을 스캔한다`() {
        val tokens = scan("\"hello\"")

        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("\"hello\"", tokens[0].lexeme)
        assertEquals("hello", tokens[0].literal)
        assertFalse(GwanLang.hadError)
    }

    @Test
    fun `빈 문자열 리터럴`() {
        val tokens = scan("\"\"")

        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("", tokens[0].literal)
    }

    @Test
    fun `여러 줄에 걸친 문자열은 개행도 포함한다`() {
        val tokens = scan("\"a\nb\"")

        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("a\nb", tokens[0].literal)
        assertEquals(2, tokens[0].line) // 시작 줄이 아닌 완료 시점 줄
    }

    @Test
    fun `미종료 문자열은 에러를 기록한다`() {
        val tokens = scan("\"no end")

        assertTrue(GwanLang.hadError)
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `정수 리터럴은 Double 값으로 변환된다`() {
        val tokens = scan("123")

        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals("123", tokens[0].lexeme)
        assertEquals(123.0, tokens[0].literal)
    }

    @Test
    fun `소수 리터럴을 스캔한다`() {
        val tokens = scan("3.14")

        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals("3.14", tokens[0].lexeme)
        assertEquals(3.14, tokens[0].literal)
    }

    @Test
    fun `숫자 앞 뒤에 digit이 없는 점은 NUMBER에 포함되지 않는다`() {
        // `.5` → DOT + NUMBER(5.0)
        val dotFirst = scan(".5")
        assertEquals(
            listOf(TokenType.DOT, TokenType.NUMBER, TokenType.EOF),
            dotFirst.map { it.type },
        )
        assertEquals(5.0, dotFirst[1].literal)

        // `5.` → NUMBER(5.0) + DOT
        val dotLast = scan("5.")
        assertEquals(
            listOf(TokenType.NUMBER, TokenType.DOT, TokenType.EOF),
            dotLast.map { it.type },
        )
        assertEquals(5.0, dotLast[0].literal)
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
