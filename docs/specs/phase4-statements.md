# Phase 4: Statements & State (문장과 상태) 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 4
> 참고: Crafting Interpreters Ch. 8 "Statements and State", Ch. 9 "Control Flow"

## 1. 목적

Phase 3까지 구현된 표현식 평가기를 확장하여, **문장(Statement) 실행**, **변수 선언·대입**,
**렉시컬 스코프**, **제어 흐름(if/while/for)**을 TDD로 구현한다.

```
var x = 10;
if (x > 5) {
  print x * 2;  // 20
} else {
  print x;
}
```

Phase 4가 끝나면 GwanLang은 변수와 제어 흐름을 갖춘 스크립트 언어로 동작한다.

## 2. 범위

### In Scope
- `Stmt` sealed class 계층 정의 (Expression, Print, Var, Block, If, While)
- `Expr` 확장: `Variable`, `Assign`, `Logical`
- `Environment` 클래스 — 렉시컬 스코프 체인 (변수 맵의 연결 리스트)
- `Parser` 확장 — 문장/선언 파싱, `for` 루프 디슈가링
- `Interpreter` 확장 — 문장 실행, 환경 관리
- `GwanLang.kt` 파이프라인 변경: `Expr` 단일 → `List<Stmt>` 실행
- 논리 연산자 `and`, `or` (short-circuit 평가)

### Out of Scope (이후 Phase)
- 함수 선언/호출, `return` 문 (Phase 5)
- `Expr.Call` (Phase 5)
- 클로저, 네이티브 함수 (Phase 5)
- `Resolver` 정적 바인딩 (Phase 6)
- 클래스, `this`, `super` (Phase 7)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Stmt.kt` | `Stmt` sealed class 계층 정의 (신규) |
| `Expr.kt` | `Variable`, `Assign`, `Logical` 서브타입 추가 (수정) |
| `Environment.kt` | 렉시컬 스코프 체인 (신규) |
| `Parser.kt` | 문장/선언 파싱, for 디슈가링 (수정) |
| `Interpreter.kt` | 문장 실행, Environment 사용 (수정) |
| `GwanLang.kt` | `List<Stmt>` 파이프라인, REPL 표현식 자동 출력 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `EnvironmentTest.kt` | Environment 단위 테스트 (신규) |
| `StatementParserTest.kt` | 문장 파싱 테스트 (신규) |
| `InterpreterTest.kt` | 문장 실행 테스트 추가 (수정) |

### 기타
- `examples/statements-demo.gwan` — 변수, 스코프, 제어흐름 시연 예제
- `docs/CHANGELOG.md` — Phase 4 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 `Stmt` sealed class 계층

```kotlin
sealed class Stmt {
    data class Expression(val expression: Expr) : Stmt()
    data class Print(val expression: Expr) : Stmt()
    data class Var(val name: Token, val initializer: Expr?) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    data class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt()
    data class While(val condition: Expr, val body: Stmt) : Stmt()
}
```

### 4.2 `Expr` 추가 서브타입

```kotlin
// 기존 4종 (Binary, Grouping, Literal, Unary)에 추가:
data class Variable(val name: Token) : Expr()
data class Assign(val name: Token, val value: Expr) : Expr()
data class Logical(val left: Expr, val op: Token, val right: Expr) : Expr()
```

### 4.3 `Environment` 클래스

변수 바인딩을 관리하는 렉시컬 스코프 체인. 각 스코프는 변수 이름→값 맵을 갖고,
`enclosing` 포인터로 상위 스코프를 참조한다.

```kotlin
class Environment(val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    fun define(name: String, value: Any?)
    fun get(name: Token): Any?
    fun assign(name: Token, value: Any?)
}
```

**동작 규칙:**
- `define()`: 현재 스코프에 변수 추가. 같은 스코프 내 재선언 허용 (값 덮어쓰기).
- `get()`: 현재 스코프에서 찾고, 없으면 `enclosing`으로 올라감. 최상위까지 없으면 RuntimeError.
- `assign()`: `get()`과 같은 탐색 순서. 없으면 RuntimeError ("Undefined variable '...'.")
- 미초기화 변수: `var x;` → 값은 `nil`(= Kotlin `null`)로 저장.

### 4.4 Parser 확장

현재 Parser는 `parse(): Expr?`로 표현식 하나만 반환한다. Phase 4에서는 문장 리스트를 반환하도록 변경한다.

```kotlin
// 변경 전
fun parse(): Expr?

