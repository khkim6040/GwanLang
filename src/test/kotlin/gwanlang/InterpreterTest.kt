package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpreterTest {

    // --- 사이클 1: RuntimeError ---

    @Test
    fun `RuntimeError는 토큰과 메시지를 보관한다`() {
        val token = Token(TokenType.MINUS, "-", null, 1)
        val error = RuntimeError(token, "Operand must be a number.")
        assertEquals(token, error.token)
        assertEquals("Operand must be a number.", error.message)
    }

    private fun evaluate(source: String): Any? {
        val tokens = Scanner(source).scanTokens()
        val expr = Parser(tokens).parse()!!
        return Interpreter().testEvaluate(expr)
    }

    // --- 사이클 2: 리터럴 평가 ---

    @Test
    fun `숫자 리터럴을 평가한다`() {
        assertEquals(42.0, evaluate("42"))
    }

    @Test
    fun `문자열 리터럴을 평가한다`() {
        assertEquals("hello", evaluate("\"hello\""))
    }

    @Test
    fun `true를 평가한다`() {
        assertEquals(true, evaluate("true"))
    }

    @Test
    fun `false를 평가한다`() {
        assertEquals(false, evaluate("false"))
    }

    @Test
    fun `nil을 평가한다`() {
        assertNull(evaluate("nil"))
    }

    // --- 사이클 3: 괄호 평가 ---

    @Test
    fun `괄호 표현식을 평가한다`() {
        assertEquals(42.0, evaluate("(42)"))
    }
}
