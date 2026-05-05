package gwanlang

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopControlTest {

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

    // === TDD 사이클 1: Scanner — break/continue 키워드 ===

    @Test
    fun `break 키워드를 스캔한다`() {
        val tokens = scan("break;")
        assertEquals(
            listOf(TokenType.BREAK, TokenType.SEMICOLON, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("break", tokens[0].lexeme)
    }

    @Test
    fun `continue 키워드를 스캔한다`() {
        val tokens = scan("continue;")
        assertEquals(
            listOf(TokenType.CONTINUE, TokenType.SEMICOLON, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("continue", tokens[0].lexeme)
    }

    // === TDD 사이클 2: Parser — break 파싱 ===

    @Test
    fun `break 문을 파싱한다`() {
        val tokens = scan("break;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val stmt = stmts[0] as Stmt.Break
        assertEquals("break", stmt.keyword.lexeme)
    }

    // === TDD 사이클 3: Parser — continue 파싱 ===

    @Test
    fun `continue 문을 파싱한다`() {
        val tokens = scan("continue;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val stmt = stmts[0] as Stmt.Continue
        assertEquals("continue", stmt.keyword.lexeme)
    }

    // === TDD 사이클 4: Parser — for 문 Stmt.For 반환 ===

    @Test
    fun `for 문을 Stmt_For로 파싱한다`() {
        val tokens = scan("for (var i = 0; i < 10; i += 1) print i;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val forStmt = stmts[0] as Stmt.For
        assertTrue(forStmt.initializer is Stmt.Var)
        assertTrue(forStmt.condition is Expr.Binary)
        assertTrue(forStmt.increment is Expr.Assign)
        assertTrue(forStmt.body is Stmt.Print)
    }

    // === TDD 사이클 5: Interpreter — while + break ===

    @Test
    fun `while 루프에서 break로 탈출한다`() {
        val result = runAndCapture("""
            var i = 0;
            while (true) {
                if (i >= 3) break;
                print i;
                i += 1;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // === TDD 사이클 6: Interpreter — while + continue ===

    @Test
    fun `while 루프에서 continue로 다음 반복으로 건너뛴다`() {
        val result = runAndCapture("""
            var i = 0;
            while (i < 5) {
                i += 1;
                if (i % 2 == 0) continue;
                print i;
            }
        """.trimIndent())
        assertEquals("1\n3\n5", result)
    }

    // === TDD 사이클 7: Interpreter — for + break ===

    @Test
    fun `for 루프에서 break로 탈출한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 10; i += 1) {
                if (i >= 3) break;
                print i;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // === TDD 사이클 8: Interpreter — for + continue (increment 실행 보장) ===

    @Test
    fun `for 루프에서 continue 시 increment가 실행된다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 10; i += 1) {
                if (i % 2 == 0) continue;
                print i;
            }
        """.trimIndent())
        assertEquals("1\n3\n5\n7\n9", result)
    }

    // === TDD 사이클 9: 중첩 루프 + break ===

    @Test
    fun `중첩 루프에서 break는 가장 가까운 루프만 탈출한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                for (var j = 0; j < 3; j += 1) {
                    if (j >= 1) break;
                    print i;
                }
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // === TDD 사이클 10: 중첩 루프 + continue ===

    @Test
    fun `중첩 루프에서 continue는 가장 가까운 루프만 건너뛴다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                for (var j = 0; j < 3; j += 1) {
                    if (j == 1) continue;
                    print j;
                }
            }
        """.trimIndent())
        assertEquals("0\n2\n0\n2\n0\n2", result)
    }

    // === TDD 사이클 11: for 루프 변수 스코프 ===

    @Test
    fun `for 루프 변수는 루프 밖에서 접근 불가`() {
        runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                print i;
            }
            print i;
        """.trimIndent())
        assertTrue(GwanLang.hadRuntimeError)
    }

    // === TDD 사이클 12: Resolver — 루프 밖 break 에러 ===

    @Test
    fun `루프 밖에서 break 사용 시 정적 에러`() {
        runAndCapture("break;")
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 13: Resolver — 루프 밖 continue 에러 ===

    @Test
    fun `루프 밖에서 continue 사용 시 정적 에러`() {
        runAndCapture("continue;")
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 14: Resolver — 함수 안에서 바깥 루프 break 에러 ===

    @Test
    fun `함수 안에서 바깥 루프의 break 사용 시 정적 에러`() {
        runAndCapture("""
            while (true) {
                fun f() {
                    break;
                }
                f();
            }
        """.trimIndent())
        assertTrue(GwanLang.hadError)
    }

    @Test
    fun `함수 안에서 바깥 루프의 continue 사용 시 정적 에러`() {
        runAndCapture("""
            while (true) {
                fun f() {
                    continue;
                }
                f();
            }
        """.trimIndent())
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 15: 기존 for 루프 회귀 ===

    @Test
    fun `기존 for 루프가 정상 동작한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 5; i += 1) {
                print i;
            }
        """.trimIndent())
        assertEquals("0\n1\n2\n3\n4", result)
    }

    @Test
    fun `조건 없는 for 루프는 무한 루프로 동작한다`() {
        val result = runAndCapture("""
            var count = 0;
            for (;;) {
                if (count >= 3) break;
                print count;
                count += 1;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }
}
