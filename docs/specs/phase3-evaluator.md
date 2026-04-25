# Phase 3: Evaluator (표현식 평가) 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 3
> 참고: Crafting Interpreters Ch. 7 "Evaluating Expressions"

## 1. 목적

Parser가 생성한 표현식 AST(`Expr`)를 재귀적으로 평가하여 런타임 값(`Any?`)을
반환하는 Tree-walking Interpreter를 TDD로 구현한다.

```
Expr.Binary(Expr.Literal(1.0), +, Expr.Literal(2.0))
──► Interpreter ──►
3.0
```

Phase 3는 **표현식 평가만** 다룬다. 문장 실행, 변수, 제어흐름은 Phase 4에서 추가한다.

## 2. 범위

### In Scope
- `Interpreter` 클래스 — `when` 표현식 기반 Tree-walking 평가기
- `RuntimeError` 예외 클래스 — 런타임 타입 에러 리포팅
- 산술 연산 (`+`, `-`, `*`, `/`)
- 비교 연산 (`>`, `>=`, `<`, `<=`)
- 동등 연산 (`==`, `!=`)
- 단항 연산 (`-` 부호 반전, `!` 논리 부정)
- 문자열 연결 (`+` — 양쪽 모두 String일 때)
- Truthiness 규칙 (`nil`, `false` → falsy, 나머지 → truthy)
- 0으로 나누기 RuntimeError 처리
- `GwanLang.kt` 파이프라인 변경 (AstPrinter → Interpreter)
- `GwanLang` 객체에 `runtimeError()` 메서드 및 `hadRuntimeError` 플래그 추가

### Out of Scope (이후 Phase)
- `Stmt` 실행 (Phase 4)
- `Environment` 스코프 체인 (Phase 4)
- 변수 (`Expr.Variable`, `Expr.Assign`) (Phase 4)
- 논리 연산자 `and`, `or` (`Expr.Logical`) (Phase 4)
- 함수 호출 (`Expr.Call`) (Phase 5)
- 클래스 관련 (`Expr.Get`, `Expr.Set`, `Expr.This`, `Expr.Super`) (Phase 7)
- 문자열 + 숫자 자동 변환 (허용하지 않음 — RuntimeError)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Interpreter.kt` | Tree-walking 표현식 평가기 (신규) |
| `RuntimeError.kt` | 런타임 에러 예외 클래스 (신규) |
| `GwanLang.kt` | `runtimeError()` 추가, `run()`에서 Interpreter 사용 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `InterpreterTest.kt` | Interpreter 평가 테스트 전체 (신규) |

### 기타
- `examples/evaluator-demo.gwan` — 표현식 평가 시연 예제
- `docs/CHANGELOG.md` — Phase 3 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 런타임 타입 시스템

GwanLang의 런타임 값은 Kotlin 타입에 직접 매핑된다:

| GwanLang 타입 | Kotlin 표현 | 예시 |
|--------------|------------|------|
| `nil` | `null` | `nil` |
| `Boolean` | `Boolean` | `true`, `false` |
| `Number` | `Double` | `3.14`, `42.0` |
| `String` | `String` | `"hello"` |

### 4.2 `RuntimeError`

```kotlin
class RuntimeError(val token: Token, message: String) : RuntimeException(message)
```

- 연산자 토큰을 보관하여 에러 메시지에 줄 번호를 포함할 수 있다.
- `GwanLang.runtimeError()`에서 catch하여 리포팅한다.

### 4.3 `Interpreter`

```kotlin
class Interpreter {
    fun interpret(expression: Expr)
    private fun evaluate(expr: Expr): Any?
    private fun isTruthy(value: Any?): Boolean
    private fun isEqual(a: Any?, b: Any?): Boolean
    private fun stringify(value: Any?): String
    private fun checkNumberOperand(op: Token, operand: Any?)
    private fun checkNumberOperands(op: Token, left: Any?, right: Any?)
}
```

### 4.4 평가 알고리즘

```
evaluate(expr):
    when expr:
        Literal  → expr.value
        Grouping → evaluate(expr.expression)
        Unary    →
            right = evaluate(expr.right)
            when expr.op.type:
                MINUS → checkNumberOperand; -(right as Double)
                BANG  → !isTruthy(right)
        Binary   →
            left = evaluate(expr.left)
            right = evaluate(expr.right)
            when expr.op.type:
                MINUS         → checkNumberOperands; left - right
                STAR          → checkNumberOperands; left * right
                SLASH         → checkNumberOperands; 0 나누기 검사; left / right
                PLUS          →
                    if (left is Double && right is Double) → left + right
                    if (left is String && right is String) → left + right
                    else → RuntimeError("Operands must be two numbers or two strings.")
                GREATER       → checkNumberOperands; left > right
                GREATER_EQUAL → checkNumberOperands; left >= right
                LESS          → checkNumberOperands; left < right
                LESS_EQUAL    → checkNumberOperands; left <= right
                EQUAL_EQUAL   → isEqual(left, right)
                BANG_EQUAL    → !isEqual(left, right)
