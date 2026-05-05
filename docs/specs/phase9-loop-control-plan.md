# Phase 9: break/continue 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** while/for 루프에서 `break`(즉시 탈출)와 `continue`(다음 반복으로 건너뛰기) 제어흐름을 구현한다.

**Architecture:** `Return` 예외 패턴을 재활용하여 `Break`/`Continue` 예외 클래스를 만들고, Interpreter의 루프 실행부에서 catch한다. 기존 `for` 디슈가링을 제거하고 `Stmt.For` AST 노드를 도입하여 `continue` 시 increment가 항상 실행되도록 보장한다. Resolver에서 `currentLoop` 카운터로 루프 밖 사용을 정적 검출한다.

**Tech Stack:** Kotlin, JUnit 5, Gradle

**스펙 문서:** `docs/specs/phase9-loop-control.md`

---

### Task 1: Scanner — break/continue 키워드 스캔

**Files:**
- Modify: `src/main/kotlin/gwanlang/TokenType.kt:19` (키워드 enum)
- Modify: `src/main/kotlin/gwanlang/Scanner.kt:114-131` (KEYWORDS 맵)
- Create: `src/test/kotlin/gwanlang/LoopControlTest.kt`

- [ ] **Step 1: Write the failing test — break/continue 키워드 스캔**

`src/test/kotlin/gwanlang/LoopControlTest.kt` 생성:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: FAIL — `TokenType.BREAK` and `TokenType.CONTINUE` do not exist

- [ ] **Step 3: Add BREAK/CONTINUE to TokenType**

`src/main/kotlin/gwanlang/TokenType.kt` — 키워드 섹션에 추가:

```kotlin
    // 키워드 (16종 → 18종)
    AND, BREAK, CLASS, CONTINUE, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,
```

- [ ] **Step 4: Add break/continue to Scanner KEYWORDS map**

`src/main/kotlin/gwanlang/Scanner.kt` — KEYWORDS 맵에 추가:

```kotlin
        private val KEYWORDS: Map<String, TokenType> = mapOf(
            "and" to TokenType.AND,
            "break" to TokenType.BREAK,
            "class" to TokenType.CLASS,
            "continue" to TokenType.CONTINUE,
            "else" to TokenType.ELSE,
            "false" to TokenType.FALSE,
            "for" to TokenType.FOR,
            "fun" to TokenType.FUN,
            "if" to TokenType.IF,
            "nil" to TokenType.NIL,
            "or" to TokenType.OR,
            "print" to TokenType.PRINT,
            "return" to TokenType.RETURN,
            "super" to TokenType.SUPER,
            "this" to TokenType.THIS,
            "true" to TokenType.TRUE,
            "var" to TokenType.VAR,
            "while" to TokenType.WHILE,
        )
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: PASS

- [ ] **Step 6: Run full test suite for regression**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/gwanlang/TokenType.kt src/main/kotlin/gwanlang/Scanner.kt src/test/kotlin/gwanlang/LoopControlTest.kt
git commit -m "feat: TokenType에 BREAK, CONTINUE 키워드 추가 및 Scanner 스캔"
```

---

### Task 2: AST 노드 + 예외 클래스 추가

**Files:**
- Modify: `src/main/kotlin/gwanlang/Stmt.kt:3-13` (sealed class에 노드 추가)
- Create: `src/main/kotlin/gwanlang/Break.kt`
- Create: `src/main/kotlin/gwanlang/Continue.kt`

- [ ] **Step 1: Add Stmt.Break, Stmt.Continue, Stmt.For to Stmt.kt**

`src/main/kotlin/gwanlang/Stmt.kt`:

