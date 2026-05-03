# Phase 6: Resolver — 정적 변수 바인딩 분석 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 6
> 참고: Crafting Interpreters Ch. 11 "Resolving and Binding"

## 1. 목적

Phase 5까지의 Interpreter는 변수를 **런타임에** Environment 체인을 따라 탐색한다.
이 방식은 대부분의 경우 올바르게 동작하지만, 클로저와 스코프가 복잡하게 얽힌
엣지 케이스에서 **잘못된 변수를 참조**하는 문제가 있다.

```gwan
var a = "global";
{
  fun showA() {
    print a;
  }
  showA();       // "global" (정상)
  var a = "block";
  showA();       // 기대: "global", 현재: "block" (버그!)
}
```

`showA()`는 선언 시점에 `a`가 전역 변수를 가리킨다. 그러나 나중에 같은 블록에서
`var a = "block"`이 선언되면, 런타임 체인 탐색이 블록의 `a`를 먼저 찾아버린다.

**Resolver**는 인터프리터 실행 **전에** AST를 순회하며 각 변수 참조가 몇 단계
상위 환경에서 선언되었는지(**거리**)를 계산한다. Interpreter는 이 거리를 사용해
체인 탐색 없이 **정확한 환경에서 직접** 변수를 조회한다.

Phase 6가 끝나면 파이프라인은 다음과 같이 변경된다:

```
소스코드 → Scanner → Parser → Resolver → Interpreter → 출력
```

## 2. 범위

### In Scope
- `Resolver` 클래스 — AST 순회, 각 변수 참조에 대한 스코프 거리 계산
- `Interpreter`에 `resolve()` 메서드 추가 — Resolver가 계산한 거리 저장
- `Interpreter`의 변수 조회/대입을 거리 기반으로 변경
- `Environment`에 `getAt(distance, name)` / `assignAt(distance, name, value)` 추가
- `GwanLang.kt` 파이프라인에 Resolver 단계 삽입
- 정적 분석 에러 검출:
  - 같은 스코프에서 변수 중복 선언
  - 자기 자신을 초기화 식에서 참조하는 변수 선언 (`var a = a;`)
  - 최상위 코드에서 `return` 사용
- 기존 테스트가 Resolver 추가 후에도 모두 통과

### Out of Scope (이후 Phase)
- `this` 바인딩 분석 (Phase 7)
- `super` 바인딩 분석 (Phase 7)
- 클래스 메서드의 `return` 문맥 분석 (Phase 7)
- `Expr.Get`, `Expr.Set` 분석 (Phase 7)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Resolver.kt` | Resolver 클래스 (신규) |
| `Interpreter.kt` | `resolve()`, `lookUpVariable()` 추가, 변수 조회/대입 변경 (수정) |
| `Environment.kt` | `getAt()`, `assignAt()`, `ancestor()` 추가 (수정) |
| `GwanLang.kt` | 파이프라인에 Resolver 삽입 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `ResolverTest.kt` | Resolver 정적 분석 테스트 (신규) |
| `InterpreterTest.kt` | 거리 기반 변수 조회 통합 테스트 추가 (수정) |

### 기타
- `examples/resolver-demo.gwan` — 클로저 스코프 정확성 시연 예제
- `docs/CHANGELOG.md` — Phase 6 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 Resolver 클래스

Resolver는 AST를 순회하며 **변수 선언**과 **변수 사용** 지점을 추적한다.
블록 스코프에 진입할 때마다 새 스코프를 스택에 push하고, 빠져나올 때 pop한다.

```kotlin
class Resolver(private val interpreter: Interpreter) {
    // 스코프 스택: 각 스코프는 Map<변수명, 정의 완료 여부>
    private val scopes = ArrayDeque<MutableMap<String, Boolean>>()

    // 현재 함수 종류 추적 (return 문 검증용)
    private var currentFunction = FunctionType.NONE
}

enum class FunctionType {
    NONE,
    FUNCTION
}
```

**스코프 맵의 `Boolean` 값 의미:**
- `false`: 변수가 선언(declare)되었지만 아직 정의(define)되지 않은 상태
- `true`: 변수가 정의(define)되어 사용 가능한 상태

이 구분은 `var a = a;` 같은 자기 참조 초기화를 감지하기 위함이다.

### 4.2 핵심 헬퍼 메서드

```kotlin
private fun beginScope() {
    scopes.addLast(mutableMapOf())
}

