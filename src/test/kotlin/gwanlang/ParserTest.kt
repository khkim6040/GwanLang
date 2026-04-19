package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ParserTest {

    private fun parse(source: String): Expr? {
        val tokens = Scanner(source).scanTokens()
        return Parser(tokens).parse()
    }

    // --- 사이클 2: 리터럴 ---

    @Test
    fun `숫자 리터럴을 파싱한다`() {
        val expr = parse("123")
        assertIs<Expr.Literal>(expr)
        assertEquals(123.0, expr.value)
    }

    @Test
    fun `소수점 숫자 리터럴을 파싱한다`() {
        val expr = parse("3.14")
        assertIs<Expr.Literal>(expr)
        assertEquals(3.14, expr.value)
    }

    @Test
    fun `문자열 리터럴을 파싱한다`() {
        val expr = parse("\"hello\"")
        assertIs<Expr.Literal>(expr)
        assertEquals("hello", expr.value)
    }

    @Test
    fun `true를 파싱한다`() {
        val expr = parse("true")
        assertIs<Expr.Literal>(expr)
        assertEquals(true, expr.value)
    }

    @Test
    fun `false를 파싱한다`() {
        val expr = parse("false")
        assertIs<Expr.Literal>(expr)
        assertEquals(false, expr.value)
    }

    @Test
    fun `nil을 파싱한다`() {
        val expr = parse("nil")
        assertIs<Expr.Literal>(expr)
        assertNull(expr.value)
    }

    // --- 사이클 3: 단항 연산자 ---

    @Test
    fun `단항 마이너스를 파싱한다`() {
        val expr = parse("-1")
        assertIs<Expr.Unary>(expr)
        assertEquals(TokenType.MINUS, expr.op.type)
        assertIs<Expr.Literal>(expr.right)
        assertEquals(1.0, (expr.right as Expr.Literal).value)
    }

    @Test
    fun `단항 NOT을 파싱한다`() {
        val expr = parse("!true")
        assertIs<Expr.Unary>(expr)
        assertEquals(TokenType.BANG, expr.op.type)
        assertIs<Expr.Literal>(expr.right)
        assertEquals(true, (expr.right as Expr.Literal).value)
    }

    @Test
    fun `중첩 단항 연산자를 파싱한다`() {
        val expr = parse("!!false")
        assertIs<Expr.Unary>(expr)
        assertEquals(TokenType.BANG, expr.op.type)
        val inner = expr.right
        assertIs<Expr.Unary>(inner)
        assertEquals(TokenType.BANG, inner.op.type)
    }

    // --- 사이클 4: factor (*, /) ---

    @Test
    fun `곱셈을 파싱한다`() {
        val expr = parse("2 * 3")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.STAR, expr.op.type)
        assertEquals(2.0, (expr.left as Expr.Literal).value)
        assertEquals(3.0, (expr.right as Expr.Literal).value)
    }

    @Test
    fun `나눗셈을 파싱한다`() {
        val expr = parse("6 / 2")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.SLASH, expr.op.type)
    }

    // --- 사이클 5: term (+, -) ---

    @Test
    fun `덧셈을 파싱한다`() {
        val expr = parse("1 + 2")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.PLUS, expr.op.type)
        assertEquals(1.0, (expr.left as Expr.Literal).value)
        assertEquals(2.0, (expr.right as Expr.Literal).value)
    }

    @Test
    fun `뺄셈을 파싱한다`() {
        val expr = parse("5 - 3")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.MINUS, expr.op.type)
    }

    // --- 사이클 6: comparison (>, >=, <, <=) ---

    @Test
    fun `비교 연산자를 파싱한다`() {
        val expr = parse("1 < 2")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.LESS, expr.op.type)
    }

    @Test
    fun `크거나 같음 연산자를 파싱한다`() {
        val expr = parse("3 >= 3")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.GREATER_EQUAL, expr.op.type)
    }

    // --- 사이클 7: equality (==, !=) ---

    @Test
    fun `동등 연산자를 파싱한다`() {
        val expr = parse("1 == 1")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.EQUAL_EQUAL, expr.op.type)
    }

    @Test
    fun `부등 연산자를 파싱한다`() {
        val expr = parse("\"a\" != \"b\"")
        assertIs<Expr.Binary>(expr)
        assertEquals(TokenType.BANG_EQUAL, expr.op.type)
    }
}
