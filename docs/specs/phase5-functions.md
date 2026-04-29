# Phase 5: Functions & Closures (함수와 클로저) 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 5
> 참고: Crafting Interpreters Ch. 10 "Functions", Ch. 11 일부 (클로저)

## 1. 목적

Phase 4까지 구현된 문장 실행기를 확장하여, **함수 선언·호출**, **매개변수 바인딩**,
**return 문**, **클로저(환경 캡처)**, **네이티브 함수**를 TDD로 구현한다.

```
fun greet(name) {
  print "Hello, " + name + "!";
}
greet("GwanLang"); // Hello, GwanLang!

fun makeCounter() {
  var count = 0;
  fun increment() {
    count = count + 1;
    return count;
  }
  return increment;
}

var counter = makeCounter();
print counter(); // 1
print counter(); // 2
```

Phase 5가 끝나면 GwanLang은 일급 함수와 클로저를 갖춘 스크립트 언어로 동작한다.

## 2. 범위

### In Scope
- `Expr.Call` 노드 추가 — 함수 호출 표현식
- `Stmt.Function` 노드 추가 — 함수 선언문
- `Stmt.Return` 노드 추가 — return 문
- `GwanCallable` 인터페이스 — 호출 가능 객체 추상화
- `GwanFunction` 클래스 — 사용자 정의 함수 런타임 객체
- `Return` 예외 클래스 — return 제어 흐름
- `Parser` 확장 — `fun` 선언, call 표현식 (`()`), `return` 문
- `Interpreter` 확장 — 함수 호출, 매개변수 바인딩, 클로저, Return 예외 처리
- 네이티브 함수: `clock()` (현재 시각 초 단위 반환)
- 인자 개수(arity) 검증

### Out of Scope (이후 Phase)
- `Resolver` 정적 변수 바인딩 (Phase 6)
- 클래스, `this`, `super`, `init()` (Phase 7)
- `Expr.Get`, `Expr.Set` 프로퍼티 접근 (Phase 7)
- 메서드 바인딩, `this` 바인딩 (Phase 7)
- 람다/익명 함수 (미정)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Expr.kt` | `Call` 서브타입 추가 (수정) |
| `Stmt.kt` | `Function`, `Return` 서브타입 추가 (수정) |
| `GwanCallable.kt` | `GwanCallable` 인터페이스 (신규) |
| `GwanFunction.kt` | 사용자 정의 함수 런타임 객체 (신규) |
| `Return.kt` | `Return` 예외 클래스 (신규) |
| `Parser.kt` | `fun` 선언, call 표현식, `return` 문 파싱 (수정) |
| `Interpreter.kt` | 함수 호출, 네이티브 함수 등록 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `FunctionParserTest.kt` | 함수 관련 파싱 테스트 (신규) |
| `InterpreterTest.kt` | 함수 호출·클로저 실행 테스트 추가 (수정) |

### 기타
- `examples/functions-demo.gwan` — 함수, 클로저, 재귀 시연 예제
- `docs/CHANGELOG.md` — Phase 5 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 AST 노드 추가

#### `Expr.Call`

```kotlin
data class Call(
    val callee: Expr,        // 호출 대상 표현식 (보통 Variable)
    val paren: Token,        // 닫는 괄호 — 에러 위치 보고용
    val arguments: List<Expr> // 인자 표현식 리스트
) : Expr()
```

#### `Stmt.Function`

```kotlin
data class Function(
    val name: Token,           // 함수 이름
    val params: List<Token>,   // 매개변수 이름 리스트
    val body: List<Stmt>       // 함수 본문 (블록 내부 문장 리스트)
) : Stmt()
```

#### `Stmt.Return`

```kotlin
data class Return(
    val keyword: Token,     // "return" 토큰 — 에러 위치 보고용
    val value: Expr?        // 반환 표현식 (없으면 nil 반환)
) : Stmt()
```

### 4.2 `GwanCallable` 인터페이스

호출 가능한 모든 객체(함수, 네이티브 함수, 이후 클래스)의 공통 인터페이스.

```kotlin
interface GwanCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}
```

### 4.3 `GwanFunction` 클래스

사용자 정의 함수의 런타임 표현. 선언 시점의 환경을 캡처하여 클로저를 구현한다.

```kotlin
class GwanFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment     // 선언 시점의 환경 (클로저)
) : GwanCallable {

    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            return returnValue.value
        }
        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}
