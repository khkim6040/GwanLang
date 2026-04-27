package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExprTest {

    @Test
    fun `Binary 노드는 left, op, right를 보관한다`() {
        val left = Expr.Literal(1.0)
        val op = Token(TokenType.PLUS, "+", null, 1)
        val right = Expr.Literal(2.0)
        val binary = Expr.Binary(left, op, right)

        assertEquals(left, binary.left)
        assertEquals(op, binary.op)
        assertEquals(right, binary.right)
    }

    @Test
    fun `Grouping 노드는 내부 표현식을 보관한다`() {
        val inner = Expr.Literal(42.0)
        val grouping = Expr.Grouping(inner)

        assertEquals(inner, grouping.expression)
    }

    @Test
    fun `Literal 노드는 다양한 타입의 값을 보관한다`() {
        assertEquals(1.0, Expr.Literal(1.0).value)
        assertEquals("hello", Expr.Literal("hello").value)
        assertEquals(true, Expr.Literal(true).value)
        assertNull(Expr.Literal(null).value)
    }

    @Test
    fun `Unary 노드는 op와 right를 보관한다`() {
        val op = Token(TokenType.MINUS, "-", null, 1)
        val right = Expr.Literal(5.0)
        val unary = Expr.Unary(op, right)

        assertEquals(op, unary.op)
        assertEquals(right, unary.right)
    }

    @Test
    fun `Variable 노드는 name 토큰을 보관한다`() {
        val name = Token(TokenType.IDENTIFIER, "x", null, 1)
        val variable = Expr.Variable(name)

        assertEquals(name, variable.name)
    }

    @Test
    fun `Assign 노드는 name과 value를 보관한다`() {
        val name = Token(TokenType.IDENTIFIER, "x", null, 1)
        val value = Expr.Literal(42.0)
        val assign = Expr.Assign(name, value)

        assertEquals(name, assign.name)
        assertEquals(value, assign.value)
    }

    @Test
    fun `Logical 노드는 left, op, right를 보관한다`() {
        val left = Expr.Literal(true)
        val op = Token(TokenType.OR, "or", null, 1)
        val right = Expr.Literal(false)
        val logical = Expr.Logical(left, op, right)

        assertEquals(left, logical.left)
        assertEquals(op, logical.op)
        assertEquals(right, logical.right)
    }

    @Test
    fun `Call 노드는 callee, paren, arguments를 보관한다`() {
        val callee = Expr.Variable(Token(TokenType.IDENTIFIER, "f", null, 1))
        val paren = Token(TokenType.RIGHT_PAREN, ")", null, 1)
        val args = listOf(Expr.Literal(1.0), Expr.Literal(2.0))
        val call = Expr.Call(callee, paren, args)

        assertEquals(callee, call.callee)
        assertEquals(paren, call.paren)
        assertEquals(2, call.arguments.size)
        assertEquals(args, call.arguments)
    }

    @Test
    fun `Call 노드는 인자가 없을 수 있다`() {
        val callee = Expr.Variable(Token(TokenType.IDENTIFIER, "f", null, 1))
        val paren = Token(TokenType.RIGHT_PAREN, ")", null, 1)
        val call = Expr.Call(callee, paren, emptyList())

        assertEquals(0, call.arguments.size)
    }
}
