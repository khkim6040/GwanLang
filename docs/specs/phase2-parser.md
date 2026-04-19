# Phase 2: Parser (표현식) 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 2
> 참고: Crafting Interpreters Ch. 5 "Representing Code", Ch. 6 "Parsing Expressions"

## 1. 목적

Scanner가 생성한 토큰 리스트(`List<Token>`)를 받아 **표현식 AST**(`Expr`)로
변환하는 Recursive Descent Parser를 TDD로 구현한다.

```
[NUMBER(1), PLUS, NUMBER(2), STAR, NUMBER(3), EOF]
──► Parser ──►
Binary(Literal(1.0), +, Binary(Literal(2.0), *, Literal(3.0)))
```

Phase 2는 **표현식만** 다룬다. 문장(`Stmt`), 변수, 제어흐름은 Phase 4에서 추가한다.

## 2. 범위

### In Scope
- `Expr` sealed class 계층 (Binary, Grouping, Literal, Unary — 4종)
- `Parser` 클래스 — Recursive Descent 방식 표현식 파싱
- 연산자 우선순위 및 결합 방향 (좌결합)
- 파싱 에러 리포팅 (토큰 위치 포함)
- `AstPrinter` — S-expression 형식 AST 출력
- `GwanLang.kt` 파이프라인 확장 (Scanner → Parser → AstPrinter)

### Out of Scope (이후 Phase)
- `Stmt` sealed class 전체 (Phase 4)
- `Expr.Variable`, `Expr.Assign` (Phase 4 — 변수)
- `Expr.Logical` (Phase 4 — 제어흐름)
- `Expr.Call` (Phase 5 — 함수)
- `Expr.Get`, `Expr.Set`, `Expr.This`, `Expr.Super` (Phase 7 — 클래스)
- 식별자(`IDENTIFIER`) primary 파싱 (변수 없이는 의미 없음)
- `synchronize` 에러 복구 (문장 경계 기반, Phase 4)
- 콤마 연산자, 삼항 연산자

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Expr.kt` | `Expr` sealed class 계층 (4종) |
| `Parser.kt` | Recursive Descent Parser |
| `AstPrinter.kt` | S-expression 형식 AST 출력 |
| `GwanLang.kt` | `run()` 함수에 Parser 단계 추가 (기존 파일 수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `ExprTest.kt` | Expr data class 생성/필드 접근 테스트 |
| `ParserTest.kt` | 파싱 테스트 전체 |
| `AstPrinterTest.kt` | AST 출력 테스트 |

### 기타
- `examples/parser-demo.gwan` — 표현식 파싱 시연 예제
- `docs/CHANGELOG.md` — Phase 2 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 문법 규칙 (EBNF)

```ebnf
expression → equality ;
equality   → comparison ( ( "!=" | "==" ) comparison )* ;
comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term       → factor ( ( "-" | "+" ) factor )* ;
factor     → unary ( ( "/" | "*" ) unary )* ;
unary      → ( "!" | "-" ) unary | primary ;
primary    → NUMBER | STRING | "true" | "false" | "nil"
           | "(" expression ")" ;
