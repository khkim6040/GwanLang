# Phase 9: break / continue — 루프 제어흐름 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 9
> 참고: Crafting Interpreters — `Return` 예외 기반 제어흐름 패턴 재활용

## 1. 목적

Phase 8까지 GwanLang의 루프(`while`, `for`)는 조건이 거짓이 될 때까지 반복하는
것 외에 흐름을 제어할 방법이 없다. Phase 9에서는 `break`와 `continue` 키워드를
추가하여 루프 내부에서 즉시 탈출하거나 다음 반복으로 건너뛸 수 있게 한다.

```gwan
// break — 루프 즉시 종료
var i = 0;
while (true) {
  if (i >= 5) break;
  print i;
  i += 1;
}
// 출력: 0 1 2 3 4

// continue — 현재 반복 건너뛰기
for (var i = 0; i < 10; i += 1) {
  if (i % 2 == 0) continue;
  print i;
}
// 출력: 1 3 5 7 9
```

파이프라인은 동일하게 유지된다:

```
소스코드 → Scanner → Parser → Resolver → Interpreter → 출력
```

### 주요 설계 변경: `for` 디슈가링 제거

기존에 `for`는 Parser에서 `Stmt.While`로 디슈가링되었다. 그러나 `continue`가
예외로 구현되면 increment 표현식까지 건너뛰어 무한 루프가 발생한다.

```
// 기존 디슈가링 문제
for (var i = 0; i < 10; i += 1) { continue; }
↓
while (i < 10) {
  { continue; }   // Continue 예외 발생
  i += 1;         // ← 건너뜀! 무한 루프
}
```

이를 해결하기 위해 `Stmt.For` AST 노드를 도입한다. Interpreter에서
`Continue` catch 후에도 increment를 항상 실행하여 올바른 시맨틱을 보장한다.

## 2. 범위

### In Scope
- **키워드 추가**: `break`, `continue` (2종)
- **AST 노드 추가**: `Stmt.Break`, `Stmt.Continue`, `Stmt.For` (3종)
- **예외 클래스 추가**: `Break`, `Continue` (`Return` 패턴 재활용)
- **Scanner 확장**: 키워드 맵에 `"break"`, `"continue"` 등록
- **Parser 확장**: `breakStatement()`, `continueStatement()` 파싱 + `forStatement()` 디슈가링 제거 → `Stmt.For` 반환
- **Resolver 확장**: `currentLoop` 상태 추적, 루프 밖 break/continue 정적 에러 검출, 함수 경계에서 루프 상태 리셋
- **Interpreter 확장**: `Stmt.While`/`Stmt.For`에서 Break/Continue catch, `Stmt.For` 직접 실행
- 기존 Phase 1~8 테스트 회귀 없음

### Out of Scope (향후 확장 후보)
- 라벨 break/continue (`break outer;`)
- `break` with value (Rust 스타일)
- `do-while` 루프

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `TokenType.kt` | `BREAK`, `CONTINUE` 키워드 추가 (수정) |
| `Scanner.kt` | 키워드 맵에 `"break"`, `"continue"` 추가 (수정) |
| `Stmt.kt` | `Stmt.Break`, `Stmt.Continue`, `Stmt.For` 추가 (수정) |
| `Parser.kt` | break/continue 파싱, `forStatement()` → `Stmt.For` 반환 (수정) |
| `Break.kt` | `Break` 예외 클래스 (신규) |
| `Continue.kt` | `Continue` 예외 클래스 (신규) |
| `Resolver.kt` | `currentLoop` 추적, break/continue/for 처리 (수정) |
| `Interpreter.kt` | Break/Continue catch, `Stmt.For` 실행 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `LoopControlTest.kt` | Phase 9 전용 테스트 (신규) |

### 기타
- `examples/loop_control.gwan` — break/continue 시연 예제
- `docs/CHANGELOG.md` — Phase 9 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 TokenType 추가

```kotlin
enum class TokenType {
    // ... 기존 토큰 ...

    // 키워드 (16종 → 18종)
    AND, BREAK, CLASS, CONTINUE, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF,
}
```

### 4.2 Scanner 확장

키워드 맵에 2개 추가:

```kotlin
private val keywords = mapOf(
    // ... 기존 키워드 ...
    "break" to TokenType.BREAK,
    "continue" to TokenType.CONTINUE,
)
```

### 4.3 예외 클래스

`Return` 패턴과 동일. `RuntimeException(null, null, true, false)`로 스택 트레이스
생성을 억제하여 성능을 유지한다. 값을 전달하지 않으므로 더 단순하다.

```kotlin
// Break.kt
package gwanlang
class Break : RuntimeException(null, null, true, false)

// Continue.kt
package gwanlang
class Continue : RuntimeException(null, null, true, false)
```

### 4.4 AST 노드 추가

