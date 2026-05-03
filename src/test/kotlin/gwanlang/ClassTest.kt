package gwanlang

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClassTest {

    @BeforeEach
    fun setUp() {
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }

    private fun parse(source: String): List<Stmt> {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        return Parser(tokens).parse()
    }

    // --- TDD 사이클 1: 빈 클래스 파싱 ---

    @Test
    fun `빈 클래스 선언을 파싱한다`() {
        val stmts = parse("class Foo {}")
        assertFalse(GwanLang.hadError)
        assertEquals(1, stmts.size)
        val cls = stmts[0] as Stmt.Class
        assertEquals("Foo", cls.name.lexeme)
        assertNull(cls.superclass)
        assertTrue(cls.methods.isEmpty())
    }

    // --- TDD 사이클 2: 메서드 있는 클래스 ---

    @Test
    fun `메서드가 있는 클래스를 파싱한다`() {
        val stmts = parse("class Foo { bar() { return 1; } baz(x) { return x; } }")
        assertFalse(GwanLang.hadError)
        val cls = stmts[0] as Stmt.Class
        assertEquals(2, cls.methods.size)
        assertEquals("bar", cls.methods[0].name.lexeme)
        assertEquals(0, cls.methods[0].params.size)
        assertEquals("baz", cls.methods[1].name.lexeme)
        assertEquals(1, cls.methods[1].params.size)
    }

    // --- TDD 사이클 3: 상속 ---

    @Test
    fun `상속 클래스를 파싱한다`() {
        val stmts = parse("class Bar < Foo {}")
        assertFalse(GwanLang.hadError)
        val cls = stmts[0] as Stmt.Class
        assertEquals("Bar", cls.name.lexeme)
        assertNotNull(cls.superclass)
        assertEquals("Foo", cls.superclass!!.name.lexeme)
    }

    // --- TDD 사이클 4: 프로퍼티 접근 ---

    @Test
    fun `프로퍼티 접근을 파싱한다`() {
        val stmts = parse("obj.field;")
        assertFalse(GwanLang.hadError)
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Get
        assertEquals("field", expr.name.lexeme)
        assertTrue(expr.obj is Expr.Variable)
    }

    @Test
    fun `체이닝된 프로퍼티 접근을 파싱한다`() {
        val stmts = parse("a.b.c;")
        assertFalse(GwanLang.hadError)
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Get
        assertEquals("c", expr.name.lexeme)
        val inner = expr.obj as Expr.Get
        assertEquals("b", inner.name.lexeme)
    }

    // --- TDD 사이클 5: 필드 대입 ---

    @Test
    fun `필드 대입을 파싱한다`() {
        val stmts = parse("obj.field = 1;")
        assertFalse(GwanLang.hadError)
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Set
        assertEquals("field", expr.name.lexeme)
        assertTrue(expr.obj is Expr.Variable)
        assertTrue(expr.value is Expr.Literal)
    }

    // --- TDD 사이클 6: this/super ---

    @Test
    fun `this 표현식을 파싱한다`() {
        val stmts = parse("this.x;")
        assertFalse(GwanLang.hadError)
        val get = (stmts[0] as Stmt.Expression).expression as Expr.Get
        val thisExpr = get.obj as Expr.This
        assertEquals("this", thisExpr.keyword.lexeme)
    }

    @Test
    fun `super 표현식을 파싱한다`() {
        val stmts = parse("super.method;")
        assertFalse(GwanLang.hadError)
        val expr = (stmts[0] as Stmt.Expression).expression as Expr.Super
        assertEquals("super", expr.keyword.lexeme)
        assertEquals("method", expr.method.lexeme)
    }
}
