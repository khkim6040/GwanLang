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
}