// 변경 후
fun parse(): List<Stmt>
```

#### 문법 규칙 (Recursive Descent)

```
program     → declaration* EOF ;
declaration → varDecl | statement ;
varDecl     → "var" IDENTIFIER ( "=" expression )? ";" ;
statement   → exprStmt | printStmt | block | ifStmt | whileStmt | forStmt ;
exprStmt    → expression ";" ;
printStmt   → "print" expression ";" ;
block       → "{" declaration* "}" ;
ifStmt      → "if" "(" expression ")" statement ( "else" statement )? ;
whileStmt   → "while" "(" expression ")" statement ;
forStmt     → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
expression  → assignment ;
assignment  → IDENTIFIER "=" assignment | logic_or ;
logic_or    → logic_and ( "or" logic_and )* ;
logic_and   → equality ( "and" equality )* ;
```

#### `for` 루프 디슈가링

`for`는 `while`로 변환한다 (별도 AST 노드 없음):

```
for (var i = 0; i < 10; i = i + 1) { body }
```
→
```
{
  var i = 0;
  while (i < 10) {
    { body }
    i = i + 1;
  }
}
```

#### 에러 복구 (synchronize)

파싱 에러 발생 시 `synchronize()`로 다음 문장 경계까지 건너뛴다:
- 세미콜론(`;`) 다음 토큰
- 키워드(`class`, `fun`, `var`, `for`, `if`, `while`, `print`, `return`) 앞

### 4.5 Interpreter 확장

#### 시그니처 변경

```kotlin
class Interpreter {
    private val globals = Environment()
    private var environment = globals

    fun interpret(statements: List<Stmt>)
    private fun execute(stmt: Stmt)
    fun executeBlock(statements: List<Stmt>, environment: Environment)
    private fun evaluate(expr: Expr): Any?
}
```

#### 문장 실행 (`execute`)

```
execute(stmt):
    when stmt:
        Expression → evaluate(stmt.expression)
        Print      → value = evaluate(stmt.expression); println(stringify(value))
        Var        →
            value = if (stmt.initializer != null) evaluate(stmt.initializer) else null
            environment.define(stmt.name.lexeme, value)
        Block      → executeBlock(stmt.statements, Environment(environment))
        If         →
            if (isTruthy(evaluate(stmt.condition)))
                execute(stmt.thenBranch)
            else if (stmt.elseBranch != null)
                execute(stmt.elseBranch)
        While      →
            while (isTruthy(evaluate(stmt.condition)))
                execute(stmt.body)
```

#### 표현식 평가 추가

```
evaluate(expr):
    ...기존 분기...
    Variable → environment.get(expr.name)
    Assign   →
        value = evaluate(expr.value)
        environment.assign(expr.name, value)
        return value
    Logical  →
        left = evaluate(expr.left)
        when expr.op.type:
            OR  → if (isTruthy(left)) return left
            AND → if (!isTruthy(left)) return left
        return evaluate(expr.right)