```kotlin
package gwanlang

sealed class Stmt {
    data class Expression(val expression: Expr) : Stmt()
    data class Print(val expression: Expr) : Stmt()
    data class Var(val name: Token, val initializer: Expr?) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    data class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt()
    data class While(val condition: Expr, val body: Stmt) : Stmt()
    data class Function(val name: Token, val params: List<Token>, val body: List<Stmt>) : Stmt()
    data class Return(val keyword: Token, val value: Expr?) : Stmt()
    data class Class(val name: Token, val superclass: Expr.Variable?, val methods: List<Stmt.Function>) : Stmt()
    data class Break(val keyword: Token) : Stmt()
    data class Continue(val keyword: Token) : Stmt()
    data class For(val initializer: Stmt?, val condition: Expr?, val increment: Expr?, val body: Stmt) : Stmt()
}
```

- [ ] **Step 2: Create Break.kt**

`src/main/kotlin/gwanlang/Break.kt`:

```kotlin
package gwanlang

class Break : RuntimeException(null, null, true, false)
```

- [ ] **Step 3: Create Continue.kt**

`src/main/kotlin/gwanlang/Continue.kt`:

```kotlin
package gwanlang

class Continue : RuntimeException(null, null, true, false)
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileKotlin`
Expected: SUCCESS (컴파일 오류 없음 — 새 Stmt 서브타입을 when에서 아직 처리하지 않으면 Kotlin이 `when` exhaustive 검사에서 경고/에러를 발생시킴. `Resolver.kt`와 `Interpreter.kt`의 `when`이 `sealed class`에 대해 non-exhaustive가 되므로 컴파일 에러 발생 가능)

**참고:** `Resolver.resolve(Stmt)`와 `Interpreter.execute(Stmt)`의 `when`이 exhaustive하므로, 새 서브타입 추가 시 즉시 컴파일 에러가 발생한다. 이는 Task 3~5에서 순차적으로 해결한다. 컴파일이 실패하면 다음 Task로 바로 진행한다.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/gwanlang/Stmt.kt src/main/kotlin/gwanlang/Break.kt src/main/kotlin/gwanlang/Continue.kt
git commit -m "feat: Stmt.Break, Stmt.Continue, Stmt.For AST 노드 및 예외 클래스 추가"
```

---

### Task 3: Parser — break/continue 파싱 + forStatement 리팩토링

**Files:**
- Modify: `src/main/kotlin/gwanlang/Parser.kt:75-161` (statement, forStatement, synchronize)
- Modify: `src/test/kotlin/gwanlang/LoopControlTest.kt` (파싱 테스트 추가)

- [ ] **Step 1: Write the failing test — break/continue 파싱**

`src/test/kotlin/gwanlang/LoopControlTest.kt`에 추가:

```kotlin
    // === TDD 사이클 2: Parser — break 파싱 ===

    @Test
    fun `break 문을 파싱한다`() {
        val tokens = scan("break;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val stmt = stmts[0] as Stmt.Break
        assertEquals("break", stmt.keyword.lexeme)
    }

    // === TDD 사이클 3: Parser — continue 파싱 ===

    @Test
    fun `continue 문을 파싱한다`() {
        val tokens = scan("continue;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val stmt = stmts[0] as Stmt.Continue
        assertEquals("continue", stmt.keyword.lexeme)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: FAIL — `break` is not recognized as a statement keyword (현재 `statement()`에 BREAK 분기 없음)

- [ ] **Step 3: Add break/continue parsing to Parser.statement()**

`src/main/kotlin/gwanlang/Parser.kt` — `statement()` 메서드에 분기 추가:

```kotlin
    private fun statement(): Stmt {
        if (match(TokenType.BREAK)) return breakStatement()
        if (match(TokenType.CONTINUE)) return continueStatement()
        if (match(TokenType.PRINT)) return printStatement()
        if (match(TokenType.RETURN)) return returnStatement()
        if (match(TokenType.LEFT_BRACE)) return Stmt.Block(block())
        if (match(TokenType.IF)) return ifStatement()
        if (match(TokenType.WHILE)) return whileStatement()
        if (match(TokenType.FOR)) return forStatement()
        return expressionStatement()
    }

    private fun breakStatement(): Stmt {
        val keyword = previous()
        consume(TokenType.SEMICOLON, "Expect ';' after 'break'.")
        return Stmt.Break(keyword)
    }

    private fun continueStatement(): Stmt {
        val keyword = previous()
        consume(TokenType.SEMICOLON, "Expect ';' after 'continue'.")
        return Stmt.Continue(keyword)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test — for 문 Stmt.For 반환**

`src/test/kotlin/gwanlang/LoopControlTest.kt`에 추가:

```kotlin
    // === TDD 사이클 4: Parser — for 문 Stmt.For 반환 ===

    @Test
    fun `for 문을 Stmt_For로 파싱한다`() {
        val tokens = scan("for (var i = 0; i < 10; i += 1) print i;")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val forStmt = stmts[0] as Stmt.For
        assertTrue(forStmt.initializer is Stmt.Var)
        assertTrue(forStmt.condition is Expr.Binary)
        assertTrue(forStmt.increment is Expr.Assign)
        assertTrue(forStmt.body is Stmt.Print)
    }
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew test --tests "gwanlang.LoopControlTest.for 문을 Stmt_For로 파싱한다"`
Expected: FAIL — `forStatement()` returns `Stmt.While` (디슈가링), not `Stmt.For`

- [ ] **Step 7: Refactor forStatement() to return Stmt.For**

`src/main/kotlin/gwanlang/Parser.kt` — `forStatement()` 전체 교체:

```kotlin
    private fun forStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer: Stmt? = if (match(TokenType.SEMICOLON)) {
            null
        } else if (match(TokenType.VAR)) {
            varDeclaration()
        } else {
            expressionStatement()
        }

        val condition = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(TokenType.RIGHT_PAREN)) expression() else null
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")

        val body = statement()

        return Stmt.For(initializer, condition, increment, body)
    }
```

- [ ] **Step 8: Update synchronize() with break/continue keywords**

`src/main/kotlin/gwanlang/Parser.kt` — `synchronize()` 메서드:

```kotlin
    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.BREAK, TokenType.CLASS, TokenType.CONTINUE,
                TokenType.FUN, TokenType.VAR, TokenType.FOR,
                TokenType.IF, TokenType.WHILE, TokenType.PRINT,
                TokenType.RETURN -> return
                else -> advance()
            }
        }
    }
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: PASS

