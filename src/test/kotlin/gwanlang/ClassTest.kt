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

    // --- TDD 사이클 9~14: 클래스 인스턴스, 프로퍼티, 메서드, this, init ---

    private fun interpret(source: String): String {
        val out = java.io.ByteArrayOutputStream()
        val prevOut = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            val scanner = Scanner(source)
            val tokens = scanner.scanTokens()
            val parser = Parser(tokens).parse()
            val interpreter = Interpreter()
            val resolver = Resolver(interpreter)
            resolver.resolve(parser)
            if (!GwanLang.hadError) {
                interpreter.interpret(parser)
            }
        } finally {
            System.setOut(prevOut)
        }
        return out.toString().trim()
    }

    @Test
    fun `클래스 선언 및 인스턴스를 생성한다`() {
        val output = interpret("class Foo {} var f = Foo(); print f;")
        assertFalse(GwanLang.hadError)
        assertEquals("Foo instance", output)
    }

    @Test
    fun `클래스 자체를 출력하면 이름이 나온다`() {
        val output = interpret("class Foo {} print Foo;")
        assertFalse(GwanLang.hadError)
        assertEquals("Foo", output)
    }

    @Test
    fun `인스턴스에 필드를 저장하고 조회한다`() {
        val output = interpret("class Foo {} var f = Foo(); f.x = 42; print f.x;")
        assertFalse(GwanLang.hadError)
        assertEquals("42", output)
    }

    @Test
    fun `존재하지 않는 프로퍼티 접근 시 런타임 에러`() {
        interpret("class Foo {} var f = Foo(); print f.x;")
        assertTrue(GwanLang.hadRuntimeError)
    }

    @Test
    fun `인스턴스가 아닌 것에 프로퍼티 접근 시 런타임 에러`() {
        interpret("""var x = "str"; print x.field;""")
        assertTrue(GwanLang.hadRuntimeError)
    }

    @Test
    fun `메서드를 호출한다`() {
        val output = interpret("""
            class Foo {
              bar() { return 1; }
            }
            print Foo().bar();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("1", output)
    }

    @Test
    fun `this로 인스턴스 필드에 접근한다`() {
        val output = interpret("""
            class Foo {
              get() { return this.x; }
            }
            var f = Foo();
            f.x = 42;
            print f.get();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("42", output)
    }

    @Test
    fun `init 생성자로 인스턴스를 초기화한다`() {
        val output = interpret("""
            class Foo {
              init(x) { this.x = x; }
            }
            print Foo(5).x;
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("5", output)
    }

    @Test
    fun `init은 항상 인스턴스를 반환한다`() {
        val output = interpret("""
            class Foo {
              init() { this.x = 1; return; }
            }
            print Foo().x;
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("1", output)
    }

    // --- TDD 사이클 17~19: 상속, 오버라이드, super ---

    @Test
    fun `부모 클래스의 메서드를 상속한다`() {
        val output = interpret("""
            class A { greet() { return "hello"; } }
            class B < A {}
            print B().greet();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("hello", output)
    }

    @Test
    fun `자식 클래스가 부모 메서드를 오버라이드한다`() {
        val output = interpret("""
            class A { m() { return 1; } }
            class B < A { m() { return 2; } }
            print B().m();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("2", output)
    }

    @Test
    fun `super로 부모 메서드를 호출한다`() {
        val output = interpret("""
            class A { m() { return "A"; } }
            class B < A { m() { return super.m() + "B"; } }
            print B().m();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("AB", output)
    }

    @Test
    fun `다단계 상속에서 메서드를 탐색한다`() {
        val output = interpret("""
            class A { m() { return "A"; } }
            class B < A {}
            class C < B {}
            print C().m();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("A", output)
    }

    @Test
    fun `클래스가 아닌 것을 상속하면 런타임 에러`() {
        interpret("""var x = "not"; class B < x {}""")
        assertTrue(GwanLang.hadRuntimeError)
    }

    @Test
    fun `메서드를 변수에 저장하고 나중에 호출한다`() {
        val output = interpret("""
            class Foo {
              init(x) { this.x = x; }
              getX() { return this.x; }
            }
            var f = Foo(10);
            var method = f.getX;
            print method();
        """.trimIndent())
        assertFalse(GwanLang.hadError)
        assertEquals("10", output)
    }
}
