package gwanlang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    // --- 사이클 4: 단항 연산 ---

    @Test
    fun `단항 마이너스는 숫자 부호를 반전한다`() {
        assertEquals(-3.0, evaluate("-3"))
    }

    @Test
    fun `NOT true는 false이다`() {
        assertEquals(false, evaluate("!true"))
    }

    @Test
    fun `NOT false는 true이다`() {
        assertEquals(true, evaluate("!false"))
    }

    @Test
    fun `NOT nil은 true이다`() {
        assertEquals(true, evaluate("!nil"))
    }

    @Test
    fun `이중 부정은 원래 truthiness를 반환한다`() {
        assertEquals(false, evaluate("!!false"))
    }

    // --- 사이클 5: 단항 타입 에러 ---

    @Test
    fun `단항 마이너스에 문자열을 넣으면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("-\"text\"") }
    }
}
