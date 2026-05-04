# Phase 8: 연산자 확장 — 모듈로 & 복합 대입 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 8
> 참고: Crafting Interpreters — 기존 산술/대입 연산자 패턴 확장

## 1. 목적

Phase 7까지 GwanLang은 `+`, `-`, `*`, `/` 네 가지 산술 연산자와 `=` 대입만
지원한다. Phase 8에서는 나머지 연산자 `%`와 복합 대입 연산자 `+=`, `-=`, `*=`,
`/=`, `%=`를 추가하여 반복적인 산술 대입 패턴을 간결하게 표현할 수 있게 한다.

```gwan
// 모듈로 연산
print 10 % 3;     // 1
print 7.5 % 2.5;  // 0

// 복합 대입
var x = 10;
x += 5;   // x = 15
x -= 3;   // x = 12
x *= 2;   // x = 24
x /= 4;   // x = 6
x %= 4;   // x = 2
print x;   // 2

// 프로퍼티 복합 대입
class Counter {
  init() { this.count = 0; }
  increment(n) { this.count += n; }
}
var c = Counter();
c.increment(5);
print c.count;  // 5
```

파이프라인은 동일하게 유지된다:

```
소스코드 → Scanner → Parser → Resolver → Interpreter → 출력
```

## 2. 범위

### In Scope
- **TokenType 추가**: `PERCENT`, `PLUS_EQUAL`, `MINUS_EQUAL`, `STAR_EQUAL`, `SLASH_EQUAL`, `PERCENT_EQUAL` (6종)
- **Scanner 확장**: 6개 신규 토큰 인식
- **Parser 확장**:
  - `factor()` 규칙에 `PERCENT` 추가 (동일 우선순위: `*`, `/`, `%`)
  - `assignment()` 규칙에서 복합 대입 토큰을 디슈가링
- **Interpreter 확장**: `Expr.Binary`에 `PERCENT` 평가 추가 (0으로 나누기 에러 포함)
- 기존 Phase 1~7 테스트 회귀 없음

### Out of Scope (향후 확장 후보)
- 증감 연산자 (`++`, `--`)
- 비트 연산자 (`&`, `|`, `^`, `~`, `<<`, `>>`)
- 거듭제곱 연산자 (`**`)
- 삼항 연산자 (`? :`)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `TokenType.kt` | `PERCENT`, `PLUS_EQUAL`, `MINUS_EQUAL`, `STAR_EQUAL`, `SLASH_EQUAL`, `PERCENT_EQUAL` 추가 (수정) |
| `Scanner.kt` | 6개 토큰 스캔 로직 추가 (수정) |
| `Parser.kt` | `factor()`에 `PERCENT` 추가, `assignment()`에 복합 대입 디슈가링 추가 (수정) |
| `Interpreter.kt` | `Expr.Binary`에 `PERCENT` 평가 추가 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `OperatorTest.kt` | Phase 8 전용 테스트 (신규) |

### 기타
- `examples/operators.gwan` — 연산자 확장 시연 예제
- `docs/CHANGELOG.md` — Phase 8 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 TokenType 추가

```kotlin
enum class TokenType {
    // 기존 단일 문자 (11종)
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // 1~2 문자 (8종 → 14종)
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    PERCENT,                                              // 신규
    PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL,     // 신규
    PERCENT_EQUAL,                                        // 신규

    // 리터럴 (3종)
    IDENTIFIER, STRING, NUMBER,

    // 키워드 (16종)
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF,
}
```

### 4.2 Scanner 확장

기존 `!`, `=`, `>`, `<` 패턴과 동일하게, `=`가 뒤따르는지 `match('=')`로 확인한다.

```kotlin
// 변경 전
c == '-' -> addToken(TokenType.MINUS)
c == '+' -> addToken(TokenType.PLUS)
c == '*' -> addToken(TokenType.STAR)

// 변경 후
c == '-' -> addToken(if (match('=')) TokenType.MINUS_EQUAL else TokenType.MINUS)
c == '+' -> addToken(if (match('=')) TokenType.PLUS_EQUAL else TokenType.PLUS)
c == '*' -> addToken(if (match('=')) TokenType.STAR_EQUAL else TokenType.STAR)
c == '%' -> addToken(if (match('=')) TokenType.PERCENT_EQUAL else TokenType.PERCENT)
```

