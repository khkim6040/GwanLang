package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunctionParserTest {

    // --- 함수 선언 파싱 ---

    @Test
    fun `매개변수 없는 함수 선언을 파싱한다`() {
        val stmts = parse("fun greet() { print 1; }")
        assertEquals(1, stmts.size)
        val fn = stmts[0] as Stmt.Function
        assertEquals("greet", fn.name.lexeme)
        assertEquals(0, fn.params.size)
        assertEquals(1, fn.body.size)
    }

    @Test
    fun `매개변수가 있는 함수 선언을 파싱한다`() {
        val stmts = parse("fun add(a, b) { print a + b; }")
        val fn = stmts[0] as Stmt.Function
        assertEquals("add", fn.name.lexeme)
        assertEquals(2, fn.params.size)
        assertEquals("a", fn.params[0].lexeme)
        assertEquals("b", fn.params[1].lexeme)
    }

    @Test
    fun `빈 본문 함수를 파싱한다`() {
        val stmts = parse("fun noop() {}")
        val fn = stmts[0] as Stmt.Function
        assertEquals("noop", fn.name.lexeme)
        assertEquals(0, fn.body.size)
    }

    // --- call 표현식 파싱 ---

    @Test
    fun `인자 없는 함수 호출을 파싱한다`() {
        val stmts = parse("f();")
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Call
        assertEquals("f", (expr.callee as Expr.Variable).name.lexeme)
        assertEquals(0, expr.arguments.size)
    }

    @Test
    fun `인자가 있는 함수 호출을 파싱한다`() {
        val stmts = parse("add(1, 2);")
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Call
        assertEquals("add", (expr.callee as Expr.Variable).name.lexeme)
        assertEquals(2, expr.arguments.size)
    }

    @Test
    fun `연쇄 호출을 파싱한다`() {
        val stmts = parse("getFunc()();")
        val outer = (stmts[0] as Stmt.Expression).expression as Expr.Call
        assertTrue(outer.callee is Expr.Call)
        val inner = outer.callee as Expr.Call
        assertEquals("getFunc", (inner.callee as Expr.Variable).name.lexeme)
    }

    // --- return 문 파싱 ---

    @Test
    fun `값이 있는 return 문을 파싱한다`() {
        val stmts = parse("fun f() { return 42; }")
        val fn = stmts[0] as Stmt.Function
        val ret = fn.body[0] as Stmt.Return
        assertEquals("return", ret.keyword.lexeme)
        assertEquals(42.0, (ret.value as Expr.Literal).value)
    }

    @Test
    fun `값이 없는 return 문을 파싱한다`() {
        val stmts = parse("fun f() { return; }")
        val fn = stmts[0] as Stmt.Function
        val ret = fn.body[0] as Stmt.Return
        assertNull(ret.value)
    }

    // --- 에러 케이스 ---

    @Test
    fun `인자가 255개를 초과하면 에러를 보고한다`() {
        val args = (1..256).joinToString(", ")
        val source = "f($args);"
        GwanLang.hadError = false
        parse(source)
        assertTrue(GwanLang.hadError)
        GwanLang.hadError = false
    }

    @Test
    fun `매개변수가 255개를 초과하면 에러를 보고한다`() {
        val params = (1..256).joinToString(", ") { "p$it" }
        val source = "fun f($params) {}"
        GwanLang.hadError = false
        parse(source)
        assertTrue(GwanLang.hadError)
        GwanLang.hadError = false
    }

    private fun parse(source: String): List<Stmt> {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        return Parser(tokens).parse()
    }
}