private fun endScope() {
    scopes.removeLast()
}

private fun declare(name: Token) {
    if (scopes.isEmpty()) return  // 전역 스코프는 Resolver가 추적하지 않음
    val scope = scopes.last()
    if (scope.containsKey(name.lexeme)) {
        GwanLang.error(name, "Already a variable with this name in this scope.")
    }
    scope[name.lexeme] = false
}

private fun define(name: Token) {
    if (scopes.isEmpty()) return
    scopes.last()[name.lexeme] = true
}

private fun resolveLocal(expr: Expr, name: Token) {
    for (i in scopes.indices.reversed()) {
        if (scopes[i].containsKey(name.lexeme)) {
            interpreter.resolve(expr, scopes.size - 1 - i)
            return
        }
    }
    // 찾지 못하면 전역 변수로 간주 → Interpreter가 globals에서 조회
}
```

### 4.3 AST 순회 — 문장(Stmt) 분석

```
resolve(statements: List<Stmt>):
    for stmt in statements:
        resolve(stmt)

resolve(stmt: Stmt):
    when stmt:
        Block →
            beginScope()
            resolve(stmt.statements)
            endScope()
        Var →
            declare(stmt.name)
            if stmt.initializer != null:
                resolve(stmt.initializer)
            define(stmt.name)
        Function →
            declare(stmt.name)
            define(stmt.name)       // 함수 이름은 즉시 정의 (재귀 허용)
            resolveFunction(stmt, FunctionType.FUNCTION)
        Expression →
            resolve(stmt.expression)
        If →
            resolve(stmt.condition)
            resolve(stmt.thenBranch)
            if stmt.elseBranch != null:
                resolve(stmt.elseBranch)
        Print →
            resolve(stmt.expression)
        Return →
            if currentFunction == FunctionType.NONE:
                GwanLang.error(stmt.keyword, "Can't return from top-level code.")
            if stmt.value != null:
                resolve(stmt.value)
        While →
            resolve(stmt.condition)
            resolve(stmt.body)
```

#### `resolveFunction()`

```kotlin
private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
    val enclosingFunction = currentFunction
    currentFunction = type

    beginScope()
    for (param in function.params) {
        declare(param)
        define(param)
    }
    resolve(function.body)
    endScope()

    currentFunction = enclosingFunction
}
```

함수 이름은 함수 본문의 스코프 **바깥**에서 선언·정의한다 (재귀 호출 허용).
매개변수는 함수 본문의 스코프 **안**에서 선언·정의한다.
`currentFunction`을 저장/복원하여 중첩 함수에서도 올바르게 추적한다.

### 4.4 AST 순회 — 표현식(Expr) 분석

```
resolve(expr: Expr):
    when expr:
        Variable →
            if scopes.isNotEmpty() && scopes.last()[expr.name.lexeme] == false:
                GwanLang.error(expr.name, "Can't read local variable in its own initializer.")
            resolveLocal(expr, expr.name)
        Assign →
            resolve(expr.value)
            resolveLocal(expr, expr.name)
        Binary →
            resolve(expr.left)
            resolve(expr.right)
        Call →
            resolve(expr.callee)
            for arg in expr.arguments:
                resolve(arg)
        Grouping →
            resolve(expr.expression)
        Literal →
            // nothing to resolve
        Logical →
            resolve(expr.left)
            resolve(expr.right)
        Unary →
            resolve(expr.right)
```

`Expr.Variable` 분석 시, 같은 스코프에서 `false`(선언만 됨) 상태인 변수를
읽으려 하면 에러를 보고한다:

```gwan
var a = a;  // 에러: "Can't read local variable in its own initializer."
```

### 4.5 Environment 확장

```kotlin
class Environment(val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    // 기존 메서드 유지 (전역 변수용)
    fun define(name: String, value: Any?) { ... }
    fun get(name: Token): Any? { ... }
    fun assign(name: Token, value: Any?) { ... }

    // 신규: 거리 기반 조회
    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    // 신규: 거리 기반 대입
    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    // 신규: N단계 상위 환경 반환
    private fun ancestor(distance: Int): Environment {
        var environment: Environment = this
        for (i in 0 until distance) {
            environment = environment.enclosing!!
        }
        return environment
    }
}
```

`ancestor()`는 `enclosing`을 `distance`번 따라간다. Resolver가 올바르게
거리를 계산했다면 `enclosing!!`은 안전하다.

### 4.6 Interpreter 변경

#### 거리 저장

```kotlin
class Interpreter {
    private val globals = Environment()
    private var environment = globals
    private val locals = mutableMapOf<Expr, Int>()  // 신규

