package gwanlang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val tokens = Scanner("$source;").scanTokens()
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.Expression).expression
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

    // --- 사이클 6: 산술 이항 연산 ---

    @Test
    fun `덧셈을 평가한다`() {
        assertEquals(3.0, evaluate("1 + 2"))
    }

    @Test
    fun `뺄셈을 평가한다`() {
        assertEquals(2.0, evaluate("5 - 3"))
    }

    @Test
    fun `곱셈을 평가한다`() {
        assertEquals(6.0, evaluate("2 * 3"))
    }

    @Test
    fun `나눗셈을 평가한다`() {
        assertEquals(2.0, evaluate("6 / 3"))
    }

    @Test
    fun `복합 산술을 평가한다`() {
        assertEquals(7.0, evaluate("1 + 2 * 3"))
    }

    @Test
    fun `좌결합 뺄셈을 평가한다`() {
        assertEquals(5.0, evaluate("10 - 3 - 2"))
    }

    @Test
    fun `산술 연산에 문자열을 넣으면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("1 - \"a\"") }
    }

    @Test
    fun `산술 연산에 boolean을 넣으면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("true * 2") }
    }

    // --- 사이클 7: 0으로 나누기 ---

    @Test
    fun `0으로 나누면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("1 / 0") }
    }

    @Test
    fun `0점0으로 나누면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("1 / 0.0") }
    }

    // --- 사이클 8: 문자열 연결 ---

    @Test
    fun `문자열 두 개를 더하면 연결된다`() {
        assertEquals("ab", evaluate("\"a\" + \"b\""))
    }

    @Test
    fun `빈 문자열도 연결할 수 있다`() {
        assertEquals("hello", evaluate("\"\" + \"hello\""))
    }

    // --- 사이클 9: + 타입 에러 ---

    @Test
    fun `숫자와 문자열을 더하면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("1 + \"a\"") }
    }

    @Test
    fun `문자열과 숫자를 더하면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("\"a\" + 1") }
    }

    @Test
    fun `boolean과 숫자를 더하면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("true + 1") }
    }

    // --- 사이클 10: 비교 연산 ---

    @Test
    fun `작다 비교를 평가한다`() {
        assertEquals(true, evaluate("1 < 2"))
    }

    @Test
    fun `크거나 같다 비교를 평가한다`() {
        assertEquals(true, evaluate("3 >= 3"))
    }

    @Test
    fun `크다 비교를 평가한다`() {
        assertEquals(false, evaluate("1 > 2"))
    }

    @Test
    fun `작거나 같다 비교를 평가한다`() {
        assertEquals(true, evaluate("2 <= 2"))
    }

    @Test
    fun `비교 연산에 문자열을 넣으면 RuntimeError`() {
        assertThrows<RuntimeError> { evaluate("\"a\" < 1") }
    }

    // --- 사이클 11: 동등 비교 ---

    @Test
    fun `같은 숫자는 동등하다`() {
        assertEquals(true, evaluate("1 == 1"))
    }

    @Test
    fun `다른 숫자는 동등하지 않다`() {
        assertEquals(true, evaluate("1 != 2"))
    }

    @Test
    fun `nil은 nil과 동등하다`() {
        assertEquals(true, evaluate("nil == nil"))
    }

    @Test
    fun `nil은 숫자와 동등하지 않다`() {
        assertEquals(false, evaluate("nil == 0"))
    }

    @Test
    fun `타입이 다르면 동등하지 않다`() {
        assertEquals(false, evaluate("1 == \"1\""))
    }

    @Test
    fun `같은 문자열은 동등하다`() {
        assertEquals(true, evaluate("\"abc\" == \"abc\""))
    }

    // --- 사이클 12: Truthiness ---

    @Test
    fun `0은 truthy이다`() {
        assertEquals(false, evaluate("!0"))
    }

    @Test
    fun `빈 문자열은 truthy이다`() {
        assertEquals(false, evaluate("!\"\""))
    }

    @Test
    fun `nil은 falsy이다`() {
        assertEquals(true, evaluate("!nil"))
    }

    // --- 사이클 13: stringify ---

    private fun interpretAndCapture(source: String): String {
        val tokens = Scanner("print $source;").scanTokens()
        val stmts = Parser(tokens).parse()
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(output))
        try {
            Interpreter().interpret(stmts)
        } finally {
            System.setOut(originalOut)
        }
        return output.toString().trim()
    }

    @Test
    fun `정수 Double은 소수점 없이 출력된다`() {
        assertEquals("2", interpretAndCapture("1 + 1"))
    }

    @Test
    fun `소수 Double은 소수점과 함께 출력된다`() {
        assertEquals("3.14", interpretAndCapture("3.14"))
    }

    @Test
    fun `nil은 nil로 출력된다`() {
        assertEquals("nil", interpretAndCapture("nil"))
    }

    @Test
    fun `true는 true로 출력된다`() {
        assertEquals("true", interpretAndCapture("true"))
    }

    @Test
    fun `문자열은 그대로 출력된다`() {
        assertEquals("hello", interpretAndCapture("\"hello\""))
    }

    // --- Phase 4: 문장 실행 ---

    /** 스크립트를 실행하고 stdout 출력을 반환한다 */
    private fun runAndCapture(source: String): String {
        val tokens = Scanner(source).scanTokens()
        val stmts = Parser(tokens).parse()
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(output))
        try {
            Interpreter().interpret(stmts)
        } finally {
            System.setOut(originalOut)
        }
        return output.toString().trimEnd()
    }

    // --- 사이클 16: print 문 실행 ---

    @Test
    fun `print 문이 값을 출력한다`() {
        assertEquals("42", runAndCapture("print 42;"))
    }

    @Test
    fun `여러 print 문이 순서대로 출력된다`() {
        assertEquals("1\n2\n3", runAndCapture("print 1; print 2; print 3;"))
    }

    // --- 사이클 17: var 선언/참조 ---

    @Test
    fun `변수를 선언하고 참조할 수 있다`() {
        assertEquals("10", runAndCapture("var x = 10; print x;"))
    }

    @Test
    fun `변수에 표현식 결과를 저장할 수 있다`() {
        assertEquals("7", runAndCapture("var x = 3 + 4; print x;"))
    }

    // --- 사이클 18: 미초기화 변수 ---

    @Test
    fun `초기값 없는 변수는 nil이다`() {
        assertEquals("nil", runAndCapture("var x; print x;"))
    }

    // --- 사이클 19: 대입 ---

    @Test
    fun `변수에 값을 대입할 수 있다`() {
        assertEquals("2", runAndCapture("var x = 1; x = 2; print x;"))
    }

    @Test
    fun `미정의 변수에 대입하면 런타임 에러`() {
        val errOutput = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errOutput))
        val originalHadRuntimeError = GwanLang.hadRuntimeError
        try {
            runAndCapture("x = 1;")
            assertTrue(errOutput.toString().contains("Undefined variable"))
        } finally {
            System.setErr(originalErr)
            GwanLang.hadRuntimeError = originalHadRuntimeError
        }
    }

    // --- 사이클 20: 블록 스코프 ---

    @Test
    fun `블록 안에서 선언한 변수는 블록 밖에서 접근 불가`() {
        val errOutput = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errOutput))
        val originalHadRuntimeError = GwanLang.hadRuntimeError
        try {
            runAndCapture("{ var x = 10; } print x;")
            assertTrue(errOutput.toString().contains("Undefined variable"))
        } finally {
            System.setErr(originalErr)
            GwanLang.hadRuntimeError = originalHadRuntimeError
        }
    }

    @Test
    fun `블록 안에서 바깥 변수를 읽을 수 있다`() {
        assertEquals("10", runAndCapture("var x = 10; { print x; }"))
    }

    // --- 사이클 21: 스코프 섀도잉 ---

    @Test
    fun `내부 스코프에서 변수를 섀도잉할 수 있다`() {
        val result = runAndCapture("""
            var x = "outer";
            {
                var x = "inner";
                print x;
            }
            print x;
        """.trimIndent())
        assertEquals("inner\nouter", result)
    }

    // --- 사이클 22: if/else ---

    @Test
    fun `if 조건이 truthy면 then 분기를 실행한다`() {
        assertEquals("yes", runAndCapture("if (true) print \"yes\";"))
    }

    @Test
    fun `if 조건이 falsy면 else 분기를 실행한다`() {
        assertEquals("no", runAndCapture("if (false) print \"yes\"; else print \"no\";"))
    }

    @Test
    fun `if 조건이 falsy이고 else가 없으면 아무것도 실행하지 않는다`() {
        assertEquals("", runAndCapture("if (false) print \"yes\";"))
    }

    // --- 사이클 23: while ---

    @Test
    fun `while 루프가 조건이 falsy가 될 때까지 반복한다`() {
        val result = runAndCapture("""
            var i = 0;
            while (i < 3) {
                print i;
                i = i + 1;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // --- 사이클 24: for ---

    @Test
    fun `for 루프가 정상 동작한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 3; i = i + 1) {
                print i;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // --- 사이클 25: 논리 연산자 ---

    @Test
    fun `or는 좌측이 truthy면 좌측 값을 반환한다`() {
        assertEquals("hi", runAndCapture("print \"hi\" or 2;"))
    }

    @Test
    fun `or는 좌측이 falsy면 우측 값을 반환한다`() {
        assertEquals("yes", runAndCapture("print nil or \"yes\";"))
    }

    @Test
    fun `and는 좌측이 falsy면 좌측 값을 반환한다`() {
        assertEquals("false", runAndCapture("print false and \"yes\";"))
    }

    @Test
    fun `and는 좌측이 truthy면 우측 값을 반환한다`() {
        assertEquals("yes", runAndCapture("print true and \"yes\";"))
    }

    // --- 사이클 27: 통합 테스트 ---

    @Test
    fun `피보나치 수열을 출력한다`() {
        val result = runAndCapture("""
            var a = 0;
            var b = 1;
            for (var i = 0; i < 8; i = i + 1) {
                print a;
                var temp = a;
                a = b;
                b = temp + b;
            }
        """.trimIndent())
        assertEquals("0\n1\n1\n2\n3\n5\n8\n13", result)
    }

    @Test
    fun `중첩 if-else와 변수 대입을 조합한다`() {
        val result = runAndCapture("""
            var x = 10;
            var result = "none";
            if (x > 5) {
                if (x > 15) {
                    result = "big";
                } else {
                    result = "medium";
                }
            } else {
                result = "small";
            }
            print result;
        """.trimIndent())
        assertEquals("medium", result)
    }

    // --- Phase 3 통합 (기존) ---

    @Test
    fun `런타임 에러는 stderr에 줄 번호와 함께 출력된다`() {
        val errOutput = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errOutput))
        val originalHadRuntimeError = GwanLang.hadRuntimeError
        try {
            val tokens = Scanner("-\"text\";").scanTokens()
            val stmts = Parser(tokens).parse()
            Interpreter().interpret(stmts)
            val errorMsg = errOutput.toString().trim()
            assertTrue(errorMsg.contains("line 1"), "에러에 줄 번호가 포함되어야 한다: $errorMsg")
            assertTrue(errorMsg.contains("Operand must be a number"), "에러 메시지가 포함되어야 한다: $errorMsg")
            assertTrue(GwanLang.hadRuntimeError, "hadRuntimeError가 true여야 한다")
        } finally {
            System.setErr(originalErr)
            GwanLang.hadRuntimeError = originalHadRuntimeError
        }
    }
}
