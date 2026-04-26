package gwanlang

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * GwanLang 인터프리터의 전역 에러 채널.
 *
 * Phase 4 수준: Scanner → Parser → Interpreter 파이프라인.
 */
object GwanLang {
    var hadError: Boolean = false
    var hadRuntimeError: Boolean = false

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '${token.lexeme}'", message)
        }
    }

    fun runtimeError(error: RuntimeError) {
        System.err.println("[line ${error.token.line}] RuntimeError: ${error.message}")
        hadRuntimeError = true
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hadError = true
    }
}

/**
 * 진입점 — 인자가 있으면 파일 실행, 없으면 REPL.
 */
private val interpreter = Interpreter()

fun main(args: Array<String>) {
    when {
        args.size > 1 -> {
            println("Usage: gwanlang [script]")
            exitProcess(64)
        }
        args.size == 1 -> runFile(args[0])
        else -> runPrompt()
    }
}

private fun runFile(path: String) {
    val source = Files.readString(Paths.get(path), StandardCharsets.UTF_8)
    run(source)
    if (GwanLang.hadError) exitProcess(65)
    if (GwanLang.hadRuntimeError) exitProcess(70)
}

private fun runPrompt() {
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line, repl = true)
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }
}

private fun run(source: String, repl: Boolean = false) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val statements = parser.parse()
    if (GwanLang.hadError) return

    if (repl && statements.size == 1 && statements[0] is Stmt.Expression) {
        val value = interpreter.interpretExpr((statements[0] as Stmt.Expression).expression)
        if (value != null) println(interpreter.stringify(value))
        return
    }

    interpreter.interpret(statements)
}