`/` 는 기존 `slashOrComment()`와 통합해야 한다:

```kotlin
c == '/' -> {
    if (match('/')) {
        // 단일 라인 주석
        while (peek() != '\n' && !isAtEnd()) advance()
    } else if (match('=')) {
        addToken(TokenType.SLASH_EQUAL)
    } else {
        addToken(TokenType.SLASH)
    }
}
```

현재 `slashOrComment()` 메서드는 인라인하여 삼분기(주석/`/=`/`/`)로 통합한다.

### 4.3 Parser 확장

#### 4.3.1 `factor()` — `%` 추가

`%`는 `*`, `/`와 동일한 우선순위(factor)에 속한다.

```kotlin
private fun factor(): Expr {
    var expr = unary()
    while (match(TokenType.SLASH, TokenType.STAR, TokenType.PERCENT)) {  // PERCENT 추가
        val op = previous()
        val right = unary()
        expr = Expr.Binary(expr, op, right)
    }
    return expr
}
```

#### 4.3.2 `assignment()` — 복합 대입 디슈가링

복합 대입 토큰을 만나면 기존 `Expr.Assign` / `Expr.Set`으로 변환한다.
새 AST 노드 없이 Parser에서 디슈가링하는 방식이다 (기존 `forStatement()`의
while 디슈가링과 동일한 패턴).

**복합 대입 → 산술 연산자 매핑:**

| 복합 대입 토큰 | 산술 연산자 토큰 |
|---------------|----------------|
| `PLUS_EQUAL` | `PLUS` |
| `MINUS_EQUAL` | `MINUS` |
| `STAR_EQUAL` | `STAR` |
| `SLASH_EQUAL` | `SLASH` |
| `PERCENT_EQUAL` | `PERCENT` |

```kotlin
private fun assignment(): Expr {
    val expr = or()

    if (match(TokenType.EQUAL)) {
        val equals = previous()
        val value = assignment()

        if (expr is Expr.Variable) {
            return Expr.Assign(expr.name, value)
        } else if (expr is Expr.Get) {
            return Expr.Set(expr.obj, expr.name, value)
        }

        throw error(equals, "Invalid assignment target.")
    }

    // 복합 대입 처리
    if (match(TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
              TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL,
              TokenType.PERCENT_EQUAL)) {
        val op = previous()
        val value = assignment()
        val binaryOp = compoundToOperator(op)

        if (expr is Expr.Variable) {
            // x += 1  →  x = x + 1
            val binary = Expr.Binary(Expr.Variable(expr.name), binaryOp, value)
            return Expr.Assign(expr.name, binary)
        } else if (expr is Expr.Get) {
            // obj.field += 1  →  obj.field = obj.field + 1
            val binary = Expr.Binary(Expr.Get(expr.obj, expr.name), binaryOp, value)
            return Expr.Set(expr.obj, expr.name, binary)
        }

        throw error(op, "Invalid assignment target.")
    }

    return expr
}
```

**`compoundToOperator()` 헬퍼:**

복합 대입 토큰에서 대응하는 산술 연산자 토큰을 생성한다. 토큰의 `line` 정보를
유지하여 런타임 에러 메시지에서 올바른 줄 번호가 출력되도록 한다.

```kotlin
private fun compoundToOperator(compoundOp: Token): Token {
    val type = when (compoundOp.type) {
        TokenType.PLUS_EQUAL    -> TokenType.PLUS
        TokenType.MINUS_EQUAL   -> TokenType.MINUS
        TokenType.STAR_EQUAL    -> TokenType.STAR
        TokenType.SLASH_EQUAL   -> TokenType.SLASH
        TokenType.PERCENT_EQUAL -> TokenType.PERCENT
        else -> throw IllegalStateException("Not a compound assignment operator: ${compoundOp.type}")
    }
    val lexeme = when (type) {
        TokenType.PLUS    -> "+"
        TokenType.MINUS   -> "-"
        TokenType.STAR    -> "*"
        TokenType.SLASH   -> "/"
        TokenType.PERCENT -> "%"
        else -> throw IllegalStateException()
    }
    return Token(type, lexeme, null, compoundOp.line)
}
```

