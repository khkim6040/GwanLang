package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GwanFunctionTest {

    @Test
    fun `GwanFunction은 GwanCallable을 구현한다`() {
        val fn = makeFunction("greet", listOf("a", "b"))
        assertTrue(fn is GwanCallable)
    }

    @Test
    fun `arity는 매개변수 개수를 반환한다`() {
        assertEquals(0, makeFunction("f", emptyList()).arity())
        assertEquals(1, makeFunction("f", listOf("a")).arity())
        assertEquals(3, makeFunction("f", listOf("a", "b", "c")).arity())
    }

    @Test
    fun `toString은 함수 이름을 포함한다`() {
        val fn = makeFunction("greet", emptyList())
        assertEquals("<fn greet>", fn.toString())
    }

    @Test
    fun `call은 본문을 실행하고 return 없으면 nil을 반환한다`() {
        // fun f() { var x = 1; }
        val body = listOf(
            Stmt.Var(
                Token(TokenType.IDENTIFIER, "x", null, 1),
                Expr.Literal(1.0)
            )
        )
        val fn = makeFunction("f", emptyList(), body)
        val interpreter = Interpreter()
        val result = fn.call(interpreter, emptyList())
        assertNull(result)
    }

    @Test
    fun `call은 Return 예외로 반환된 값을 돌려준다`() {
        // fun f() { return 42; }
        val body = listOf(
            Stmt.Return(
                Token(TokenType.RETURN, "return", null, 1),
                Expr.Literal(42.0)
            )
        )
        val fn = makeFunction("f", emptyList(), body)
        val interpreter = Interpreter()
        val result = fn.call(interpreter, emptyList())
        assertEquals(42.0, result)
    }

    @Test
    fun `call은 매개변수를 환경에 바인딩한다`() {
        // fun f(a) { return a; }
        val body = listOf(
            Stmt.Return(
                Token(TokenType.RETURN, "return", null, 1),
                Expr.Variable(Token(TokenType.IDENTIFIER, "a", null, 1))
            )
        )
        val fn = makeFunction("f", listOf("a"), body)
        val interpreter = Interpreter()
        val result = fn.call(interpreter, listOf(99.0))
        assertEquals(99.0, result)
    }

    @Test
    fun `Return 예외는 value를 보관한다`() {
        val ret = Return(42.0)
        assertEquals(42.0, ret.value)
    }

    @Test
    fun `Return 예외의 value는 null일 수 있다`() {
        val ret = Return(null)
        assertNull(ret.value)
    }

    private fun makeFunction(
        name: String,
        params: List<String>,
        body: List<Stmt> = emptyList()
    ): GwanFunction {
        val nameToken = Token(TokenType.IDENTIFIER, name, null, 1)
        val paramTokens = params.map { Token(TokenType.IDENTIFIER, it, null, 1) }
        val declaration = Stmt.Function(nameToken, paramTokens, body)
        return GwanFunction(declaration, Environment())
    }
}
