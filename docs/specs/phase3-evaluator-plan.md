# Phase 3: Evaluator 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parser가 생성한 표현식 AST(`Expr`)를 평가하여 런타임 값을 반환하는 Tree-walking Interpreter를 구현한다.

**Architecture:** `Interpreter` 클래스가 `Expr` sealed class를 `when`으로 패턴 매칭하여 재귀적으로 평가한다. 런타임 타입 에러는 `RuntimeError` 예외로 리포팅하며, `GwanLang` 객체가 최상위에서 catch하여 stderr에 출력한다.

**Tech Stack:** Kotlin, JUnit 5, Gradle (Kotlin DSL)

---

## 파일 구조

### 신규 생성
| 파일 | 역할 |
|------|------|
| `src/main/kotlin/gwanlang/RuntimeError.kt` | 런타임 에러 예외 클래스 |
| `src/main/kotlin/gwanlang/Interpreter.kt` | Tree-walking 표현식 평가기 |
| `src/test/kotlin/gwanlang/InterpreterTest.kt` | Interpreter 테스트 |
| `examples/evaluator-demo.gwan` | 표현식 평가 시연 예제 |

### 수정
| 파일 | 변경 내용 |
|------|----------|
| `src/main/kotlin/gwanlang/GwanLang.kt:16-17` | `hadRuntimeError`, `runtimeError()` 추가 |
| `src/main/kotlin/gwanlang/GwanLang.kt:51-54` | `runFile()`에 exit code 70 추가 |
| `src/main/kotlin/gwanlang/GwanLang.kt:57-65` | `runPrompt()`에 `hadRuntimeError` 리셋 추가 |
| `src/main/kotlin/gwanlang/GwanLang.kt:67-75` | `run()`에서 AstPrinter → Interpreter 교체 |
| `docs/CHANGELOG.md` | Phase 3 완료 기록 |
| `GWANLANG_SPEC.md` | §Phase 3 체크박스 업데이트 |

---

## Task 1: RuntimeError 예외 클래스

**Files:**
- Create: `src/main/kotlin/gwanlang/RuntimeError.kt`
- Create: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 파일 생성 — RuntimeError 테스트 작성**

```kotlin
package gwanlang

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InterpreterTest {

    // --- 사이클 1: RuntimeError ---

    @Test
    fun `RuntimeError는 토큰과 메시지를 보관한다`() {
        val token = Token(TokenType.MINUS, "-", null, 1)
        val error = RuntimeError(token, "Operand must be a number.")
        assertEquals(token, error.token)
        assertEquals("Operand must be a number.", error.message)
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: FAIL — `RuntimeError` 클래스가 존재하지 않음

- [ ] **Step 3: RuntimeError 구현**

```kotlin
package gwanlang