```

**우선순위 (낮음 → 높음):**

| 우선순위 | 연산자 | 규칙 |
|---------|--------|------|
| 1 (최저) | `==` `!=` | equality |
| 2 | `>` `>=` `<` `<=` | comparison |
| 3 | `+` `-` | term |
| 4 | `*` `/` | factor |
| 5 (최고) | `!` `-` (단항) | unary |

모든 이항 연산자는 **좌결합(left-associative)** 이다.

### 4.2 `Expr` sealed class

```kotlin
sealed class Expr {
    data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Literal(val value: Any?) : Expr()
    data class Unary(val op: Token, val right: Expr) : Expr()
}
```

- `Literal.value` 타입: `Double`(숫자), `String`(문자열), `Boolean`(true/false), `null`(nil)
- `Binary.op`, `Unary.op`: 연산자 토큰 (줄 번호 등 위치 정보 포함)

### 4.3 `Parser`

```kotlin
class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Expr?

    // 문법 규칙 — 각 메서드가 하나의 규칙에 대응
    private fun expression(): Expr
    private fun equality(): Expr
    private fun comparison(): Expr
    private fun term(): Expr
    private fun factor(): Expr
    private fun unary(): Expr
    private fun primary(): Expr

    // 유틸리티
    private fun match(vararg types: TokenType): Boolean
    private fun check(type: TokenType): Boolean
    private fun advance(): Token
    private fun isAtEnd(): Boolean
    private fun peek(): Token
    private fun previous(): Token
    private fun consume(type: TokenType, message: String): Token
    private fun error(token: Token, message: String): ParseError

    private class ParseError : RuntimeException()
}
```

### 4.4 파싱 알고리즘

```
parse():
    try:
        return expression()
    catch ParseError:
        return null

expression():
    return equality()

equality():
    expr = comparison()
    while match(BANG_EQUAL, EQUAL_EQUAL):
        op = previous()
        right = comparison()
        expr = Binary(expr, op, right)
    return expr

comparison():  // equality와 동일 패턴
term():        // 동일 패턴
factor():      // 동일 패턴

unary():
    if match(BANG, MINUS):
        op = previous()
        right = unary()  // 재귀 — 우결합
        return Unary(op, right)
    return primary()

primary():
    if match(FALSE)  -> Literal(false)
    if match(TRUE)   -> Literal(true)
    if match(NIL)    -> Literal(null)
    if match(NUMBER, STRING) -> Literal(previous().literal)
    if match(LEFT_PAREN):
        expr = expression()
        consume(RIGHT_PAREN, "Expect ')' after expression.")
        return Grouping(expr)
    throw error(peek(), "Expect expression.")
```

### 4.5 유틸리티 메서드 상세

- `match(vararg types)`: 현재 토큰이 `types` 중 하나이면 `advance()` 후 `true`
- `check(type)`: 현재 토큰이 `type`인지 확인 (소비하지 않음). EOF면 `false`
- `advance()`: 현재 토큰 반환 후 `current++`. EOF가 아닐 때만 증가
- `peek()`: `tokens[current]` 반환
- `previous()`: `tokens[current - 1]` 반환
- `consume(type, msg)`: `check(type)`이면 `advance()`, 아니면 `error(peek(), msg)` throw
- `isAtEnd()`: `peek().type == EOF`

### 4.6 에러 처리

`GwanLang.kt`에 토큰 기반 에러 메서드 추가:

```kotlin
fun error(token: Token, message: String) {
    if (token.type == TokenType.EOF) {
        report(token.line, " at end", message)
    } else {
        report(token.line, " at '${token.lexeme}'", message)
    }
}
```

- `primary()`에서 매칭 실패 시 `ParseError` throw
- `consume()`에서 기대 토큰 불일치 시 `ParseError` throw
- `parse()`에서 `ParseError` catch → `null` 반환
- Phase 2에서는 단일 표현식만 파싱하므로 `synchronize` 불필요

### 4.7 `AstPrinter`

S-expression(Lisp 스타일) 형식으로 AST를 문자열로 변환:

```kotlin
class AstPrinter {
    fun print(expr: Expr): String = when (expr) {
        is Expr.Binary   -> parenthesize(expr.op.lexeme, expr.left, expr.right)
        is Expr.Grouping -> parenthesize("group", expr.expression)
        is Expr.Literal  -> if (expr.value == null) "nil" else expr.value.toString()
        is Expr.Unary    -> parenthesize(expr.op.lexeme, expr.right)
    }