**디슈가링 결과 (변수):**

```
x += 1
↓
Expr.Assign(
  name = x,
  value = Expr.Binary(
    left = Expr.Variable(x),
    op = Token(PLUS, "+"),
    right = Expr.Literal(1.0)
  )
)
```

**디슈가링 결과 (프로퍼티):**

```
obj.field += 1
↓
Expr.Set(
  obj = obj,          // 동일한 Expr 노드 공유
  name = "field",
  value = Expr.Binary(
    left = Expr.Get(obj, "field"),    // obj 표현식이 새 Get 노드에 복제됨
    op = Token(PLUS, "+"),
    right = Expr.Literal(1.0)
  )
)
```

**참고:** 프로퍼티 디슈가링에서 `expr.obj`가 `Expr.Get`의 `obj`와 `Expr.Set`의
`obj`에 모두 사용되므로 `obj` 표현식이 2회 평가된다. 이 프로젝트 수준에서는
실질적 문제가 없으므로 허용한다.

### 4.4 Interpreter 확장 — `%` 평가

`Expr.Binary`의 `when`에 `PERCENT` 케이스를 추가한다:

```kotlin
TokenType.PERCENT -> {
    checkNumberOperands(expr.op, left, right)
    val leftNum = left as Double
    val rightNum = right as Double
    if (rightNum == 0.0) {
        throw RuntimeError(expr.op, "Modulo by zero.")
    }
    leftNum % rightNum
}
```

Kotlin의 `%` 연산자(`rem`)를 사용하며, 결과의 부호는 피제수(왼쪽)를 따른다:
- `7 % 3` → `1.0`
- `-7 % 3` → `-1.0`
- `7 % -3` → `1.0`

### 4.5 Resolver — 변경 없음

복합 대입이 Parser에서 기존 AST 노드(`Expr.Assign`, `Expr.Set`, `Expr.Binary`,
`Expr.Variable`, `Expr.Get`)로 디슈가링되므로, Resolver는 수정 없이 기존 로직을
그대로 사용한다.

## 5. 에러 처리 방침

| 상황 | 단계 | 메시지 |
|------|------|--------|
| `10 % 0` | Interpreter | RuntimeError: "Modulo by zero." |
| `"a" % 2` | Interpreter | RuntimeError: "Operands must be numbers." |
| `10 / 0` | Interpreter | RuntimeError: "Division by zero." (기존) |
| `"a" += 1` (비대입 대상) | Parser | ParseError: "Invalid assignment target." |
| 복합 대입의 산술 부분 타입 에러 | Interpreter | 기존 Binary 에러 메시지 그대로 |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | Scanner — `%` 토큰 | `10 % 3` → `[NUMBER, PERCENT, NUMBER, ...]` | TokenType.PERCENT, Scanner `%` |
| 2 | Scanner — `%=` 토큰 | `x %= 3` → `[IDENTIFIER, PERCENT_EQUAL, NUMBER, ...]` | TokenType.PERCENT_EQUAL, Scanner `%=` |
| 3 | Scanner — `+=`, `-=` 토큰 | `x += 1; y -= 2;` → 올바른 토큰열 | PLUS_EQUAL, MINUS_EQUAL |
| 4 | Scanner — `*=`, `/=` 토큰 | `x *= 2; y /= 3;` → 올바른 토큰열 | STAR_EQUAL, SLASH_EQUAL |
| 5 | Scanner — `/=` vs `//` 구분 | `x /= 2; // comment` → `/=` 토큰 + 주석 무시 | slashOrComment 리팩토링 |
| 6 | Parser — `%` 우선순위 | `1 + 2 % 3` → `Binary(1, +, Binary(2, %, 3))` | factor()에 PERCENT 추가 |
| 7 | Parser — 변수 복합 대입 | `x += 1` → `Assign(x, Binary(Variable(x), +, 1))` | assignment() 디슈가링 |
| 8 | Parser — 프로퍼티 복합 대입 | `obj.f += 1` → `Set(obj, f, Binary(Get(obj, f), +, 1))` | assignment() 프로퍼티 디슈가링 |
| 9 | Parser — 잘못된 복합 대입 대상 | `1 += 2` → ParseError | 에러 처리 |
| 10 | Interpreter — `%` 정수 | `print 10 % 3;` → "1" | Binary PERCENT 평가 |
| 11 | Interpreter — `%` 실수 | `print 7.5 % 2.5;` → "0" | 실수 모듈로 |
| 12 | Interpreter — `%` 음수 | `print -7 % 3;` → "-1" | 음수 모듈로 부호 |
| 13 | Interpreter — `%` 0으로 나누기 | `print 10 % 0;` → RuntimeError | 0 검사 |
| 14 | Interpreter — `%` 타입 에러 | `print "a" % 2;` → RuntimeError | checkNumberOperands |
| 15 | Interpreter — `+=` 변수 | `var x = 10; x += 5; print x;` → "15" | 디슈가링 + 기존 Assign |
| 16 | Interpreter — `-=`, `*=`, `/=`, `%=` 변수 | 각 연산자별 변수 복합 대입 검증 | 각 연산자 디슈가링 |
| 17 | Interpreter — 프로퍼티 복합 대입 | `class C { init() { this.v = 0; } } var c = C(); c.v += 5; print c.v;` → "5" | Set 디슈가링 |
| 18 | Interpreter — 연쇄 복합 대입 | `var x = 1; x += 2; x *= 3; print x;` → "9" | 연속 복합 대입 |
| 19 | Interpreter — 복합 대입 + 표현식 | `var x = 10; x += 2 * 3; print x;` → "16" | 우변 표현식 평가 |
| 20 | 기존 테스트 회귀 검증 | `./gradlew test` 전체 통과 | 전체 |