class RuntimeError(val token: Token, message: String) : RuntimeException(message)
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/gwanlang/RuntimeError.kt src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "feat: RuntimeError 예외 클래스 정의"
```

---

## Task 2: Interpreter 골격 및 리터럴/괄호 평가

**Files:**
- Create: `src/main/kotlin/gwanlang/Interpreter.kt`
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성 — 리터럴/괄호 평가**

`InterpreterTest.kt`에 헬퍼와 테스트 추가:

```kotlin
    private fun evaluate(source: String): Any? {
        val tokens = Scanner(source).scanTokens()
        val expr = Parser(tokens).parse()!!
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
```

import 문에 `assertNull` 추가:
```kotlin
import kotlin.test.assertNull
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: FAIL — `Interpreter` 클래스가 존재하지 않음

- [ ] **Step 3: Interpreter 골격 구현**

```kotlin
package gwanlang

class Interpreter {

    fun interpret(expression: Expr) {
        try {
            val value = evaluate(expression)
            println(stringify(value))
        } catch (error: RuntimeError) {
            GwanLang.runtimeError(error)
        }
    }

    /** 테스트용 — evaluate를 외부에서 직접 호출 */
    fun testEvaluate(expr: Expr): Any? = evaluate(expr)

    private fun evaluate(expr: Expr): Any? = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Grouping -> evaluate(expr.expression)
        is Expr.Unary -> TODO()
        is Expr.Binary -> TODO()
    }

    private fun stringify(value: Any?): String {
        if (value == null) return "nil"
        if (value is Double) {
            val text = value.toString()
            if (text.endsWith(".0")) return text.dropLast(2)
            return text
        }
        return value.toString()
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS (리터럴/괄호 테스트 전체 통과)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/gwanlang/Interpreter.kt src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "feat: Interpreter 골격 및 리터럴/괄호 평가"
```

---

## Task 3: 단항 연산자 평가 (-, !)

**Files:**
- Modify: `src/main/kotlin/gwanlang/Interpreter.kt`
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성 — 단항 연산**

`InterpreterTest.kt`에 추가:

```kotlin
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
```

import 문에 추가:
```kotlin
import org.junit.jupiter.api.assertThrows
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: FAIL — Unary 분기가 `TODO()`

- [ ] **Step 3: 단항 평가 구현**

`Interpreter.kt`의 `evaluate()` 메서드에서 Unary 분기를 교체:

```kotlin
        is Expr.Unary -> {
            val right = evaluate(expr.right)
            when (expr.op.type) {
                TokenType.MINUS -> {
                    checkNumberOperand(expr.op, right)
                    -(right as Double)
                }
                TokenType.BANG -> !isTruthy(right)
                else -> null
            }
        }
```

`Interpreter` 클래스에 유틸리티 메서드 추가:

```kotlin
    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun checkNumberOperand(op: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(op, "Operand must be a number.")
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/gwanlang/Interpreter.kt src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "feat: 단항 연산자 평가 (-,!)"
```

---

## Task 4: 산술 이항 연산자 평가 (+, -, *, /)

**Files:**
- Modify: `src/main/kotlin/gwanlang/Interpreter.kt`
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성 — 산술 연산**

`InterpreterTest.kt`에 추가:

```kotlin
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
        // 1 + 2 * 3 = 7 (우선순위 반영)
        assertEquals(7.0, evaluate("1 + 2 * 3"))
    }

    @Test
    fun `좌결합 뺄셈을 평가한다`() {
        // 10 - 3 - 2 = 5
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
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: FAIL — Binary 분기가 `TODO()`

- [ ] **Step 3: 산술 이항 연산 구현**

`Interpreter.kt`의 `evaluate()` 메서드에서 Binary 분기를 교체:

```kotlin
        is Expr.Binary -> {
            val left = evaluate(expr.left)
            val right = evaluate(expr.right)
            when (expr.op.type) {
                TokenType.MINUS -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) - (right as Double)
                }
                TokenType.STAR -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) * (right as Double)
                }
                TokenType.SLASH -> {
                    checkNumberOperands(expr.op, left, right)
                    if (right as Double == 0.0) {
                        throw RuntimeError(expr.op, "Division by zero.")
                    }
                    (left as Double) / right
                }
                TokenType.PLUS -> {
                    if (left is Double && right is Double) left + right
                    else if (left is String && right is String) left + right
                    else throw RuntimeError(expr.op, "Operands must be two numbers or two strings.")
                }
                TokenType.GREATER -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) > (right as Double)
                }
                TokenType.GREATER_EQUAL -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) >= (right as Double)
                }
                TokenType.LESS -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) < (right as Double)
                }
                TokenType.LESS_EQUAL -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) <= (right as Double)
                }
                TokenType.EQUAL_EQUAL -> isEqual(left, right)
                TokenType.BANG_EQUAL -> !isEqual(left, right)
                else -> null
            }
        }
```

유틸리티 메서드 추가:

```kotlin
    private fun checkNumberOperands(op: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(op, "Operands must be numbers.")
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false
        return a == b
    }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/gwanlang/Interpreter.kt src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "feat: 산술 이항 연산자 평가 (+,-,*,/)"
```

---

## Task 5: 0으로 나누기, 문자열 연결, + 타입 검사

**Files:**
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성**

`InterpreterTest.kt`에 추가:

```kotlin
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
```

- [ ] **Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS (Task 4에서 이미 구현됨 — 이 Task는 추가 엣지 케이스 검증)

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "test: 0으로 나누기, 문자열 연결, + 타입 에러 엣지 케이스"
```

---

## Task 6: 비교/동등 연산자 및 truthiness 테스트

**Files:**
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성**

`InterpreterTest.kt`에 추가:

```kotlin
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
```

- [ ] **Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS (Task 4에서 이미 구현됨)

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "test: 비교/동등 연산자 및 truthiness 엣지 케이스"
```

---

## Task 7: stringify 테스트

**Files:**
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`

- [ ] **Step 1: 테스트 작성**

`InterpreterTest.kt`에 추가. stringify는 `interpret()`를 통해 stdout으로 출력되므로, stdout을 캡처하여 테스트한다:

```kotlin
    // --- 사이클 13: stringify ---

    private fun interpretAndCapture(source: String): String {
        val tokens = Scanner(source).scanTokens()
        val expr = Parser(tokens).parse()!!
        val output = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(output))
        try {
            Interpreter().interpret(expr)
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
```

- [ ] **Step 2: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: PASS

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/gwanlang/InterpreterTest.kt
git commit -m "test: stringify 출력 검증"
```

---

## Task 8: GwanLang 파이프라인에 Interpreter 연결

**Files:**
- Modify: `src/main/kotlin/gwanlang/GwanLang.kt`
- Modify: `src/test/kotlin/gwanlang/InterpreterTest.kt`
- Create: `examples/evaluator-demo.gwan`

- [ ] **Step 1: 통합 테스트 작성**

`InterpreterTest.kt`에 추가:

```kotlin
    // --- 사이클 14: GwanLang 통합 ---

    @Test
    fun `런타임 에러는 stderr에 줄 번호와 함께 출력된다`() {
        val errOutput = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errOutput))
        val originalHadRuntimeError = GwanLang.hadRuntimeError
        try {
            val tokens = Scanner("-\"text\"").scanTokens()
            val expr = Parser(tokens).parse()!!
            Interpreter().interpret(expr)
            val errorMsg = errOutput.toString().trim()
            assert(errorMsg.contains("line 1")) { "에러에 줄 번호가 포함되어야 한다: $errorMsg" }
            assert(errorMsg.contains("Operand must be a number")) { "에러 메시지가 포함되어야 한다: $errorMsg" }
            assert(GwanLang.hadRuntimeError) { "hadRuntimeError가 true여야 한다" }
        } finally {
            System.setErr(originalErr)
            GwanLang.hadRuntimeError = originalHadRuntimeError
        }
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "gwanlang.InterpreterTest"`
Expected: FAIL — `GwanLang.hadRuntimeError`와 `GwanLang.runtimeError()`가 없음

- [ ] **Step 3: GwanLang.kt 수정**

`GwanLang.kt`를 다음과 같이 수정:

```kotlin
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
```

`run()` 함수 변경:

```kotlin
private fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val expression = parser.parse()
    if (GwanLang.hadError) return
    Interpreter().interpret(expression!!)
}
```

`runFile()` 변경 — `hadRuntimeError` 시 exit code 70:

```kotlin
private fun runFile(path: String) {
    val source = Files.readString(Paths.get(path), StandardCharsets.UTF_8)
    run(source)
    if (GwanLang.hadError) exitProcess(65)
    if (GwanLang.hadRuntimeError) exitProcess(70)
}
```

`runPrompt()` 변경 — `hadRuntimeError` 리셋:

```kotlin
private fun runPrompt() {
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false
    }
}
```

- [ ] **Step 4: 테스트 실행 — 전체 통과 확인**

Run: `./gradlew test`
Expected: PASS (기존 Scanner/Parser 테스트 + Interpreter 테스트 전체)

- [ ] **Step 5: 예제 파일 생성**

`examples/evaluator-demo.gwan`:

```
1 + 2 * 3
```

- [ ] **Step 6: 예제 실행 확인**

Run: `./gradlew run --args="examples/evaluator-demo.gwan"`
Expected: `7`

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/gwanlang/GwanLang.kt src/test/kotlin/gwanlang/InterpreterTest.kt examples/evaluator-demo.gwan
git commit -m "feat: GwanLang 파이프라인에 Interpreter 연결"
```

---

## Task 9: 문서 업데이트 및 Phase 3 완료

**Files:**
- Modify: `docs/CHANGELOG.md`
- Modify: `GWANLANG_SPEC.md`

- [ ] **Step 1: CHANGELOG.md 업데이트**

`docs/CHANGELOG.md`의 `## [Unreleased]` 바로 아래에 추가:

```markdown
### Phase 3: Evaluator — 표현식 평가
- `Interpreter` 클래스 — `when` 표현식 기반 Tree-walking 평가기
  - 산술 연산: `+`, `-`, `*`, `/`
  - 비교 연산: `>`, `>=`, `<`, `<=`
  - 동등 연산: `==`, `!=`
  - 단항 연산: `-` (부호 반전), `!` (논리 부정)
  - 문자열 연결: `+` (양쪽 모두 String일 때)
  - Truthiness: `nil`, `false`만 falsy
- `RuntimeError` 예외 클래스 — 런타임 타입 에러 리포팅
  - 0으로 나누기 RuntimeError
  - 타입 불일치 RuntimeError (줄 번호 포함)
- `GwanLang.kt` 파이프라인 변경: Scanner → Parser → Interpreter
  - `runtimeError()` 메서드, `hadRuntimeError` 플래그, exit code 70
  - REPL에서 `hadRuntimeError` 리셋
- `examples/evaluator-demo.gwan` — 표현식 평가 시연 예제
- 테스트: `InterpreterTest` — TDD 사이클 14개 기반
```

- [ ] **Step 2: GWANLANG_SPEC.md Phase 3 체크박스 업데이트**

Phase 3 완료 기준 체크박스를 모두 `[x]`로 변경:

```markdown
**완료 기준:**
- [x] 산술/비교/논리 연산 평가
- [x] 런타임 타입 에러 처리
- [x] Truthiness 규칙: `nil`, `false` → falsy, 나머지 → truthy
- [x] 문자열 연결 (`+`)
- [x] 테스트: REPL에서 표현식 평가
```

- [ ] **Step 3: 전체 빌드 및 테스트 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew test`
Expected: 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add docs/CHANGELOG.md GWANLANG_SPEC.md
git commit -m "docs: Phase 3 완료 기록"
```