```

### 4.5 Truthiness 규칙

```
isTruthy(value):
    if value == null → false
    if value is Boolean → value
    else → true
```

- `nil`과 `false`만 falsy, 나머지는 모두 truthy
- `0`, `""` (빈 문자열)도 truthy (Ruby/Lox 스타일)

### 4.6 동등 비교 규칙

```
isEqual(a, b):
    if a == null && b == null → true
    if a == null → false
    return a == b  // Kotlin 구조적 동등
```

- 타입이 다르면 `false` (에러가 아님)
- `nil == nil` → `true`

### 4.7 stringify 규칙

```
stringify(value):
    if value == null → "nil"
    if value is Double:
        text = value.toString()
        if text.endsWith(".0") → text.dropLast(2)
        return text
    return value.toString()
```

- 정수 값의 `.0` 접미사 제거: `2.0` → `"2"`, `3.14` → `"3.14"`

### 4.8 0으로 나누기 처리

`SLASH` 연산에서 우측 피연산자가 `0.0`인 경우:

```kotlin
if (right == 0.0) throw RuntimeError(expr.op, "Division by zero.")
```

### 4.9 `GwanLang.kt` 변경

```kotlin
object GwanLang {
    var hadError: Boolean = false
    var hadRuntimeError: Boolean = false  // 추가