**참고:** 이 시점에서 전체 테스트(`./gradlew test`)는 실패할 수 있다. Resolver와 Interpreter의 `when`이 `Stmt.Break`, `Stmt.Continue`, `Stmt.For`를 처리하지 않기 때문이다. Task 4, 5에서 해결한다.

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/gwanlang/Parser.kt src/test/kotlin/gwanlang/LoopControlTest.kt
git commit -m "feat: Parser에서 break/continue 파싱 및 forStatement() Stmt.For 반환"
```

---

### Task 4: Resolver — 루프 밖 break/continue 정적 에러 검출

**Files:**
- Modify: `src/main/kotlin/gwanlang/Resolver.kt:3-97` (currentLoop, resolve(Stmt), resolveFunction)
- Modify: `src/test/kotlin/gwanlang/LoopControlTest.kt` (에러 검출 테스트 추가)

- [ ] **Step 1: Write the failing test — 루프 밖 break 에러**

`src/test/kotlin/gwanlang/LoopControlTest.kt`에 추가:

```kotlin
    // === TDD 사이클 12: Resolver — 루프 밖 break 에러 ===

    @Test
    fun `루프 밖에서 break 사용 시 정적 에러`() {
        runAndCapture("break;")
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 13: Resolver — 루프 밖 continue 에러 ===

    @Test
    fun `루프 밖에서 continue 사용 시 정적 에러`() {
        runAndCapture("continue;")
        assertTrue(GwanLang.hadError)
    }

    // === TDD 사이클 14: Resolver — 함수 안에서 바깥 루프 break 에러 ===

    @Test
    fun `함수 안에서 바깥 루프의 break 사용 시 정적 에러`() {
        runAndCapture("""
            while (true) {
                fun f() {
                    break;
                }
                f();
            }
        """.trimIndent())
        assertTrue(GwanLang.hadError)
    }

    @Test
    fun `함수 안에서 바깥 루프의 continue 사용 시 정적 에러`() {
        runAndCapture("""
            while (true) {
                fun f() {
                    continue;
                }
                f();
            }
        """.trimIndent())
        assertTrue(GwanLang.hadError)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: FAIL — Resolver에 `Stmt.Break`/`Stmt.Continue`/`Stmt.For` 처리 없음 (컴파일 에러 또는 런타임 에러)

- [ ] **Step 3: Add currentLoop and new Stmt handling to Resolver**

`src/main/kotlin/gwanlang/Resolver.kt`:

`currentLoop` 필드 추가 (line 6 근처):

```kotlin
class Resolver(private val interpreter: Interpreter) {

    private val scopes = ArrayDeque<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE
    private var currentLoop = 0
```

`resolve(Stmt)` when에 새 분기 추가 (기존 `Stmt.While` 분기 뒤에):

```kotlin
            is Stmt.While -> {
                resolve(stmt.condition)
                currentLoop++
                resolve(stmt.body)
                currentLoop--
            }
            is Stmt.For -> {
                beginScope()
                if (stmt.initializer != null) resolve(stmt.initializer)
                if (stmt.condition != null) resolve(stmt.condition)
                if (stmt.increment != null) resolve(stmt.increment)
                currentLoop++
                resolve(stmt.body)
                currentLoop--
                endScope()
            }
            is Stmt.Break -> {
                if (currentLoop == 0) {
                    GwanLang.error(stmt.keyword, "Can't use 'break' outside of a loop.")
                }
            }
            is Stmt.Continue -> {
                if (currentLoop == 0) {
                    GwanLang.error(stmt.keyword, "Can't use 'continue' outside of a loop.")
                }
            }
```

`resolveFunction()` 메서드에서 루프 상태 리셋:

```kotlin
    private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        val enclosingLoop = currentLoop
        currentFunction = type
        currentLoop = 0

        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()

        currentFunction = enclosingFunction
        currentLoop = enclosingLoop
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: PASS (Scanner + Parser 테스트는 통과, Resolver 에러 검출 테스트도 통과)

**참고:** 전체 `./gradlew test`는 아직 실패할 수 있다 — `Interpreter.execute()`의 `when`이 새 Stmt 서브타입을 처리하지 않기 때문이다.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/gwanlang/Resolver.kt src/test/kotlin/gwanlang/LoopControlTest.kt
git commit -m "feat: Resolver에서 루프 밖 break/continue 정적 에러 검출"
```

---

### Task 5: Interpreter — break/continue 실행 + Stmt.For

**Files:**
- Modify: `src/main/kotlin/gwanlang/Interpreter.kt:31-43,57-119` (interpret, execute)
- Modify: `src/test/kotlin/gwanlang/LoopControlTest.kt` (실행 테스트 추가)

- [ ] **Step 1: Write the failing test — while + break**

`src/test/kotlin/gwanlang/LoopControlTest.kt`에 추가:

```kotlin
    // === TDD 사이클 5: Interpreter — while + break ===

    @Test
    fun `while 루프에서 break로 탈출한다`() {
        val result = runAndCapture("""
            var i = 0;
            while (true) {
                if (i >= 3) break;
                print i;
                i += 1;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "gwanlang.LoopControlTest.while 루프에서 break로 탈출한다"`
Expected: FAIL — `Interpreter.execute()`에 `Stmt.Break`/`Stmt.Continue`/`Stmt.For` 처리 없음

- [ ] **Step 3: Add break/continue/for handling to Interpreter**

`src/main/kotlin/gwanlang/Interpreter.kt` — `execute()` when에 새 분기 추가:

`Stmt.Break`와 `Stmt.Continue` throw 추가:

```kotlin
            is Stmt.Break -> throw Break()
            is Stmt.Continue -> throw Continue()
```

`Stmt.While` 분기를 Break/Continue catch로 교체:

```kotlin
            is Stmt.While -> {
                try {
                    while (isTruthy(evaluate(stmt.condition))) {
                        try {
                            execute(stmt.body)
                        } catch (_: Continue) {
                            // 다음 반복으로
                        }
                    }
                } catch (_: Break) {
                    // 루프 탈출
                }
            }
```

`Stmt.For` 분기 추가:

```kotlin
            is Stmt.For -> {
                val forEnv = Environment(environment)
                val previous = this.environment
                try {
                    this.environment = forEnv
                    if (stmt.initializer != null) execute(stmt.initializer)
                    val condition = stmt.condition ?: Expr.Literal(true)
                    while (isTruthy(evaluate(condition))) {
                        try {
                            execute(stmt.body)
                        } catch (_: Continue) {
                            // body 나머지 건너뛰고 increment로
                        }
                        if (stmt.increment != null) evaluate(stmt.increment)
                    }
                } catch (_: Break) {
                    // 루프 탈출
                } finally {
                    this.environment = previous
                }
            }
```

`interpret()` 메서드에 최상위 Break/Continue 안전 catch 추가:

```kotlin
    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RuntimeError) {
            GwanLang.runtimeError(error)
        } catch (_: Return) {
            System.err.println("RuntimeError: Can't return from top-level code.")
            GwanLang.hadRuntimeError = true
        } catch (_: Break) {
            System.err.println("RuntimeError: Can't use 'break' outside of a loop.")
            GwanLang.hadRuntimeError = true
        } catch (_: Continue) {
            System.err.println("RuntimeError: Can't use 'continue' outside of a loop.")
            GwanLang.hadRuntimeError = true
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "gwanlang.LoopControlTest.while 루프에서 break로 탈출한다"`
Expected: PASS

- [ ] **Step 5: Write more tests — while+continue, for+break, for+continue**

`src/test/kotlin/gwanlang/LoopControlTest.kt`에 추가:

```kotlin
    // === TDD 사이클 6: Interpreter — while + continue ===

    @Test
    fun `while 루프에서 continue로 다음 반복으로 건너뛴다`() {
        val result = runAndCapture("""
            var i = 0;
            while (i < 5) {
                i += 1;
                if (i % 2 == 0) continue;
                print i;
            }
        """.trimIndent())
        assertEquals("1\n3\n5", result)
    }

    // === TDD 사이클 7: Interpreter — for + break ===

    @Test
    fun `for 루프에서 break로 탈출한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 10; i += 1) {
                if (i >= 3) break;
                print i;
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // === TDD 사이클 8: Interpreter — for + continue (increment 실행 보장) ===

    @Test
    fun `for 루프에서 continue 시 increment가 실행된다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 10; i += 1) {
                if (i % 2 == 0) continue;
                print i;
            }
        """.trimIndent())
        assertEquals("1\n3\n5\n7\n9", result)
    }

    // === TDD 사이클 9: 중첩 루프 + break ===

    @Test
    fun `중첩 루프에서 break는 가장 가까운 루프만 탈출한다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                for (var j = 0; j < 3; j += 1) {
                    if (j >= 1) break;
                    print i;
                }
            }
        """.trimIndent())
        assertEquals("0\n1\n2", result)
    }

    // === TDD 사이클 10: 중첩 루프 + continue ===

    @Test
    fun `중첩 루프에서 continue는 가장 가까운 루프만 건너뛴다`() {
        val result = runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                for (var j = 0; j < 3; j += 1) {
                    if (j == 1) continue;
                    print j;
                }
            }
        """.trimIndent())
        assertEquals("0\n2\n0\n2\n0\n2", result)
    }

    // === TDD 사이클 11: for 루프 변수 스코프 ===

    @Test
    fun `for 루프 변수는 루프 밖에서 접근 불가`() {
        runAndCapture("""
            for (var i = 0; i < 3; i += 1) {
                print i;
            }
            print i;
        """.trimIndent())
        assertTrue(GwanLang.hadRuntimeError)
    }
