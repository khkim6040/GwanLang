package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AstPrinterTest {

    private fun printAst(source: String): String {
        val tokens = Scanner(source).scanTokens()
        val expr = Parser(tokens).parse()!!
        return AstPrinter().print(expr)
    }

    @Test
    fun `숫자 리터럴을 출력한다`() {
        assertEquals("1.0", printAst("1"))
    }

    @Test
    fun `문자열 리터럴을 출력한다`() {
        assertEquals("hello", printAst("\"hello\""))
    }

    @Test
    fun `nil을 출력한다`() {
        assertEquals("nil", printAst("nil"))
    }

    @Test
    fun `boolean을 출력한다`() {
        assertEquals("true", printAst("true"))
    }

    @Test
    fun `단항 연산자를 출력한다`() {
        assertEquals("(- 1.0)", printAst("-1"))
    }

    @Test
    fun `이항 연산자를 출력한다`() {
        assertEquals("(+ 1.0 2.0)", printAst("1 + 2"))
    }

    @Test
    fun `그룹화를 출력한다`() {
        assertEquals("(group (+ 1.0 2.0))", printAst("(1 + 2)"))
    }

    @Test
    fun `복합 표현식을 출력한다`() {
        assertEquals("(+ 1.0 (* 2.0 3.0))", printAst("1 + 2 * 3"))
    }

    @Test
    fun `중첩 괄호 표현식을 출력한다`() {
        assertEquals("(* (group (+ 1.0 2.0)) 3.0)", printAst("(1 + 2) * 3"))
    }
}