```

**논리 연산자 short-circuit:**
- `or`: 좌측이 truthy면 우측을 평가하지 않고 좌측 값 반환
- `and`: 좌측이 falsy면 우측을 평가하지 않고 좌측 값 반환
- 반환 값은 `Boolean`이 아니라 마지막으로 평가된 **원래 값**

#### `executeBlock`

```kotlin
fun executeBlock(statements: List<Stmt>, environment: Environment) {
    val previous = this.environment
    try {
        this.environment = environment
        for (statement in statements) execute(statement)
    } finally {
        this.environment = previous
    }
}
```

### 4.6 `GwanLang.kt` 파이프라인 변경

```kotlin
private fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val statements = parser.parse()
    if (GwanLang.hadError) return
    interpreter.interpret(statements)
}
```

**변경 사항:**
- `Interpreter` 인스턴스를 전역 필드로 유지 (REPL에서 변수 상태 유지)
- `run()` 내에서 매번 새 Interpreter를 만들지 않음
- REPL에서 표현식만 입력한 경우 결과를 출력하는 것은 `Expression` 문장의 evaluate 결과가 자동 버려지는 방식 유지 (print 없으면 출력 안 함)

### 4.7 `AstPrinter` 처리

Phase 4에서 파이프라인이 `List<Stmt>`로 바뀌므로 `AstPrinter`는 더 이상 메인 파이프라인에서 사용되지 않는다. 제거하지 않고 유틸리티로 남겨둔다 (디버깅용).

## 5. 에러 처리 방침

| 상황 | 동작 |
|------|------|
| 미정의 변수 읽기 (`print x;` — x 미선언) | RuntimeError: "Undefined variable 'x'." |
| 미정의 변수 대입 (`x = 5;` — x 미선언) | RuntimeError: "Undefined variable 'x'." |
| `var` 뒤 식별자 누락 | ParseError: "Expect variable name." |
| `print` 뒤 세미콜론 누락 | ParseError: "Expect ';' after value." |
| 블록 `}` 누락 | ParseError: "Expect '}' after block." |
| `if` 뒤 `(` 누락 | ParseError: "Expect '(' after 'if'." |
| `if` 조건 뒤 `)` 누락 | ParseError: "Expect ')' after if condition." |
| `while` 뒤 `(` 누락 | ParseError: "Expect '(' after 'while'." |
| `while` 조건 뒤 `)` 누락 | ParseError: "Expect ')' after condition." |
| `for` 뒤 `(` 누락 | ParseError: "Expect '(' after 'for'." |
| `for` 조건 뒤 `)` 누락 | ParseError: "Expect ')' after for clauses." |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | Stmt 정의 | Stmt 서브타입 생성, 필드 접근 | `Stmt.kt` |
| 2 | Expr 확장 | Variable, Assign, Logical 노드 생성 | `Expr.kt` 수정 |
| 3 | Environment 기본 | define/get 동작, 미정의 변수 에러 | `Environment.kt` |
| 4 | Environment 스코프 | 중첩 스코프에서 변수 조회/섀도잉 | `Environment.kt` enclosing |
| 5 | Environment 대입 | assign 동작, 상위 스코프 대입 | `Environment.assign()` |
| 6 | print 문 파싱 | `print "hello";` → `Stmt.Print` | `Parser` printStmt |
| 7 | 표현식 문 파싱 | `1 + 2;` → `Stmt.Expression` | `Parser` exprStmt |
| 8 | var 선언 파싱 | `var x = 5;`, `var y;` → `Stmt.Var` | `Parser` varDecl |
| 9 | 변수 참조 파싱 | `x` → `Expr.Variable` | `Parser` primary 확장 |
| 10 | 대입 파싱 | `x = 10;` → `Expr.Assign` | `Parser` assignment |
| 11 | 블록 파싱 | `{ var x = 1; print x; }` → `Stmt.Block` | `Parser` block |
| 12 | if 문 파싱 | `if (cond) stmt`, `if (cond) stmt else stmt` | `Parser` ifStmt |
| 13 | while 문 파싱 | `while (cond) stmt` | `Parser` whileStmt |
| 14 | for 문 파싱 | `for (var i=0; i<10; i=i+1) stmt` → while 디슈가링 | `Parser` forStmt |
| 15 | 논리 연산자 파싱 | `a or b`, `a and b` → `Expr.Logical` | `Parser` logic_or/and |
| 16 | print 문 실행 | `print 42;` → stdout "42" | `Interpreter` execute Print |
| 17 | var 선언/참조 실행 | `var x = 10; print x;` → "10" | `Interpreter` + Environment |
| 18 | 미초기화 변수 | `var x; print x;` → "nil" | `Interpreter` Var nil 기본값 |
| 19 | 대입 실행 | `var x = 1; x = 2; print x;` → "2" | `Interpreter` Assign |
| 20 | 블록 스코프 | 내부 스코프 변수가 외부에서 안 보임 | `Interpreter` executeBlock |
| 21 | 스코프 섀도잉 | 내부 스코프에서 같은 이름 변수 재선언 | Environment 스코프 체인 |
| 22 | if/else 실행 | 조건에 따른 분기 실행 | `Interpreter` execute If |
| 23 | while 실행 | 조건 반복 실행, 종료 확인 | `Interpreter` execute While |
| 24 | for 실행 | 카운터 기반 반복, 피보나치 출력 | for 디슈가링 + while 실행 |
| 25 | 논리 연산자 실행 | short-circuit 평가, 값 반환 | `Interpreter` Logical |
| 26 | 에러 복구 | 파싱 에러 후 다음 문장 계속 파싱 | `Parser` synchronize |
| 27 | 통합 테스트 | 피보나치, FizzBuzz 등 종합 스크립트 | 전체 파이프라인 |

### 테스트 작성 규약
- `EnvironmentTest`: Environment 단위 테스트 (Parser/Interpreter 없이)
- `StatementParserTest`: 문장 파싱 결과 AST 구조 검증
- `InterpreterTest`: stdout 캡처로 문장 실행 결과 검증
- stdout 캡처 헬퍼: `System.setOut()`으로 PrintStream 교체

## 7. 완료 기준 (Definition of Done)

- [ ] `Stmt` sealed class 계층 정의 (Expression, Print, Var, Block, If, While)
- [ ] `Expr` 확장 (Variable, Assign, Logical)
- [ ] `Environment` 클래스 — define/get/assign, 스코프 체인
- [ ] `Parser` 확장 — 문장/선언 파싱, 에러 복구
- [ ] `for` 루프 디슈가링 (while로 변환)
- [ ] 논리 연산자 `and`, `or` (short-circuit)
- [ ] `Interpreter` 확장 — 문장 실행, 변수, 스코프
- [ ] `GwanLang.kt` 파이프라인 변경 (`List<Stmt>` 실행)
- [ ] 미정의 변수 접근/대입 시 RuntimeError
- [ ] 블록 스코프 및 섀도잉 정상 동작
- [ ] if/else, while, for 정상 동작
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/statements-demo.gwan` 정상 동작 (피보나치 출력 포함)
- [ ] `docs/CHANGELOG.md`에 Phase 4 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 4 Statements 스펙 문서 작성`
2. `feat: Stmt sealed class 계층 및 Expr 확장 (Variable, Assign, Logical)`
3. `feat: Environment 클래스 — 변수 정의/조회/대입, 스코프 체인`
4. `feat: Parser 문장 파싱 (print, expression, var 선언)`
5. `feat: Parser 변수 참조/대입 및 블록 파싱`
6. `feat: Parser if/while/for 문 파싱 (for → while 디슈가링)`
7. `feat: Parser 논리 연산자 (and, or) 파싱`
8. `feat: Interpreter 문장 실행 (print, expression, var)`
9. `feat: Interpreter 블록 스코프 및 대입 실행`
10. `feat: Interpreter if/else, while 실행`
11. `feat: Interpreter 논리 연산자 short-circuit 실행`
12. `feat: GwanLang 파이프라인 변경 및 통합 테스트`
13. `docs: Phase 4 완료 기록`