```

- [ ] **Step 6: Run all tests to verify they pass**

Run: `./gradlew test --tests "gwanlang.LoopControlTest"`
Expected: ALL PASS

- [ ] **Step 7: Run full test suite for regression**

Run: `./gradlew test`
Expected: ALL PASS (기존 for 루프 테스트 포함 — Stmt.For가 기존 디슈가링과 동일한 동작 보장)

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/gwanlang/Interpreter.kt src/test/kotlin/gwanlang/LoopControlTest.kt
git commit -m "feat: Interpreter에서 while/for의 break/continue 처리 및 Stmt.For 실행"
```

---

### Task 6: 마무리 — 예제 파일 + 문서 업데이트

**Files:**
- Create: `examples/loop_control.gwan`
- Modify: `docs/CHANGELOG.md`
- Modify: `GWANLANG_SPEC.md:583` (§6 진행 추적표)

- [ ] **Step 1: Create example file**

`examples/loop_control.gwan`:

```gwan
// Phase 9: break / continue 예제

// === break ===
// while + break: 3까지만 출력
var i = 0;
while (true) {
    if (i >= 3) break;
    print i;
    i += 1;
}
// 출력: 0, 1, 2

// for + break: 5 이상이면 중단
for (var n = 0; n < 100; n += 1) {
    if (n >= 5) break;
    print n;
}
// 출력: 0, 1, 2, 3, 4

// === continue ===
// 홀수만 출력
for (var i = 0; i < 10; i += 1) {
    if (i % 2 == 0) continue;
    print i;
}
// 출력: 1, 3, 5, 7, 9

// === 중첩 루프 ===
// 내부 루프만 break
for (var x = 1; x <= 3; x += 1) {
    for (var y = 1; y <= 3; y += 1) {
        if (y > x) break;
        print x * 10 + y;
    }
}
// 출력: 11, 21, 22, 31, 32, 33
```