```kotlin
sealed class Stmt {
    // ... 기존 노드 ...
    data class Break(val keyword: Token) : Stmt()
    data class Continue(val keyword: Token) : Stmt()
    data class For(
        val initializer: Stmt?,
        val condition: Expr?,
        val increment: Expr?,
        val body: Stmt
    ) : Stmt()
}
```

`keyword` 필드는 에러 메시지의 줄 번호 추적용이다.

`Stmt.For`의 `condition`이 `null`이면 `true`로 간주한다 (`for (;;)` = 무한 루프).

### 4.5 Parser 확장

#### 4.5.1 `statement()` — break/continue 분기 추가

```kotlin
private fun statement(): Stmt {
    if (match(TokenType.BREAK)) return breakStatement()
    if (match(TokenType.CONTINUE)) return continueStatement()
    if (match(TokenType.PRINT)) return printStatement()
    // ... 나머지 ...
}
```

#### 4.5.2 `breakStatement()` / `continueStatement()`

```kotlin
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

#### 4.5.3 `forStatement()` — 디슈가링 제거, `Stmt.For` 반환

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

#### 4.5.4 `synchronize()` — break/continue 키워드 추가

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

### 4.6 Resolver 확장

#### 4.6.1 `currentLoop` 상태

```kotlin
class Resolver(private val interpreter: Interpreter) {
    // ... 기존 필드 ...
    private var currentLoop = 0
```

#### 4.6.2 루프 문 처리

```kotlin
is Stmt.While -> {
    resolve(stmt.condition)
    currentLoop++
    resolve(stmt.body)
    currentLoop--
}

is Stmt.For -> {
    if (stmt.initializer != null) resolve(stmt.initializer)
    if (stmt.condition != null) resolve(stmt.condition)
    if (stmt.increment != null) resolve(stmt.increment)
    currentLoop++
    resolve(stmt.body)
    currentLoop--
}
```

`Stmt.For`의 initializer는 루프 스코프 바깥에서 resolve된다.
initializer에 `var`가 포함될 경우, `Stmt.Var`의 기존 처리가 동작한다.
단, `Stmt.For`의 initializer에 선언된 변수는 body에서 접근 가능해야 하므로,
Resolver에서는 별도 스코프를 만들지 않는다 (Interpreter에서 환경으로 처리).

**정정**: Resolver에서 `Stmt.For`는 initializer에 선언된 변수가 condition,
increment, body 모두에서 접근 가능해야 한다. Resolver는 렉시컬 스코프만
추적하므로 `Stmt.For` 전체를 하나의 스코프로 감싸야 한다:

```kotlin
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
```

#### 4.6.3 break/continue 검증

```kotlin
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

#### 4.6.4 함수 경계에서 루프 상태 리셋

함수 안에서 바깥 루프의 `break`/`continue`를 사용할 수 없도록 한다:

```kotlin
private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
    val enclosingFunction = currentFunction
    val enclosingLoop = currentLoop
    currentFunction = type
    currentLoop = 0  // 함수 경계에서 리셋

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

### 4.7 Interpreter 확장

#### 4.7.1 `Stmt.While` — Break/Continue catch

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

#### 4.7.2 `Stmt.For` — 직접 실행

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

**환경 관리**: `Stmt.For`는 initializer의 변수 스코프를 위해 항상 새 환경을 생성한다.
initializer가 없더라도 일관성을 위해 새 환경을 생성하며, 이는 기존 `for` 디슈가링의
`Stmt.Block` 래핑과 동일한 효과다.

#### 4.7.3 `Stmt.Break` / `Stmt.Continue` — 예외 throw

```kotlin
is Stmt.Break -> throw Break()
is Stmt.Continue -> throw Continue()
```

#### 4.7.4 `interpret()` — 최상위 Break/Continue catch

Resolver가 정적으로 검출하지만, 안전을 위해 최상위에서도 catch한다:

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

## 5. 에러 처리 방침

| 상황 | 단계 | 메시지 |
|------|------|--------|
| 루프 밖 `break;` | Resolver (정적) | `"Can't use 'break' outside of a loop."` |
| 루프 밖 `continue;` | Resolver (정적) | `"Can't use 'continue' outside of a loop."` |
| 함수 안에서 바깥 루프 `break` | Resolver (정적) | `"Can't use 'break' outside of a loop."` |
| `break` 뒤 세미콜론 누락 | Parser | `"Expect ';' after 'break'."` |
| `continue` 뒤 세미콜론 누락 | Parser | `"Expect ';' after 'continue'."` |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | Scanner — break/continue 키워드 | `break;` → `[BREAK, SEMICOLON, EOF]` | TokenType, Scanner 키워드 맵 |
| 2 | Parser — break 문 파싱 | `break;` → `Stmt.Break` | Parser `breakStatement()` |
| 3 | Parser — continue 문 파싱 | `continue;` → `Stmt.Continue` | Parser `continueStatement()` |
| 4 | Parser — for 문 Stmt.For 반환 | `for (var i = 0; i < 10; i += 1) {}` → `Stmt.For` | Parser `forStatement()` 변경 |
| 5 | Interpreter — while + break | `var i=0; while(true){if(i>=3)break; print i; i+=1;}` → "0\n1\n2" | Break 예외, While catch |
| 6 | Interpreter — while + continue | `var i=0; while(i<5){i+=1; if(i%2==0)continue; print i;}` → "1\n3\n5" | Continue 예외, While catch |
| 7 | Interpreter — for + break | `for(var i=0;i<10;i+=1){if(i>=3)break; print i;}` → "0\n1\n2" | Stmt.For 실행, Break catch |
| 8 | Interpreter — for + continue | `for(var i=0;i<10;i+=1){if(i%2==0)continue; print i;}` → "1\n3\n5\n7\n9" | Continue catch 후 increment 실행 |
| 9 | Interpreter — 중첩 루프 + break | 내부 루프만 탈출, 외부 루프 계속 | 가장 가까운 루프만 catch |
| 10 | Interpreter — 중첩 루프 + continue | 내부 루프만 건너뛰기 | 가장 가까운 루프만 catch |
| 11 | Interpreter — for 스코프 | `for(var i=0;i<3;i+=1){} print i;` → 런타임 에러 | For 환경 스코프 |
| 12 | Resolver — 루프 밖 break 에러 | `break;` → 정적 에러 | Resolver `currentLoop` 검증 |
| 13 | Resolver — 루프 밖 continue 에러 | `continue;` → 정적 에러 | Resolver `currentLoop` 검증 |
| 14 | Resolver — 함수 안에서 바깥 루프 break 에러 | `while(true){fun f(){break;} f();}` → 정적 에러 | `resolveFunction` 루프 리셋 |
| 15 | 기존 for 루프 회귀 | Phase 4의 for 루프 테스트가 모두 통과 | Stmt.For 실행 호환성 |
| 16 | 기존 테스트 전체 회귀 | `./gradlew test` 전체 통과 | 전체 |

### 테스트 작성 규약
- `LoopControlTest.kt`: Phase 9 전용 테스트. Scanner/Parser/Interpreter/Resolver 결과를 모두 포함.
- stdout 캡처: `System.setOut()`으로 출력 검증
- 에러 검증: `GwanLang.hadError` / `GwanLang.hadRuntimeError` 플래그 확인
- 기존 테스트(`ScannerTest`, `ParserTest`, `InterpreterTest`, `ClassTest`, `OperatorTest`)가 매 사이클마다 통과 확인

## 7. 완료 기준 (Definition of Done)

- [ ] `TokenType` — `BREAK`, `CONTINUE` 키워드 추가
- [ ] `Scanner` — 키워드 맵에 `"break"`, `"continue"` 등록
- [ ] `Stmt` — `Stmt.Break`, `Stmt.Continue`, `Stmt.For` 추가
- [ ] `Break.kt` / `Continue.kt` — 예외 클래스 생성
- [ ] `Parser` — `breakStatement()`, `continueStatement()` 파싱
- [ ] `Parser` — `forStatement()` → `Stmt.For` 반환 (디슈가링 제거)
- [ ] `Parser` — `synchronize()`에 break/continue 키워드 추가
- [ ] `Resolver` — `currentLoop` 추적, 루프 밖 break/continue 정적 에러
- [ ] `Resolver` — `resolveFunction()`에서 `currentLoop` 리셋
- [ ] `Resolver` — `Stmt.For` 처리 (스코프 + condition/increment resolve)
- [ ] `Interpreter` — `Stmt.While`에서 Break/Continue catch
- [ ] `Interpreter` — `Stmt.For` 직접 실행 (환경 관리 + Break/Continue catch)
- [ ] `Interpreter` — `Stmt.Break`/`Stmt.Continue` → 예외 throw
- [ ] `Interpreter` — `interpret()`에서 최상위 Break/Continue 안전 catch
- [ ] 기존 `for` 루프 테스트 회귀 없음 (Stmt.For 전환 호환)
- [ ] 기존 Phase 1~8 테스트 전체 통과
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/loop_control.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 9 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 9 break/continue 스펙 문서 작성`
2. `feat: TokenType에 BREAK, CONTINUE 키워드 추가`
3. `feat: Scanner에서 break, continue 키워드 스캔`
4. `feat: Stmt.Break, Stmt.Continue, Stmt.For AST 노드 추가`
5. `feat: Break, Continue 예외 클래스 추가`
6. `feat: Parser에서 break, continue 문 파싱`
7. `refactor: forStatement() 디슈가링 제거, Stmt.For 반환`
8. `feat: Resolver에서 루프 밖 break/continue 정적 에러 검출`
9. `feat: Interpreter에서 while/for의 break/continue 처리`
10. `test: Phase 9 루프 제어흐름 테스트 추가`
11. `docs: Phase 9 완료 기록`