    fun runtimeError(error: RuntimeError) {  // 추가
        System.err.println("[line ${error.token.line}] RuntimeError: ${error.message}")
        hadRuntimeError = true
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

`runFile()` 변경:

```kotlin
private fun runFile(path: String) {
    val source = Files.readString(Paths.get(path), StandardCharsets.UTF_8)
    run(source)
    if (GwanLang.hadError) exitProcess(65)
    if (GwanLang.hadRuntimeError) exitProcess(70)  // 추가
}
```

`runPrompt()` REPL에서는 `hadRuntimeError`도 리셋:

```kotlin
private fun runPrompt() {
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        GwanLang.hadError = false
        GwanLang.hadRuntimeError = false  // 추가
    }
}
```

### 4.10 `interpret()` 메서드

```kotlin
fun interpret(expression: Expr) {
    try {
        val value = evaluate(expression)
        println(stringify(value))
    } catch (error: RuntimeError) {
        GwanLang.runtimeError(error)
    }
}
```

## 5. 에러 처리 방침

| 상황 | 동작 |
|------|------|
| 단항 `-`에 숫자 아닌 피연산자 | RuntimeError: "Operand must be a number." |
| 이항 `-`, `*`, `/`에 숫자 아닌 피연산자 | RuntimeError: "Operands must be numbers." |
| 비교 연산자에 숫자 아닌 피연산자 | RuntimeError: "Operands must be numbers." |
| `+`에 (Double, Double)도 (String, String)도 아닌 조합 | RuntimeError: "Operands must be two numbers or two strings." |
| 0으로 나누기 | RuntimeError: "Division by zero." |
| `==`, `!=`의 타입 불일치 | 에러 아님, `false` 반환 |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | RuntimeError 정의 | RuntimeError 생성, token/message 접근 | `RuntimeError.kt` |
| 2 | 리터럴 평가 | `42` → `42.0`, `"hi"` → `"hi"`, `true`/`false`/`nil` | `evaluate()` Literal 분기 |
| 3 | 괄호 평가 | `(42)` → `42.0` | `evaluate()` Grouping 분기 |
| 4 | 단항 연산 | `-3` → `-3.0`, `!true` → `false`, `!nil` → `true` | `evaluate()` Unary 분기 |
| 5 | 단항 타입 에러 | `-"text"` → RuntimeError | `checkNumberOperand()` |
| 6 | 산술 이항 연산 | `1+2` → `3.0`, `6/3` → `2.0` | `evaluate()` Binary 산술 분기 |
| 7 | 0으로 나누기 | `1/0` → RuntimeError | Binary SLASH 분기 |
| 8 | 문자열 연결 | `"a"+"b"` → `"ab"` | Binary PLUS String 분기 |
| 9 | `+` 타입 에러 | `1+"a"` → RuntimeError | Binary PLUS 타입 검사 |
| 10 | 비교 연산 | `1<2` → `true`, `3>=3` → `true` | Binary 비교 분기 |
| 11 | 비교 타입 에러 | `"a"<1` → RuntimeError | `checkNumberOperands()` |
| 12 | 동등 비교 | `1==1` → `true`, `nil==nil` → `true`, `1=="1"` → `false` | `isEqual()` |
| 13 | Truthiness | `!0` → `false` (0 is truthy), `!""` → `false` | `isTruthy()` |
| 14 | stringify | `2.0` → `"2"`, `3.14` → `"3.14"`, `nil` → `"nil"` | `stringify()` |
| 15 | GwanLang 통합 | 파이프라인 Scanner→Parser→Interpreter, 런타임 에러 리포팅 | `GwanLang.kt` 수정 |

### 테스트 작성 규약
- 헬퍼: `fun evaluate(src: String): Any?` — Scanner + Parser + Interpreter.evaluate()를 결합한 편의 함수
- RuntimeError 테스트: `assertThrows<RuntimeError>` 사용
- 통합 테스트: stdout/stderr 캡처로 출력 검증

## 7. 완료 기준 (Definition of Done)

- [ ] `RuntimeError` 예외 클래스 정의
- [ ] `Interpreter` 클래스 — 모든 `Expr` 노드 평가
- [ ] 산술 연산 (`+`, `-`, `*`, `/`) 정상 동작
- [ ] 비교 연산 (`>`, `>=`, `<`, `<=`) 정상 동작
- [ ] 동등 연산 (`==`, `!=`) 정상 동작 (타입 불일치 시 `false`)
- [ ] 단항 연산 (`-`, `!`) 정상 동작
- [ ] 문자열 연결 (`+`) 정상 동작
- [ ] Truthiness 규칙 적용 (`nil`, `false`만 falsy)
- [ ] 0으로 나누기 RuntimeError
- [ ] 타입 에러 시 RuntimeError + 줄 번호 포함 메시지
- [ ] `GwanLang.kt` 파이프라인 변경 (Interpreter 사용)
- [ ] `hadRuntimeError` 플래그 및 exit code 70
- [ ] REPL에서 표현식 평가 결과 출력
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/evaluator-demo.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 3 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `feat: RuntimeError 예외 클래스 정의`
2. `feat: Interpreter 골격 및 리터럴/괄호 평가`
3. `feat: 단항 연산자 평가 (-,!)`
4. `feat: 산술 이항 연산자 평가 (+,-,*,/)`
5. `feat: 0으로 나누기 RuntimeError 처리`
6. `feat: 문자열 연결 및 + 타입 검사`
7. `feat: 비교/동등 연산자 평가`
8. `feat: GwanLang 파이프라인에 Interpreter 연결`
9. `docs: Phase 3 완료 기록`