- [ ] **Step 2: Run the example**

Run: `./gradlew run --args="examples/loop_control.gwan"`
Expected: 출력이 주석과 일치

- [ ] **Step 3: Update CHANGELOG.md**

`docs/CHANGELOG.md`에 Phase 9 완료 기록 추가 (기존 항목 위에):

```markdown
## Phase 9: break / continue — 루프 제어흐름 (YYYY-MM-DD)

- `break` 문: 가장 가까운 루프 즉시 종료
- `continue` 문: 현재 반복 건너뛰고 다음 반복으로
- `Stmt.For` AST 노드 도입 (기존 for 디슈가링 제거)
  - `continue` 시 increment 표현식이 항상 실행됨
- Resolver: 루프 밖 break/continue 정적 에러 검출
- Resolver: 함수 경계에서 루프 상태 리셋 (함수 안에서 바깥 루프 break/continue 차단)
```

- [ ] **Step 4: Update GWANLANG_SPEC.md §6 진행 추적표**

`GWANLANG_SPEC.md` line 583 — Phase 9 행 업데이트:

```markdown
| 9. break/continue | ✅ 완료 | YYYY-MM-DD | YYYY-MM-DD | Stmt.For 도입, TDD 사이클 N개 |
```

- [ ] **Step 5: Run full test suite one final time**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add examples/loop_control.gwan docs/CHANGELOG.md GWANLANG_SPEC.md
git commit -m "docs: Phase 9 완료 기록"
```