    fun resolve(expr: Expr, depth: Int) {           // 신규
        locals[expr] = depth
    }
    // ...
}
```

`locals`는 각 AST 노드(변수 참조 / 대입)에 대해 Resolver가 계산한 스코프
거리를 저장한다. Kotlin의 `data class`는 참조 동등성이 아닌 구조적 동등성을
사용하므로, `locals`의 키를 **identity 기반**으로 관리해야 한다.
`IdentityHashMap`을 사용한다.

```kotlin
private val locals = IdentityHashMap<Expr, Int>()
```

#### 변수 조회 변경

```kotlin
private fun lookUpVariable(name: Token, expr: Expr): Any? {
    val distance = locals[expr]
    return if (distance != null) {
        environment.getAt(distance, name.lexeme)
    } else {
        globals.get(name)
    }
}
```

`locals`에 없는 변수는 전역 변수로 간주하여 `globals`에서 조회한다.

#### evaluate() 변경 — Variable, Assign

```
is Expr.Variable → lookUpVariable(expr.name, expr)

is Expr.Assign → {
    val value = evaluate(expr.value)
    val distance = locals[expr]
    if (distance != null) {
        environment.assignAt(distance, expr.name, value)
    } else {
        globals.assign(expr.name, value)
    }
    value
}
```

기존의 `environment.get(expr.name)` / `environment.assign(expr.name, value)`를
`lookUpVariable()` / 거리 기반 분기로 교체한다.

### 4.7 GwanLang.kt 파이프라인 변경

```kotlin
private fun run(source: String, repl: Boolean = false) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val statements = parser.parse()
    if (GwanLang.hadError) return

    val resolver = Resolver(interpreter)    // 신규
    resolver.resolve(statements)            // 신규
    if (GwanLang.hadError) return           // 신규

    if (repl && statements.size == 1 && statements[0] is Stmt.Expression) {
        val value = interpreter.interpretExpr((statements[0] as Stmt.Expression).expression)
        if (!GwanLang.hadRuntimeError) println(interpreter.stringify(value))
        return
    }

    interpreter.interpret(statements)
}
```

Resolver에서 에러가 발생하면 Interpreter를 실행하지 않는다.

### 4.8 최상위 return 처리 변경

현재 `Interpreter.interpret()`에서 `Return` 예외를 catch하여 최상위 return을
방어하고 있다. Resolver 도입 후에는 **정적 분석 단계에서** `return`이 함수
밖에서 사용되었는지 검증하므로, Interpreter의 catch 블록은 안전망으로 유지하되
Resolver가 1차 방어선이 된다.

## 5. 에러 처리 방침

| 상황 | 단계 | 동작 |
|------|------|------|
| `var a = a;` (자기 참조 초기화) | Resolver | "Can't read local variable in its own initializer." |
| 같은 스코프 중복 선언 `var a = 1; var a = 2;` | Resolver | "Already a variable with this name in this scope." |
| 최상위 `return` | Resolver | "Can't return from top-level code." |
| 미선언 변수 사용 | Interpreter | RuntimeError: "Undefined variable '...'." (기존과 동일) |

**참고:** 전역 스코프의 중복 선언은 에러로 처리하지 않는다. 전역 변수는 Resolver가
추적하지 않으며, REPL에서 변수를 재선언하는 것은 유용하기 때문이다.

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | Environment getAt/assignAt | `getAt(0, name)` 현재 환경 조회, `getAt(2, name)` 상위 조회, `assignAt()` 대입 검증 | `Environment` getAt, assignAt, ancestor |
| 2 | Resolver 기본 — 변수 선언/사용 | `var a = 1; print a;` → Resolver가 `interpreter.resolve()` 호출 | `Resolver` 클래스 기본 구조, resolve Variable/Var |
| 3 | Resolver — 블록 스코프 | `{ var a = 1; print a; }` → 거리 0으로 resolve | `Resolver` Block, beginScope/endScope |
| 4 | Resolver — 중첩 스코프 거리 | `var a = 1; { { print a; } }` → 거리 2로 resolve | `resolveLocal()` 거리 계산 |
| 5 | Resolver — 함수 분석 | `fun f() { var x = 1; print x; }` → 매개변수/본문 resolve | `resolveFunction()` |
| 6 | Resolver — 클로저 거리 | `fun outer() { var x = 1; fun inner() { print x; } }` → 거리 1로 resolve | 중첩 함수 resolve |
| 7 | 중복 선언 에러 | `{ var a = 1; var a = 2; }` → 에러 보고 | `declare()` 중복 검사 |
| 8 | 자기 참조 초기화 에러 | `{ var a = a; }` → 에러 보고 | `Expr.Variable` resolve 시 false 체크 |
| 9 | 최상위 return 에러 | `return 1;` → 에러 보고 | `currentFunction` 체크 |
| 10 | Interpreter 거리 기반 조회 | `var a = 1; { var a = 2; print a; }` → "2" (거리 0) | `lookUpVariable()`, `getAt()` |
| 11 | Interpreter 거리 기반 대입 | `var a = 1; { a = 2; } print a;` → "2" (전역 대입) | `assignAt()`, 전역 분기 |
| 12 | 클로저 정확성 — 핵심 시나리오 | §1의 `showA()` 예제 → 두 번 모두 "global" | 전체 파이프라인 |
| 13 | 재귀 함수 | 피보나치 재귀 → 올바른 결과 | 함수 이름 즉시 define |
| 14 | 기존 테스트 회귀 검증 | `./gradlew test` 전체 통과 | 파이프라인 통합 |
| 15 | 통합 테스트 | 클로저 카운터, 중첩 함수, 복잡한 스코프 체인 | 전체 Resolver + Interpreter |

### 테스트 작성 규약
- `ResolverTest`: Resolver 정적 분석 결과 검증 (에러 보고, resolve 호출 여부)
- `InterpreterTest`: stdout 캡처로 Resolver 통과 후 실행 결과 검증
- 에러 검증: `GwanLang.hadError` 플래그 또는 stderr 캡처로 에러 메시지 확인
- 기존 Phase 1~5 테스트가 모두 통과하는지 매 사이클마다 확인

## 7. 완료 기준 (Definition of Done)

- [ ] `Resolver` 클래스 — AST 순회, 변수 바인딩 거리 계산
- [ ] `FunctionType` enum — NONE, FUNCTION
- [ ] `Environment.getAt()` — 거리 기반 변수 조회
- [ ] `Environment.assignAt()` — 거리 기반 변수 대입
- [ ] `Environment.ancestor()` — N단계 상위 환경 반환
- [ ] `Interpreter.resolve()` — Resolver가 계산한 거리 저장
- [ ] `Interpreter.lookUpVariable()` — 거리 기반 변수 조회 분기
- [ ] `Interpreter` — `Expr.Variable`, `Expr.Assign` 거리 기반으로 변경
- [ ] `Interpreter.locals` — `IdentityHashMap<Expr, Int>`
- [ ] `GwanLang.kt` — 파이프라인에 Resolver 삽입
- [ ] Resolver — 같은 스코프 중복 선언 에러
- [ ] Resolver — 자기 참조 초기화 에러 (`var a = a;`)
- [ ] Resolver — 최상위 `return` 에러
- [ ] 클로저 정확성 — §1 핵심 시나리오 통과
- [ ] 기존 Phase 1~5 테스트 전체 통과 (회귀 없음)
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/resolver-demo.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 6 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 6 Resolver 스펙 문서 작성`
2. `feat: Environment getAt/assignAt/ancestor 추가`
3. `feat: Resolver 클래스 기본 구조 및 변수 resolve`
4. `feat: Resolver 블록 스코프, 함수 분석`
5. `feat: Resolver 정적 에러 검출 (중복 선언, 자기 참조, 최상위 return)`
6. `feat: Interpreter 거리 기반 변수 조회/대입`
7. `feat: GwanLang 파이프라인에 Resolver 삽입`
8. `test: Resolver + Interpreter 통합 테스트 (클로저 정확성)`
9. `docs: Phase 6 완료 기록`
