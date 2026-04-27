package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementParserTest {

    private fun parse(source: String): List<Stmt> {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        return Parser(tokens).parse()
    }

    // --- print 문 ---

    @Test
    fun `print 문을 파싱한다`() {
        val stmts = parse("print 42;")

        assertEquals(1, stmts.size)
        val print = assertIs<Stmt.Print>(stmts[0])
        val literal = assertIs<Expr.Literal>(print.expression)
        assertEquals(42.0, literal.value)
    }

    // --- 표현식 문 ---

    @Test
    fun `표현식 문을 파싱한다`() {
        val stmts = parse("1 + 2;")

        assertEquals(1, stmts.size)
        val exprStmt = assertIs<Stmt.Expression>(stmts[0])
        assertIs<Expr.Binary>(exprStmt.expression)
    }

    // --- var 선언 ---

    @Test
    fun `초기값이 있는 var 선언을 파싱한다`() {
        val stmts = parse("var x = 10;")

        assertEquals(1, stmts.size)
        val varStmt = assertIs<Stmt.Var>(stmts[0])
        assertEquals("x", varStmt.name.lexeme)
        val literal = assertIs<Expr.Literal>(varStmt.initializer)
        assertEquals(10.0, literal.value)
    }

    @Test
    fun `초기값 없는 var 선언을 파싱한다`() {
        val stmts = parse("var x;")

        assertEquals(1, stmts.size)
        val varStmt = assertIs<Stmt.Var>(stmts[0])
        assertEquals("x", varStmt.name.lexeme)
        assertNull(varStmt.initializer)
    }

    // --- 변수 참조 ---

    @Test
    fun `변수 참조를 파싱한다`() {
        val stmts = parse("print x;")

        val print = assertIs<Stmt.Print>(stmts[0])
        val variable = assertIs<Expr.Variable>(print.expression)
        assertEquals("x", variable.name.lexeme)
    }

    // --- 대입 ---

    @Test
    fun `대입 표현식을 파싱한다`() {
        val stmts = parse("x = 10;")

        val exprStmt = assertIs<Stmt.Expression>(stmts[0])
        val assign = assertIs<Expr.Assign>(exprStmt.expression)
        assertEquals("x", assign.name.lexeme)
        val literal = assertIs<Expr.Literal>(assign.value)
        assertEquals(10.0, literal.value)
    }

    // --- 블록 ---

    @Test
    fun `블록을 파싱한다`() {
        val stmts = parse("{ var x = 1; print x; }")

        assertEquals(1, stmts.size)
        val block = assertIs<Stmt.Block>(stmts[0])
        assertEquals(2, block.statements.size)
        assertIs<Stmt.Var>(block.statements[0])
        assertIs<Stmt.Print>(block.statements[1])
    }

    // --- if 문 ---

    @Test
    fun `if 문을 파싱한다`() {
        val stmts = parse("if (true) print 1;")

        assertEquals(1, stmts.size)
        val ifStmt = assertIs<Stmt.If>(stmts[0])
        assertIs<Expr.Literal>(ifStmt.condition)
        assertIs<Stmt.Print>(ifStmt.thenBranch)
        assertNull(ifStmt.elseBranch)
    }

    @Test
    fun `if-else 문을 파싱한다`() {
        val stmts = parse("if (true) print 1; else print 2;")

        val ifStmt = assertIs<Stmt.If>(stmts[0])
        assertIs<Stmt.Print>(ifStmt.thenBranch)
        assertIs<Stmt.Print>(ifStmt.elseBranch)
    }

    // --- while 문 ---

    @Test
    fun `while 문을 파싱한다`() {
        val stmts = parse("while (true) print 1;")

        assertEquals(1, stmts.size)
        val whileStmt = assertIs<Stmt.While>(stmts[0])
        assertIs<Expr.Literal>(whileStmt.condition)
        assertIs<Stmt.Print>(whileStmt.body)
    }

    // --- for 문 (while로 디슈가링) ---

    @Test
    fun `for 문을 while로 디슈가링한다`() {
        val stmts = parse("for (var i = 0; i < 10; i = i + 1) print i;")

        // 외부 블록: { var i = 0; while (...) { ... } }
        assertEquals(1, stmts.size)
        val block = assertIs<Stmt.Block>(stmts[0])
        assertEquals(2, block.statements.size)
        assertIs<Stmt.Var>(block.statements[0])
        val whileStmt = assertIs<Stmt.While>(block.statements[1])
        // while 본문은 블록: { print i; i = i + 1; }
        val body = assertIs<Stmt.Block>(whileStmt.body)
        assertEquals(2, body.statements.size)
    }

    @Test
    fun `for 문에서 초기화와 증분을 생략할 수 있다`() {
        val stmts = parse("for (;true;) print 1;")

        // 초기화 없음 → 블록 없이 바로 while
        val whileStmt = assertIs<Stmt.While>(stmts[0])
        assertIs<Expr.Literal>(whileStmt.condition)
    }

    // --- 논리 연산자 ---

    @Test
    fun `or 연산자를 파싱한다`() {
        val stmts = parse("print true or false;")

        val print = assertIs<Stmt.Print>(stmts[0])
        val logical = assertIs<Expr.Logical>(print.expression)
        assertEquals(TokenType.OR, logical.op.type)
    }

    @Test
    fun `and 연산자를 파싱한다`() {
        val stmts = parse("print true and false;")

        val print = assertIs<Stmt.Print>(stmts[0])
        val logical = assertIs<Expr.Logical>(print.expression)
        assertEquals(TokenType.AND, logical.op.type)
    }

    // --- 여러 문장 ---

    @Test
    fun `여러 문장을 순서대로 파싱한다`() {
        val stmts = parse("var x = 1; var y = 2; print x + y;")

        assertEquals(3, stmts.size)
        assertIs<Stmt.Var>(stmts[0])
        assertIs<Stmt.Var>(stmts[1])
        assertIs<Stmt.Print>(stmts[2])
    }

    // --- 에러 복구 ---

    @Test
    fun `파싱 에러 후 다음 문장을 계속 파싱한다`() {
        val stmts = parse("var = 1; print 42;")

        // 첫 문장은 에러로 건너뛰고, 두 번째 문장은 파싱 성공
        assertTrue(stmts.any { it is Stmt.Print })
    }
}