```

**핵심 동작:**
- `call()` 시 클로저 환경을 `enclosing`으로 하는 새 환경을 만든다.
- 매개변수를 새 환경에 `define()`한다.
- `executeBlock()`으로 본문을 실행한다.
- `Return` 예외를 catch하여 반환 값을 꺼낸다.
- `return` 없이 함수가 끝나면 `nil`을 반환한다.

### 4.4 `Return` 예외 클래스

`return` 문의 제어 흐름을 구현하기 위한 예외. `RuntimeException`을 상속하되
`RuntimeError`와는 별개이다 (에러가 아니라 제어 흐름).

```kotlin
class Return(val value: Any?) : RuntimeException(null, null, true, false)
```

`enableSuppression=true, writableStackTrace=false`로 스택 트레이스를 생략하여
성능 오버헤드를 최소화한다.

### 4.5 Parser 확장

#### 문법 규칙 변경

```
declaration → funDecl | varDecl | statement ;
funDecl     → "fun" function ;
function    → IDENTIFIER "(" parameters? ")" block ;
parameters  → IDENTIFIER ( "," IDENTIFIER )* ;
statement   → ... | returnStmt ;
returnStmt  → "return" expression? ";" ;
```

호출 표현식은 `unary`와 `primary` 사이에 `call` 규칙을 삽입한다:

```
unary → ( "!" | "-" ) unary | call ;
call  → primary ( "(" arguments? ")" )* ;
arguments → expression ( "," expression )* ;
```

#### `declaration()` 확장

```kotlin
private fun declaration(): Stmt? {
    return try {
        if (match(TokenType.FUN)) funDeclaration("function")
        else if (match(TokenType.VAR)) varDeclaration()
        else statement()
    } catch (e: ParseError) {
        synchronize()
        null
    }
}
```

#### `funDeclaration()` — 함수 선언 파싱

```
funDeclaration(kind: String):
    name = consume(IDENTIFIER, "Expect $kind name.")
    consume(LEFT_PAREN, "Expect '(' after $kind name.")
    params = []
    if (!check(RIGHT_PAREN)):
        do:
            if (params.size >= 255):
                error(peek(), "Can't have more than 255 parameters.")
            params.add(consume(IDENTIFIER, "Expect parameter name."))
        while match(COMMA)
    consume(RIGHT_PAREN, "Expect ')' after parameters.")
    consume(LEFT_BRACE, "Expect '{' before $kind body.")
    body = block()
    return Stmt.Function(name, params, body)
```

`kind` 매개변수는 "function"을 받는다. Phase 7에서 "method"로 재사용한다.

#### `call()` — 호출 표현식 파싱

```
call():
    expr = primary()
    while true:
        if match(LEFT_PAREN):
            expr = finishCall(expr)
        else:
            break
    return expr

finishCall(callee: Expr):
    arguments = []
    if (!check(RIGHT_PAREN)):
        do:
            if (arguments.size >= 255):
                error(peek(), "Can't have more than 255 arguments.")
            arguments.add(expression())
        while match(COMMA)
    paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")
    return Expr.Call(callee, paren, arguments)
```

호출은 좌결합으로 연쇄 가능하다: `getCallback()()`.

#### `returnStatement()` — return 문 파싱

```
returnStatement():
    keyword = previous()  // "return" 토큰
    value = if (!check(SEMICOLON)) expression() else null
    consume(SEMICOLON, "Expect ';' after return value.")
    return Stmt.Return(keyword, value)
