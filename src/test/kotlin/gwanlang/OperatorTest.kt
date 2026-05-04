package gwanlang

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorTest {

    @BeforeEach
    fun resetErrorState() {
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }

    private fun scan(source: String): List<Token> = Scanner(source).scanTokens()

    private fun runAndCapture(source: String): String {
        val tokens = Scanner(source).scanTokens()
        val stmts = Parser(tokens).parse()
        val interp = Interpreter()
        val resolver = Resolver(interp)
        resolver.resolve(stmts)
        if (GwanLang.hadError) return ""
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(output))
        try {
            interp.interpret(stmts)
        } finally {
            System.setOut(originalOut)
        }
        return output.toString().trimEnd()
    }

    private fun evaluate(source: String): Any? {
        val tokens = Scanner("$source;").scanTokens()
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.Expression).expression
        return Interpreter().testEvaluate(expr)
    }

    // === TDD 사이클 1: Scanner — % 토큰 ===

    @Test
    fun `퍼센트 토큰을 스캔한다`() {
        val tokens = scan("10 % 3")
        assertEquals(
            listOf(TokenType.NUMBER, TokenType.PERCENT, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("%", tokens[1].lexeme)
    }

    // === TDD 사이클 2: Scanner — %= 토큰 ===

    @Test
    fun `퍼센트 이퀄 토큰을 스캔한다`() {
        val tokens = scan("x %= 3")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.PERCENT_EQUAL, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("%=", tokens[1].lexeme)
    }

    // === TDD 사이클 3: Scanner — +=, -= 토큰 ===

    @Test
    fun `플러스 이퀄 토큰을 스캔한다`() {
        val tokens = scan("x += 1")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.PLUS_EQUAL, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("+=", tokens[1].lexeme)
    }

    @Test
    fun `마이너스 이퀄 토큰을 스캔한다`() {
        val tokens = scan("y -= 2")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.MINUS_EQUAL, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("-=", tokens[1].lexeme)
    }

    // === TDD 사이클 4: Scanner — *=, /= 토큰 ===

    @Test
    fun `스타 이퀄 토큰을 스캔한다`() {
        val tokens = scan("x *= 2")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.STAR_EQUAL, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("*=", tokens[1].lexeme)
    }

    @Test
    fun `슬래시 이퀄 토큰을 스캔한다`() {
        val tokens = scan("y /= 3")
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.SLASH_EQUAL, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("/=", tokens[1].lexeme)
    }

    // === TDD 사이클 5: Scanner — /= vs // 구분 ===

    @Test
    fun `슬래시 이퀄과 주석을 구분한다`() {
        val tokens = scan("x /= 2; // comment")
        assertEquals(
            listOf(
                TokenType.IDENTIFIER, TokenType.SLASH_EQUAL, TokenType.NUMBER,
                TokenType.SEMICOLON, TokenType.EOF,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun `기존 슬래시와 주석이 여전히 동작한다`() {
        val tokens = scan("6 / 3 // division")
        assertEquals(
            listOf(TokenType.NUMBER, TokenType.SLASH, TokenType.NUMBER, TokenType.EOF),
            tokens.map { it.type },
        )
    }

    @Test
    fun `기존 단일 문자 연산자가 복합 대입과 공존한다`() {
        val tokens = scan("+ += - -= * *= / /= % %=")
        assertEquals(
            listOf(
                TokenType.PLUS, TokenType.PLUS_EQUAL,
                TokenType.MINUS, TokenType.MINUS_EQUAL,
                TokenType.STAR, TokenType.STAR_EQUAL,
                TokenType.SLASH, TokenType.SLASH_EQUAL,
                TokenType.PERCENT, TokenType.PERCENT_EQUAL,
                TokenType.EOF,
            ),
            tokens.map { it.type },
        )
    }

    // === TDD 사이클 6: Parser — % 우선순위 ===

    @Test
    fun `퍼센트는 곱셈과 같은 우선순위이다`() {
        // 1 + 2 % 3 → Binary(1, +, Binary(2, %, 3))
        val tokens = Scanner("1 + 2 % 3;").scanTokens()
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.Expression).expression
        assertTrue(expr is Expr.Binary)
        val binary = expr as Expr.Binary
        assertEquals(TokenType.PLUS, binary.op.type)
        assertTrue(binary.right is Expr.Binary)
        val right = binary.right as Expr.Binary
        assertEquals(TokenType.PERCENT, right.op.type)
    }

    // === TDD 사이클 7: Parser — 변수 복합 대입 디슈가링 ===

    @Test
    fun `변수 복합 대입이 Assign과 Binary로 디슈가링된다`() {
        // x += 1  →  Assign(x, Binary(Variable(x), +, 1))
        val tokens = Scanner("x += 1;").scanTokens()
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.Expression).expression
        assertTrue(expr is Expr.Assign)
        val assign = expr as Expr.Assign
        assertEquals("x", assign.name.lexeme)
        assertTrue(assign.value is Expr.Binary)
        val binary = assign.value as Expr.Binary
        assertEquals(TokenType.PLUS, binary.op.type)
        assertTrue(binary.left is Expr.Variable)
        assertEquals("x", (binary.left as Expr.Variable).name.lexeme)
    }

    // === TDD 사이클 8: Parser — 프로퍼티 복합 대입 디슈가링 ===

    @Test
    fun `프로퍼티 복합 대입이 Set과 Binary로 디슈가링된다`() {
        // obj.f += 1  →  Set(obj, f, Binary(Get(obj, f), +, 1))
        val tokens = Scanner("obj.f += 1;").scanTokens()
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.Expression).expression
        assertTrue(expr is Expr.Set)
        val set = expr as Expr.Set
        assertEquals("f", set.name.lexeme)
        assertTrue(set.value is Expr.Binary)
        val binary = set.value as Expr.Binary
        assertEquals(TokenType.PLUS, binary.op.type)
        assertTrue(binary.left is Expr.Get)
    }

    // === TDD 사이클 9: Parser — 잘못된 복합 대입 대상 ===

    @Test
    fun `리터럴에 복합 대입하면 파서 에러`() {
        val tokens = Scanner("1 += 2;").scanTokens()
        Parser(tokens).parse()
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 10: Interpreter — % 정수 ===

    @Test
    fun `정수 모듈로 연산을 평가한다`() {
        assertEquals("1", runAndCapture("print 10 % 3;"))
    }

    // === TDD 사이클 11: Interpreter — % 실수 ===

    @Test
    fun `실수 모듈로 연산을 평가한다`() {
        assertEquals("0", runAndCapture("print 7.5 % 2.5;"))
    }

    // === TDD 사이클 12: Interpreter — % 음수 ===

    @Test
    fun `음수 모듈로 연산의 부호는 피제수를 따른다`() {
        assertEquals("-1", runAndCapture("print -7 % 3;"))
    }

    @Test
    fun `양수를 음수로 모듈로하면 양수 결과`() {
        assertEquals("1", runAndCapture("print 7 % -3;"))
    }

    // === TDD 사이클 13: Interpreter — % 0으로 나누기 ===

    @Test
    fun `0으로 모듈로하면 런타임 에러`() {
        assertThrows<RuntimeError> { evaluate("10 % 0") }
    }

    // === TDD 사이클 14: Interpreter — % 타입 에러 ===

    @Test
    fun `문자열에 모듈로하면 런타임 에러`() {
        assertThrows<RuntimeError> { evaluate("\"a\" % 2") }
    }

    // === TDD 사이클 15: Interpreter — += 변수 ===

    @Test
    fun `변수에 += 복합 대입`() {
        assertEquals("15", runAndCapture("var x = 10; x += 5; print x;"))
    }

    // === TDD 사이클 16: Interpreter — -=, *=, /=, %= 변수 ===

    @Test
    fun `변수에 -= 복합 대입`() {
        assertEquals("7", runAndCapture("var x = 10; x -= 3; print x;"))
    }

    @Test
    fun `변수에 *= 복합 대입`() {
        assertEquals("20", runAndCapture("var x = 10; x *= 2; print x;"))
    }

    @Test
    fun `변수에 나누기= 복합 대입`() {
        assertEquals("5", runAndCapture("var x = 10; x /= 2; print x;"))
    }

    @Test
    fun `변수에 %= 복합 대입`() {
        assertEquals("1", runAndCapture("var x = 10; x %= 3; print x;"))
    }

    // === TDD 사이클 17: Interpreter — 프로퍼티 복합 대입 ===

    @Test
    fun `프로퍼티에 += 복합 대입`() {
        assertEquals("5", runAndCapture("""
            class C { init() { this.v = 0; } }
            var c = C();
            c.v += 5;
            print c.v;
        """.trimIndent()))
    }

    // === TDD 사이클 18: Interpreter — 연쇄 복합 대입 ===

    @Test
    fun `연쇄 복합 대입이 정상 동작한다`() {
        assertEquals("9", runAndCapture("var x = 1; x += 2; x *= 3; print x;"))
    }

    // === TDD 사이클 19: Interpreter — 복합 대입 + 표현식 ===

    @Test
    fun `복합 대입 우변에 표현식이 올 수 있다`() {
        assertEquals("16", runAndCapture("var x = 10; x += 2 * 3; print x;"))
    }

    @Test
    fun `for 루프에서 복합 대입을 사용한다`() {
        assertEquals("0\n1\n2", runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                print i;
            }
        """.trimIndent()))
    }
}
