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
 * Phase 1 수준: Scanner 결과 출력까지.
 * 파서/인터프리터는 이후 Phase에서 추가된다.
 */
object GwanLang {
    var hadError: Boolean = false

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hadError = true
    }
}

/**
 * 진입점 — 인자가 있으면 파일 실행, 없으면 REPL.
 */
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
}

private fun runPrompt() {
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        GwanLang.hadError = false
    }
}

private fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    for (token in tokens) {
        println(token)
    }
}
