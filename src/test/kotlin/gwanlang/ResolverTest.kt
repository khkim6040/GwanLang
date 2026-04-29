package gwanlang

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolverTest {

    private lateinit var interpreter: Interpreter

    @BeforeEach
    fun setUp() {
        interpreter = Interpreter()
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }

    private fun parse(source: String): List<Stmt> {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        return parser.parse()
    }

    // --- 사이클 2: 변수 선언/사용 resolve ---

    @Test
    fun `블록 내 변수 선언과 사용을 resolve한다`() {
        val statements = parse("{ var a = 1; print a; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        // Resolver가 에러 없이 완료
        assertTrue(!GwanLang.hadError)
    }

    // --- 사이클 3: 블록 스코프 ---

    @Test
    fun `중첩 블록에서 외부 변수를 올바르게 resolve한다`() {
        val statements = parse("{ var a = 1; { print a; } }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    @Test
    fun `여러 변수의 스코프를 올바르게 resolve한다`() {
        val statements = parse("{ var a = 1; var b = 2; print a + b; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    // --- 사이클 4~5: 함수 분석 ---

    @Test
    fun `함수 선언과 본문을 resolve한다`() {
        val statements = parse("fun f() { var x = 1; print x; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    @Test
    fun `함수 매개변수를 resolve한다`() {
        val statements = parse("fun f(a, b) { print a + b; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    @Test
    fun `중첩 함수에서 외부 변수를 클로저로 resolve한다`() {
        val source = """
            fun outer() {
                var x = 1;
                fun inner() {
                    print x;
                }
            }
        """.trimIndent()
        val statements = parse(source)
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    @Test
    fun `재귀 함수를 resolve한다`() {
        val source = """
            fun fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
        """.trimIndent()
        val statements = parse(source)
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    // --- 사이클 6: 정적 에러 검출 ---

    @Test
    fun `같은 스코프에서 변수 중복 선언 시 에러를 보고한다`() {
        val statements = parse("{ var a = 1; var a = 2; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(GwanLang.hadError)
    }

    @Test
    fun `자기 참조 초기화 시 에러를 보고한다`() {
        val statements = parse("{ var a = a; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(GwanLang.hadError)
    }

    @Test
    fun `최상위 return 시 에러를 보고한다`() {
        val statements = parse("return 1;")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(GwanLang.hadError)
    }

    @Test
    fun `함수 내 return은 에러가 아니다`() {
        val statements = parse("fun f() { return 1; }")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }

    @Test
    fun `전역 변수 중복 선언은 에러가 아니다`() {
        val statements = parse("var a = 1; var a = 2;")
        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        assertTrue(!GwanLang.hadError)
    }
}