### 테스트 작성 규약
- `OperatorTest.kt`: Phase 8 전용 테스트. Scanner/Parser/Interpreter 결과를 모두 포함.
- stdout 캡처: `System.setOut()`으로 출력 검증
- 에러 검증: `GwanLang.hadError` / `GwanLang.hadRuntimeError` 플래그 확인
- 기존 테스트(`ScannerTest`, `ParserTest`, `InterpreterTest`, `ClassTest`)가 매 사이클마다 통과 확인

## 7. 완료 기준 (Definition of Done)

- [ ] `TokenType` — `PERCENT`, `PLUS_EQUAL`, `MINUS_EQUAL`, `STAR_EQUAL`, `SLASH_EQUAL`, `PERCENT_EQUAL` 추가
- [ ] `Scanner` — 6개 신규 토큰 스캔
- [ ] `Scanner` — `/=` vs `//` 주석 올바르게 구분
- [ ] `Parser` — `factor()`에서 `%` 파싱 (`*`, `/`와 동일 우선순위)
- [ ] `Parser` — 복합 대입 디슈가링 (변수 대상)
- [ ] `Parser` — 복합 대입 디슈가링 (프로퍼티 대상)
- [ ] `Parser` — 잘못된 복합 대입 대상 에러
- [ ] `Interpreter` — `%` 산술 평가
- [ ] `Interpreter` — `%` 0으로 나누기 RuntimeError
- [ ] `Interpreter` — `%` 타입 에러 (피연산자가 숫자가 아닐 때)
- [ ] Resolver 변경 없음 (디슈가링으로 기존 노드 재활용)
- [ ] 기존 Phase 1~7 테스트 전체 통과 (회귀 없음)
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/operators.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 8 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 8 연산자 확장 스펙 문서 작성`
2. `feat: TokenType에 PERCENT 및 복합 대입 토큰 추가`
3. `feat: Scanner에서 %, %=, +=, -=, *=, /= 토큰 스캔`
4. `feat: Parser factor()에 % 연산자 추가`
5. `feat: Interpreter에서 % 산술 평가 및 0 나누기 에러`
6. `feat: Parser assignment()에서 복합 대입 디슈가링`
7. `test: 연산자 확장 통합 테스트`
8. `docs: Phase 8 완료 기록`