    private fun parenthesize(name: String, vararg exprs: Expr): String {
        val builder = StringBuilder()
        builder.append("(").append(name)
        for (expr in exprs) {
            builder.append(" ").append(print(expr))
        }
        builder.append(")")
        return builder.toString()
    }
}
```

예시:
- `1 + 2` → `(+ 1.0 2.0)`
- `-3` → `(- 3.0)`
- `(1 + 2) * 3` → `(* (group (+ 1.0 2.0)) 3.0)`

### 4.8 `GwanLang.kt` 변경

`run()` 함수에 Parser → AstPrinter 단계 추가:

```kotlin
private fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val expression = parser.parse()

    if (GwanLang.hadError) return
    println(AstPrinter().print(expression!!))
}
```

## 5. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | Expr 타입 정의 | Binary, Grouping, Literal, Unary 생성/필드 접근 | `Expr.kt` |
| 2 | Literal 파싱 | `123` → Literal(123.0), `"hi"` → Literal("hi"), `true`/`false`/`nil` | `Parser.primary()` |
| 3 | Unary 파싱 | `-1` → Unary(MINUS, Literal(1.0)), `!true` → Unary(BANG, Literal(true)) | `Parser.unary()` |
| 4 | Factor 파싱 | `2 * 3` → Binary, `6 / 2` → Binary | `Parser.factor()` |
| 5 | Term 파싱 | `1 + 2` → Binary, `5 - 3` → Binary | `Parser.term()` |
| 6 | Comparison 파싱 | `1 < 2`, `3 >= 3` → Binary | `Parser.comparison()` |
| 7 | Equality 파싱 | `1 == 1`, `"a" != "b"` → Binary | `Parser.equality()` |
| 8 | Grouping 파싱 | `(1 + 2)` → Grouping(Binary) | `Parser.primary()` 괄호 분기 |
| 9 | 우선순위/결합 | `1 + 2 * 3` → 올바른 트리, `1 - 2 - 3` → 좌결합 | 전체 파서 |
| 10 | 에러 처리 | 닫히지 않은 `(`, 예상치 못한 토큰 → 에러 리포팅 + null | `consume()`, `error()` |
| 11 | AstPrinter | `1 + 2` → `(+ 1.0 2.0)`, 중첩 표현식 → S-expression | `AstPrinter` |
| 12 | GwanLang 통합 | REPL 파이프라인 Scanner → Parser → AstPrinter 연결 | `GwanLang.run()` |

### 테스트 작성 규약
- 헬퍼: `fun parse(src: String): Expr?` — Scanner + Parser를 결합한 편의 함수
- 각 테스트는 AST 구조를 직접 패턴 매칭(`is Expr.Binary` 등)으로 검증
- AstPrinter 테스트는 문자열 비교로 검증

## 6. 완료 기준 (Definition of Done)

- [ ] `Expr` sealed class 4종 정의 및 테스트
- [ ] `Parser` 클래스 — 전체 표현식 문법 파싱
- [ ] 연산자 우선순위 올바른 AST 생성
- [ ] 좌결합 이항 연산자 확인
- [ ] 파싱 에러 시 줄 번호 + 토큰 위치 포함 에러 메시지
- [ ] `AstPrinter` S-expression 출력
- [ ] `GwanLang.kt` 파이프라인 연결 (REPL에서 표현식 → AST 출력)
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/parser-demo.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 2 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 7. 작업 분해 (권장 커밋 단위)

1. `feat: Expr sealed class 계층 정의 (Binary, Grouping, Literal, Unary)`
2. `feat: Parser 골격 및 리터럴 파싱`
3. `feat: 단항 연산자 파싱`
4. `feat: 이항 연산자 파싱 (factor, term, comparison, equality)`
5. `feat: 그룹화(괄호) 파싱`
6. `feat: 파싱 에러 처리 및 GwanLang.error(token) 추가`
7. `feat: AstPrinter S-expression 출력`
8. `feat: GwanLang 파이프라인에 Parser 연결`
9. `docs: Phase 2 완료 기록`