```

### 4.6 Interpreter 확장

#### 네이티브 함수 등록

`Interpreter` 생성자에서 전역 환경에 네이티브 함수를 등록한다.

```kotlin
class Interpreter {
    private val globals = Environment()
    private var environment = globals

    init {
        globals.define("clock", object : GwanCallable {
            override fun arity(): Int = 0
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return System.currentTimeMillis().toDouble() / 1000.0
            }
            override fun toString(): String = "<native fn>"
        })
    }
    // ...
}
```

#### `execute()` 확장 — Function, Return 처리

```
execute(stmt):
    ...기존 분기...
    Function →
        val function = GwanFunction(stmt, environment)  // 현재 환경을 클로저로 캡처
        environment.define(stmt.name.lexeme, function)
    Return →
        val value = if (stmt.value != null) evaluate(stmt.value) else null
        throw Return(value)
```

#### `evaluate()` 확장 — Call 처리

```
evaluate(expr):
    ...기존 분기...
    Call →
        val callee = evaluate(expr.callee)
        val arguments = expr.arguments.map { evaluate(it) }

        if (callee !is GwanCallable) {
            throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }

        if (arguments.size != callee.arity()) {
            throw RuntimeError(expr.paren,
                "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
```

**호출 순서:**
1. callee 표현식을 먼저 평가한다.
2. 인자를 왼쪽에서 오른쪽으로 평가한다.
3. callee가 `GwanCallable`인지 검증한다.
4. 인자 개수가 arity와 일치하는지 검증한다.
5. `call()`을 호출한다.

## 5. 에러 처리 방침

| 상황 | 동작 |
|------|------|
| 함수가 아닌 값 호출 (`"str"()`) | RuntimeError: "Can only call functions and classes." |
| 인자 개수 불일치 (`fun f(a) {}; f(1,2)`) | RuntimeError: "Expected 1 arguments but got 2." |
| `fun` 뒤 이름 누락 | ParseError: "Expect function name." |
| 매개변수 뒤 `)` 누락 | ParseError: "Expect ')' after parameters." |
| 본문 `{` 누락 | ParseError: "Expect '{' before function body." |
| 인자 뒤 `)` 누락 | ParseError: "Expect ')' after arguments." |
| `return` 값 뒤 `;` 누락 | ParseError: "Expect ';' after return value." |
| 매개변수 255개 초과 | ParseError (보고만, 파싱은 계속) |
| 인자 255개 초과 | ParseError (보고만, 파싱은 계속) |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | AST 노드 정의 | `Expr.Call`, `Stmt.Function`, `Stmt.Return` 생성·필드 접근 | `Expr.kt`, `Stmt.kt` |
| 2 | GwanCallable / GwanFunction 기본 | `GwanFunction` 생성, `arity()`, `toString()` | `GwanCallable.kt`, `GwanFunction.kt` |
| 3 | Return 예외 | `Return` 생성, value 접근 | `Return.kt` |
| 4 | 함수 선언 파싱 | `fun f() {}` → `Stmt.Function` | `Parser` funDeclaration |
| 5 | 매개변수 파싱 | `fun f(a, b, c) {}` → params 3개 | `Parser` parameters |
| 6 | call 표현식 파싱 | `f()`, `f(1, 2)` → `Expr.Call` | `Parser` call, finishCall |
| 7 | return 문 파싱 | `return;`, `return 42;` → `Stmt.Return` | `Parser` returnStatement |
| 8 | 파싱 에러 케이스 | 이름 누락, `)` 누락, `{` 누락 등 | `Parser` 에러 메시지 |
| 9 | 인자 최대 개수 | 255개 초과 시 에러 보고 | `Parser` 인자/매개변수 제한 |
| 10 | 네이티브 함수 clock | `clock()` 호출 → Double 반환 | `Interpreter` init |
| 11 | 단순 함수 호출 | `fun greet() { print "hi"; } greet();` → "hi" | `Interpreter` Function + Call |
| 12 | 매개변수 바인딩 | `fun add(a, b) { print a + b; } add(1, 2);` → "3" | `GwanFunction.call()` |
| 13 | return 값 반환 | `fun square(x) { return x * x; } print square(3);` → "9" | Return 예외 처리 |
| 14 | 암묵적 nil 반환 | `fun f() {} print f();` → "nil" | `GwanFunction.call()` null 반환 |
| 15 | 인자 개수 검증 | 개수 불일치 시 RuntimeError | `Interpreter` arity 체크 |
| 16 | 호출 불가 값 검증 | `"not a function"()` → RuntimeError | `Interpreter` GwanCallable 체크 |
| 17 | 재귀 함수 | 피보나치 재귀 구현 | 재귀 호출 + return |
| 18 | 클로저 | `makeCounter()` 패턴 — 환경 캡처 검증 | `GwanFunction` closure |
| 19 | 중첩 함수 | 함수 안에 함수 선언·호출 | 스코프 체인 |
| 20 | 일급 함수 | 함수를 변수에 저장, 인자로 전달 | 함수 = GwanCallable 값 |
| 21 | 연쇄 호출 | `getFunc()()` 패턴 | `call()` while 루프 |
| 22 | 통합 테스트 | 재귀 피보나치, 클로저 카운터, 고차 함수 종합 | 전체 파이프라인 |

### 테스트 작성 규약
- `FunctionParserTest`: 함수 관련 파싱 결과 AST 구조 검증
- `InterpreterTest`: stdout 캡처로 함수 실행 결과 검증
- stdout 캡처 헬퍼: Phase 4에서 사용한 `System.setOut()` 방식 재사용
- 네이티브 함수 `clock()` 테스트: 반환 타입이 `Double`인지 검증 (정확한 값은 검증 불가)

## 7. 완료 기준 (Definition of Done)

- [ ] `Expr.Call` 서브타입 추가
- [ ] `Stmt.Function`, `Stmt.Return` 서브타입 추가
- [ ] `GwanCallable` 인터페이스 정의
- [ ] `GwanFunction` 클래스 — arity, call, toString, 클로저
- [ ] `Return` 예외 클래스
- [ ] `Parser` — `fun` 선언 파싱 (매개변수 포함)
- [ ] `Parser` — call 표현식 파싱 (연쇄 호출 포함)
- [ ] `Parser` — `return` 문 파싱
- [ ] `Parser` — 매개변수/인자 255개 제한
- [ ] `Interpreter` — 함수 선언 실행 (환경 캡처)
- [ ] `Interpreter` — 함수 호출 (매개변수 바인딩, Return 예외 처리)
- [ ] `Interpreter` — 호출 불가/인자 불일치 RuntimeError
- [ ] 네이티브 함수 `clock()` 등록 및 호출
- [ ] 클로저 정상 동작 (환경 캡처 검증)
- [ ] 재귀 함수 정상 동작
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/functions-demo.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 5 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 5 Functions & Closures 스펙 문서 작성`
2. `feat: Expr.Call, Stmt.Function, Stmt.Return AST 노드 추가`
3. `feat: GwanCallable 인터페이스 및 GwanFunction 클래스`
4. `feat: Return 예외 클래스`
5. `feat: Parser 함수 선언 파싱 (fun, 매개변수)`
6. `feat: Parser call 표현식 파싱`
7. `feat: Parser return 문 파싱`
8. `feat: Interpreter 네이티브 함수 clock() 등록`
9. `feat: Interpreter 함수 선언·호출 실행`
10. `feat: Interpreter return 문 및 클로저 실행`
11. `test: 함수 통합 테스트 (재귀, 클로저, 고차 함수)`
12. `docs: Phase 5 완료 기록`
