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
}
