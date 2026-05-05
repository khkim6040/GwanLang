package gwanlang

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LoopControlTest {

    @BeforeEach
    fun resetErrorState() {
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }

    private fun scan(source: String): List<Token> = Scanner(source).scanTokens()

    private fun runAndCapture(source: String): String {
        val tokens = Scanner(source).scanTokens()
        val stmts = Parser(tokens).parse()
        val interp = Interpreter()
        val resolver = Resolver(interp)
        resolver.resolve(stmts)
        if (GwanLang.hadError) return ""
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(output))
        try {
            interp.interpret(stmts)
        } finally {
            System.setOut(originalOut)
        }
        return output.toString().trimEnd()
    }

    // === TDD 사이클 1: Scanner — break/continue 키워드 ===

    @Test
    fun `break 키워드를 스캔한다`() {
        val tokens = scan("break;")
        assertEquals(
            listOf(TokenType.BREAK, TokenType.SEMICOLON, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("break", tokens[0].lexeme)
    }

    @Test
    fun `continue 키워드를 스캔한다`() {
        val tokens = scan("continue;")
        assertEquals(
            listOf(TokenType.CONTINUE, TokenType.SEMICOLON, TokenType.EOF),
            tokens.map { it.type },
        )
        assertEquals("continue", tokens[0].lexeme)
    }
}
