package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StmtTest {

    @Test
    fun `Expression 문은 표현식을 보관한다`() {
        val expr = Expr.Literal(42.0)
        val stmt = Stmt.Expression(expr)

        assertEquals(expr, stmt.expression)
    }

    @Test
    fun `Print 문은 표현식을 보관한다`() {
        val expr = Expr.Literal("hello")
        val stmt = Stmt.Print(expr)

        assertEquals(expr, stmt.expression)
    }

    @Test
    fun `Var 문은 name과 initializer를 보관한다`() {
        val name = Token(TokenType.IDENTIFIER, "x", null, 1)
        val initializer = Expr.Literal(10.0)
        val stmt = Stmt.Var(name, initializer)

        assertEquals(name, stmt.name)
        assertEquals(initializer, stmt.initializer)
    }

    @Test
    fun `Var 문의 initializer는 null일 수 있다`() {
        val name = Token(TokenType.IDENTIFIER, "x", null, 1)
        val stmt = Stmt.Var(name, null)

        assertNull(stmt.initializer)
    }

    @Test
    fun `Block 문은 문장 리스트를 보관한다`() {
        val inner = Stmt.Print(Expr.Literal(1.0))
        val block = Stmt.Block(listOf(inner))

        assertEquals(1, block.statements.size)
        assertEquals(inner, block.statements[0])
    }

    @Test
    fun `If 문은 condition, thenBranch, elseBranch를 보관한다`() {
        val condition = Expr.Literal(true)
        val thenBranch = Stmt.Print(Expr.Literal("yes"))
        val elseBranch = Stmt.Print(Expr.Literal("no"))
        val ifStmt = Stmt.If(condition, thenBranch, elseBranch)

        assertEquals(condition, ifStmt.condition)
        assertEquals(thenBranch, ifStmt.thenBranch)
        assertEquals(elseBranch, ifStmt.elseBranch)
    }

    @Test
    fun `If 문의 elseBranch는 null일 수 있다`() {
        val condition = Expr.Literal(true)
        val thenBranch = Stmt.Print(Expr.Literal("yes"))
        val ifStmt = Stmt.If(condition, thenBranch, null)

        assertNull(ifStmt.elseBranch)
    }

    @Test
    fun `While 문은 condition과 body를 보관한다`() {
        val condition = Expr.Literal(true)
        val body = Stmt.Print(Expr.Literal("loop"))
        val whileStmt = Stmt.While(condition, body)

        assertEquals(condition, whileStmt.condition)
        assertEquals(body, whileStmt.body)
    }

    @Test
    fun `Function 문은 name, params, body를 보관한다`() {
        val name = Token(TokenType.IDENTIFIER, "greet", null, 1)
        val params = listOf(
            Token(TokenType.IDENTIFIER, "a", null, 1),
            Token(TokenType.IDENTIFIER, "b", null, 1)
        )
        val body = listOf(Stmt.Print(Expr.Literal("hello")))
        val fn = Stmt.Function(name, params, body)

        assertEquals(name, fn.name)
        assertEquals(params, fn.params)
        assertEquals(body, fn.body)
    }

    @Test
    fun `Function 문의 params는 비어 있을 수 있다`() {
        val name = Token(TokenType.IDENTIFIER, "noop", null, 1)
        val fn = Stmt.Function(name, emptyList(), emptyList())

        assertEquals(0, fn.params.size)
    }

    @Test
    fun `Return 문은 keyword와 value를 보관한다`() {
        val keyword = Token(TokenType.RETURN, "return", null, 1)
        val value = Expr.Literal(42.0)
        val ret = Stmt.Return(keyword, value)

        assertEquals(keyword, ret.keyword)
        assertEquals(value, ret.value)
    }

    @Test
    fun `Return 문의 value는 null일 수 있다`() {
        val keyword = Token(TokenType.RETURN, "return", null, 1)
        val ret = Stmt.Return(keyword, null)

        assertNull(ret.value)
    }
}
