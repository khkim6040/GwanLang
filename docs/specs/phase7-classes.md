# Phase 7: Classes — 클래스와 상속 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 7
> 참고: Crafting Interpreters Ch. 12 "Classes", Ch. 13 "Inheritance"

## 1. 목적

Phase 6까지 GwanLang은 함수와 클로저를 지원하지만, 데이터와 행동을 하나의
단위로 묶는 **객체 지향** 메커니즘이 없다. Phase 7에서는 클래스 선언, 인스턴스
생성, 프로퍼티, 메서드, `this`, 생성자(`init`), 단일 상속(`<`), `super`를
구현하여 GwanLang을 완전한 스크립트 언어로 완성한다.

```gwan
class Animal {
  init(name) {
    this.name = name;
  }
  speak() {
    print this.name + " makes a sound.";
  }
}

class Dog < Animal {
  speak() {
    print this.name + " barks!";
  }
  fetch(item) {
    print this.name + " fetches " + item + "!";
  }
}

var dog = Dog("Rex");
dog.speak();          // "Rex barks!"
dog.fetch("ball");    // "Rex fetches ball!"
```

Phase 7이 끝나면 파이프라인은 동일하게 유지된다:

```
소스코드 → Scanner → Parser → Resolver → Interpreter → 출력
```

단, Parser, Resolver, Interpreter 각각이 클래스 관련 AST 노드를 처리하게 확장된다.

## 2. 범위

### In Scope
- **AST 확장**: `Expr.Get`, `Expr.Set`, `Expr.This`, `Expr.Super`, `Stmt.Class`
- **Parser 확장**: 클래스 선언, 프로퍼티 접근(`.`), `this`, `super.method()`, 필드 대입(`obj.field = value`)
- **`GwanClass`**: `GwanCallable` 구현, 메서드 저장, `findMethod()`, 상속 체인
- **`GwanInstance`**: 프로퍼티 맵, `get()`/`set()`
- **`GwanFunction.bind(instance)`**: 메서드 호출 시 `this` 바인딩
- **Interpreter 확장**: 클래스 선언 실행, 인스턴스 생성, Get/Set/This/Super 평가
- **Resolver 확장**:
  - `Stmt.Class` — 클래스 스코프 분석
  - `this` — 클래스 메서드 안에서만 허용, 거리 계산
  - `super` — 서브클래스 메서드 안에서만 허용, 거리 계산
  - 자기 자신을 상속하는 것 금지
- **`FunctionType` 확장**: `INITIALIZER`, `METHOD` 추가
- **`ClassType` enum 추가**: `NONE`, `CLASS`, `SUBCLASS`
- 기존 Phase 1~6 테스트 회귀 없음

### Out of Scope (향후 확장 후보)
- 정적 메서드(static method)
- getter/setter 문법
- 다중 상속 또는 믹스인
- 메타클래스
- 연산자 오버로딩
- 추상 클래스/인터페이스

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `Expr.kt` | `Get`, `Set`, `This`, `Super` 추가 (수정) |
| `Stmt.kt` | `Class` 추가 (수정) |
| `Parser.kt` | classDeclaration, `.` 접근, this/super 파싱, 필드 대입 (수정) |
| `GwanClass.kt` | 클래스 런타임 객체 (신규) |
| `GwanInstance.kt` | 인스턴스 런타임 객체 (신규) |
| `GwanFunction.kt` | `bind()` 메서드 추가, init 반환값 처리 (수정) |
| `Interpreter.kt` | Class/Get/Set/This/Super 실행 (수정) |
| `Resolver.kt` | Class/Get/Set/This/Super 분석, ClassType/FunctionType 확장 (수정) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `ClassTest.kt` | 클래스 전용 통합 테스트 (신규) |
| `InterpreterTest.kt` | 클래스 관련 통합 테스트 추가 (수정) |

### 기타
- `examples/classes.gwan` — 클래스/상속 시연 예제
- `src/test/kotlin/gwanlang/testdata/classes.gwan` — 클래스 통합 테스트 스크립트
- `docs/CHANGELOG.md` — Phase 7 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 AST 확장

#### Expr 추가 노드

```kotlin
sealed class Expr {
    // ... 기존 8종 유지 ...
    data class Get(val obj: Expr, val name: Token) : Expr()
    data class Set(val obj: Expr, val name: Token, val value: Expr) : Expr()
    data class This(val keyword: Token) : Expr()
    data class Super(val keyword: Token, val method: Token) : Expr()
}
```

| 노드 | 역할 | 예시 |
|------|------|------|
| `Get` | 프로퍼티/메서드 읽기 | `obj.field`, `obj.method` |
| `Set` | 프로퍼티 대입 | `obj.field = value` |
| `This` | 현재 인스턴스 참조 | `this.name` |
| `Super` | 부모 클래스 메서드 참조 | `super.speak()` |

#### Stmt 추가 노드

```kotlin
sealed class Stmt {
    // ... 기존 8종 유지 ...
    data class Class(
        val name: Token,
        val superclass: Expr.Variable?,
        val methods: List<Stmt.Function>
    ) : Stmt()
}
```

- `superclass`: `class Dog < Animal`에서 `Animal`에 해당하는 Variable 표현식. 상속이 없으면 `null`.
- `methods`: 클래스 본문의 메서드 목록. 각 메서드는 기존 `Stmt.Function`을 재사용.

### 4.2 Parser 확장

#### 4.2.1 classDeclaration

`declaration()`에서 `class` 키워드를 인식한다:

```kotlin
private fun declaration(): Stmt? {
    return try {
        if (match(TokenType.CLASS)) classDeclaration()
        else if (match(TokenType.FUN)) funDeclaration("function")
        else if (match(TokenType.VAR)) varDeclaration()
        else statement()
    } catch (e: ParseError) {
        synchronize()
        null
    }
}
```

```kotlin
private fun classDeclaration(): Stmt {
    val name = consume(TokenType.IDENTIFIER, "Expect class name.")

    val superclass = if (match(TokenType.LESS)) {
        consume(TokenType.IDENTIFIER, "Expect superclass name.")
        Expr.Variable(previous())
    } else null

    consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")

    val methods = mutableListOf<Stmt.Function>()
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
        methods.add(funDeclaration("method"))
    }

    consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")
    return Stmt.Class(name, superclass, methods)
}
```

**설계 결정:**
- 상속 문법은 `<` 토큰을 사용한다 (Lox와 동일).
- 클래스 본문에는 메서드만 허용한다 (필드 선언 문법 없음, `this.field = value`로 동적 추가).
- 메서드 파싱에 기존 `funDeclaration("method")`를 재사용한다.

#### 4.2.2 프로퍼티 접근 (`.`)

`call()` 규칙에 `.IDENTIFIER` 체인을 추가한다:

```kotlin
private fun call(): Expr {
    var expr = primary()
    while (true) {
        if (match(TokenType.LEFT_PAREN)) {
            expr = finishCall(expr)
        } else if (match(TokenType.DOT)) {
            val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
            expr = Expr.Get(expr, name)
        } else {
            break
        }
    }
    return expr
}
```

#### 4.2.3 필드 대입 (Set)

`assignment()` 규칙에서 `Expr.Get`을 대입 대상으로 인식한다:

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

    return expr
}
```

#### 4.2.4 `this` 파싱

`primary()`에 `this` 키워드를 추가한다:

```kotlin
if (match(TokenType.THIS)) return Expr.This(previous())
```

#### 4.2.5 `super` 파싱

`primary()`에 `super` 키워드를 추가한다:

```kotlin
if (match(TokenType.SUPER)) {
    val keyword = previous()
    consume(TokenType.DOT, "Expect '.' after 'super'.")
    val method = consume(TokenType.IDENTIFIER, "Expect superclass method name.")
    return Expr.Super(keyword, method)
}
```

`super`는 항상 `super.methodName` 형태로만 사용 가능하다 (`super` 단독 사용 불가).

### 4.3 GwanClass — 클래스 런타임 객체

```kotlin
class GwanClass(
    val name: String,
    val superclass: GwanClass?,
    private val methods: Map<String, GwanFunction>
) : GwanCallable {

    fun findMethod(name: String): GwanFunction? {
        if (methods.containsKey(name)) {
            return methods[name]
        }
        // 상속 체인 탐색
        if (superclass != null) {
            return superclass.findMethod(name)
        }
        return null
    }

    override fun arity(): Int {
        val initializer = findMethod("init")
        return initializer?.arity() ?: 0
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = GwanInstance(this)
        val initializer = findMethod("init")
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments)
        }
        return instance
    }

    override fun toString(): String = name
}
```

**설계 결정:**
- 클래스 자체가 `GwanCallable`이므로 `ClassName()` 호출이 인스턴스 생성을 의미한다.
- `arity()`는 `init` 메서드의 매개변수 수를 반환한다 (없으면 0).
- `findMethod()`는 현재 클래스에서 먼저 찾고, 없으면 superclass 체인을 탐색한다.
- `call()`에서 인스턴스 생성 후 `init`을 자동으로 호출한다.

### 4.4 GwanInstance — 인스턴스 런타임 객체

```kotlin
class GwanInstance(private val klass: GwanClass) {
    private val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
            return fields[name.lexeme]
        }

        val method = klass.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String = "${klass.name} instance"
}
```

**프로퍼티 조회 우선순위:**
1. 인스턴스 필드 (동적으로 추가된 값)
2. 클래스 메서드 (+ 상속 체인)
3. 위 모두 없으면 RuntimeError

**참고:** 필드와 동일한 이름의 메서드가 있으면 필드가 우선한다 (필드가 메서드를 가린다).

### 4.5 GwanFunction 확장 — `bind()`

메서드 호출 시 `this`를 바인딩하기 위해 `bind()` 메서드를 추가한다:

```kotlin
class GwanFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
    private val isInitializer: Boolean = false
) : GwanCallable {

    fun bind(instance: GwanInstance): GwanFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return GwanFunction(declaration, environment, isInitializer)
    }

    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            // init에서 early return하면 this를 반환
            if (isInitializer) return closure.getAt(0, "this")
            return returnValue.value
        }
        // init은 암묵적으로 this를 반환
        if (isInitializer) return closure.getAt(0, "this")
        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}
```

**`bind()` 동작:**
- 새 환경을 만들어 `"this"`를 인스턴스로 정의한다.
- 이 환경을 클로저로 하는 새 `GwanFunction`을 반환한다.
- 이로써 메서드 본문에서 `this`를 참조하면 Resolver가 계산한 거리(1)로 바인딩된 인스턴스를 찾는다.

**`isInitializer` 동작:**
- `init()` 메서드에서 `return;`(값 없는 return)을 하면 `this`를 반환한다.
- `init()` 본문이 끝까지 실행되면 암묵적으로 `this`를 반환한다.
- `init()` 에서 값이 있는 `return value;`는 Resolver가 정적으로 금지한다.

### 4.6 Interpreter 확장

#### 4.6.1 `Stmt.Class` 실행

```kotlin
is Stmt.Class -> {
    val superclass: Any? = if (stmt.superclass != null) {
        val sc = evaluate(stmt.superclass)
        if (sc !is GwanClass) {
            throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
        }
        sc
    } else null

    environment.define(stmt.name.lexeme, null)

    // super 환경 설정
    if (stmt.superclass != null) {
        environment = Environment(environment)
        environment.define("super", superclass)
    }

    val methods = mutableMapOf<String, GwanFunction>()
    for (method in stmt.methods) {
        val function = GwanFunction(method, environment, method.name.lexeme == "init")
        methods[method.name.lexeme] = function
    }

    val klass = GwanClass(stmt.name.lexeme, superclass as GwanClass?, methods)

    if (superclass != null) {
        environment = environment.enclosing!!
    }

    environment.assign(stmt.name, klass)
}
```

**실행 순서:**
1. superclass 표현식을 평가하고 클래스인지 검증
2. 클래스 이름을 현재 환경에 `null`로 선언 (전방 참조 허용)
3. superclass가 있으면 새 환경을 만들어 `"super"` 바인딩
4. 각 메서드를 `GwanFunction`으로 변환 (현재 환경 = 클로저)
5. `GwanClass` 생성
6. super 환경을 pop
7. 클래스 이름에 `GwanClass` 객체를 대입

#### 4.6.2 `Expr.Get` 평가

```kotlin
is Expr.Get -> {
    val obj = evaluate(expr.obj)
    if (obj is GwanInstance) {
        return obj.get(expr.name)
    }
    throw RuntimeError(expr.name, "Only instances have properties.")
}
```

#### 4.6.3 `Expr.Set` 평가

```kotlin
is Expr.Set -> {
    val obj = evaluate(expr.obj)
    if (obj !is GwanInstance) {
        throw RuntimeError(expr.name, "Only instances have fields.")
    }
    val value = evaluate(expr.value)
    obj.set(expr.name, value)
    value
}
```

#### 4.6.4 `Expr.This` 평가

```kotlin
is Expr.This -> lookUpVariable(expr.keyword, expr)
```

`this`는 변수 조회와 동일하게 처리한다. Resolver가 거리를 계산해 놓았으므로
`lookUpVariable()`이 정확한 환경에서 `"this"`를 찾는다.

#### 4.6.5 `Expr.Super` 평가

```kotlin
is Expr.Super -> {
    val distance = locals[expr]!!
    val superclass = environment.getAt(distance, "super") as GwanClass
    // "this"는 항상 "super" 바로 안쪽 환경에 있음
    val obj = environment.getAt(distance - 1, "this") as GwanInstance
    val method = superclass.findMethod(expr.method.lexeme)
        ?: throw RuntimeError(expr.method, "Undefined property '${expr.method.lexeme}'.")
    method.bind(obj)
}
```

**`super` 환경 구조:**

```
... → [super: SuperClass] → [this: instance] → method body
          distance              distance - 1
```

Resolver가 `super`의 거리를 계산하면, `this`는 항상 그보다 1 가까운 환경에 있다.

### 4.7 Resolver 확장

#### 4.7.1 ClassType enum

```kotlin
enum class ClassType {
    NONE,
    CLASS,
    SUBCLASS
}
```

#### 4.7.2 FunctionType 확장

```kotlin
enum class FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
}
```

#### 4.7.3 상태 추가

```kotlin
class Resolver(private val interpreter: Interpreter) {
    private val scopes = ArrayDeque<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE      // 신규
}
```

#### 4.7.4 `Stmt.Class` 분석

```kotlin
is Stmt.Class -> {
    val enclosingClass = currentClass
    currentClass = ClassType.CLASS

    declare(stmt.name)
    define(stmt.name)

    if (stmt.superclass != null) {
        if (stmt.superclass.name.lexeme == stmt.name.lexeme) {
            GwanLang.error(stmt.superclass.name, "A class can't inherit from itself.")
        }
        currentClass = ClassType.SUBCLASS
        resolve(stmt.superclass)

        // super를 위한 스코프
        beginScope()
        scopes.last()["super"] = true
    }

    // this를 위한 스코프
    beginScope()
    scopes.last()["this"] = true

    for (method in stmt.methods) {
        val declaration = if (method.name.lexeme == "init") {
            FunctionType.INITIALIZER
        } else {
            FunctionType.METHOD
        }
        resolveFunction(method, declaration)
    }

    endScope()  // this 스코프

    if (stmt.superclass != null) {
        endScope()  // super 스코프
    }

    currentClass = enclosingClass
}
```

**스코프 구조 (상속 있을 때):**

```
[ ... 외부 스코프 ... ]
[ super: true ]          ← beginScope (superclass != null일 때)
[ this: true ]           ← beginScope
  메서드 분석              ← resolveFunction
[ endScope ]             ← this
[ endScope ]             ← super
```

#### 4.7.5 `Expr.This` 분석

```kotlin
is Expr.This -> {
    if (currentClass == ClassType.NONE) {
        GwanLang.error(expr.keyword, "Can't use 'this' outside of a class.")
        return
    }
    resolveLocal(expr, expr.keyword)
}
```

#### 4.7.6 `Expr.Super` 분석

```kotlin
is Expr.Super -> {
    if (currentClass == ClassType.NONE) {
        GwanLang.error(expr.keyword, "Can't use 'super' outside of a class.")
    } else if (currentClass != ClassType.SUBCLASS) {
        GwanLang.error(expr.keyword, "Can't use 'super' in a class with no superclass.")
    }
    resolveLocal(expr, expr.keyword)
}
```

#### 4.7.7 `Expr.Get` / `Expr.Set` 분석

```kotlin
is Expr.Get -> resolve(expr.obj)
is Expr.Set -> {
    resolve(expr.value)
    resolve(expr.obj)
}
```

프로퍼티 이름은 동적이므로 resolve하지 않는다 (런타임에 인스턴스에서 탐색).

#### 4.7.8 `Stmt.Return` 추가 검증

```kotlin
is Stmt.Return -> {
    if (currentFunction == FunctionType.NONE) {
        GwanLang.error(stmt.keyword, "Can't return from top-level code.")
    }
    if (stmt.value != null) {
        if (currentFunction == FunctionType.INITIALIZER) {
            GwanLang.error(stmt.keyword, "Can't return a value from an initializer.")
        }
        resolve(stmt.value)
    }
}
```

`init()` 에서 `return;` (값 없음)은 허용하지만, `return value;`는 금지한다.

### 4.8 stringify 확장

```kotlin
fun stringify(value: Any?): String {
    if (value == null) return "nil"
    if (value is Double) {
        val text = value.toString()
        if (text.endsWith(".0")) return text.dropLast(2)
        return text
    }
    return value.toString()  // GwanClass, GwanInstance의 toString() 사용
}
```

`GwanClass.toString()` → `"ClassName"`, `GwanInstance.toString()` → `"ClassName instance"`

## 5. 에러 처리 방침

| 상황 | 단계 | 메시지 |
|------|------|--------|
| `this`를 클래스 밖에서 사용 | Resolver | "Can't use 'this' outside of a class." |
| `super`를 클래스 밖에서 사용 | Resolver | "Can't use 'super' outside of a class." |
| `super`를 상속받지 않은 클래스에서 사용 | Resolver | "Can't use 'super' in a class with no superclass." |
| 자기 자신을 상속 | Resolver | "A class can't inherit from itself." |
| `init()`에서 값 반환 | Resolver | "Can't return a value from an initializer." |
| 클래스가 아닌 것을 상속 | Interpreter | RuntimeError: "Superclass must be a class." |
| 인스턴스가 아닌 것에 프로퍼티 접근 | Interpreter | RuntimeError: "Only instances have properties." |
| 인스턴스가 아닌 것에 필드 대입 | Interpreter | RuntimeError: "Only instances have fields." |
| 존재하지 않는 프로퍼티 접근 | Interpreter | RuntimeError: "Undefined property 'name'." |
| 클래스 호출 시 인자 수 불일치 | Interpreter | RuntimeError: "Expected N arguments but got M." (기존 Call 로직) |
| `super.method` 에서 메서드 없음 | Interpreter | RuntimeError: "Undefined property 'method'." |

## 6. 테스트 계획 (TDD 사이클)

| # | 사이클 | 주요 테스트 | 대응 구현 |
|---|--------|-------------|----------|
| 1 | AST 확장 & 파싱 — 빈 클래스 | `class Foo {}` 파싱 → `Stmt.Class(name="Foo", superclass=null, methods=[])` | `Expr.Get/Set/This/Super`, `Stmt.Class`, `classDeclaration()` |
| 2 | 파싱 — 메서드 있는 클래스 | `class Foo { bar() { return 1; } }` → 메서드 리스트 검증 | `classDeclaration()`의 메서드 파싱 |
| 3 | 파싱 — 상속 | `class Bar < Foo {}` → superclass 검증 | `<` 파싱 |
| 4 | 파싱 — 프로퍼티 접근 | `obj.field` → `Expr.Get` | `call()`에 `.` 처리 |
| 5 | 파싱 — 필드 대입 | `obj.field = 1` → `Expr.Set` | `assignment()`에 Get→Set 변환 |
| 6 | 파싱 — this/super | `this.x`, `super.method` → 각각 `Expr.This`, `Expr.Super` | `primary()`에 this/super |
| 7 | GwanInstance — get/set | 필드 저장/조회, 미정의 필드 에러 | `GwanInstance` |
| 8 | GwanClass — 인스턴스 생성 | `Foo()` → GwanInstance 반환, `toString()` 검증 | `GwanClass.call()` |
| 9 | Interpreter — 클래스 선언 & 인스턴스 생성 | `class Foo {} var f = Foo(); print f;` → "Foo instance" | `Stmt.Class`, `Expr.Call` (클래스) |
| 10 | Interpreter — 프로퍼티 get/set | `f.x = 1; print f.x;` → "1" | `Expr.Get`, `Expr.Set` 평가 |
| 11 | GwanFunction.bind & 메서드 호출 | `class Foo { bar() { return 1; } } print Foo().bar();` → "1" | `bind()`, 메서드 디스패치 |
| 12 | this 바인딩 | `class Foo { get() { return this.x; } } var f = Foo(); f.x = 42; print f.get();` → "42" | `Expr.This` 평가 |
| 13 | init 생성자 | `class Foo { init(x) { this.x = x; } } print Foo(5).x;` → "5" | `init` 호출, `isInitializer` |
| 14 | init 반환값 = this | `var f = Foo(1);` 에서 f가 인스턴스인지 검증, init에서 `return;` 허용 | `isInitializer` return 처리 |
| 15 | Resolver — this 클래스 밖 에러 | `print this;` → 에러 | `currentClass` 체크 |
| 16 | Resolver — init에서 값 반환 에러 | `class F { init() { return 1; } }` → 에러 | `FunctionType.INITIALIZER` 체크 |
| 17 | 단일 상속 — 메서드 상속 | `class A { m() {} } class B < A {} B().m()` → 부모 메서드 호출 | `findMethod()` 상속 체인 |
| 18 | 메서드 오버라이드 | `class A { m() { return 1; } } class B < A { m() { return 2; } } print B().m();` → "2" | `findMethod()` 순서 |
| 19 | super 호출 | `class A { m() { return "A"; } } class B < A { m() { return super.m() + "B"; } } print B().m();` → "AB" | `Expr.Super` 평가, super 환경 |
| 20 | Resolver — super 에러 | 상속 없는 클래스에서 `super` → 에러, 클래스 밖 `super` → 에러 | `ClassType` 체크 |
| 21 | Resolver — 자기 상속 에러 | `class A < A {}` → 에러 | 이름 비교 |
| 22 | 런타임 에러 — 비클래스 상속 | `var x = "not a class"; class B < x {}` 실행 시 에러 | superclass 타입 체크 |
| 23 | 런타임 에러 — 비인스턴스 프로퍼티 접근 | `"str".field` → 에러 | Get/Set 타입 체크 |
| 24 | 복합 시나리오 — 클로저 + 클래스 | 메서드 안에서 클로저 캡처, 콜백 패턴 | 전체 파이프라인 |
| 25 | 복합 시나리오 — 다단계 상속 | A < B < C 체인에서 메서드 탐색 | `findMethod()` 재귀 |
| 26 | 기존 테스트 회귀 검증 | `./gradlew test` 전체 통과 | 전체 |
| 27 | 통합 테스트 — classes.gwan | 종합 시나리오 스크립트 실행 | 전체 파이프라인 |

### 테스트 작성 규약
- `ClassTest.kt`: 클래스 전용 테스트. 파싱, Resolver 에러, Interpreter 실행 결과를 모두 포함.
- stdout 캡처: `System.setOut()`으로 출력 검증
- 에러 검증: `GwanLang.hadError` / stderr 캡처
- 기존 테스트(`ScannerTest`, `ParserTest`, `InterpreterTest`)가 매 사이클마다 통과 확인

## 7. 완료 기준 (Definition of Done)

- [ ] `Expr.Get` — 프로퍼티 접근 AST 노드
- [ ] `Expr.Set` — 프로퍼티 대입 AST 노드
- [ ] `Expr.This` — this 참조 AST 노드
- [ ] `Expr.Super` — super 참조 AST 노드
- [ ] `Stmt.Class` — 클래스 선언 AST 노드
- [ ] `Parser` — classDeclaration, `.` 접근, this, super, 필드 대입 파싱
- [ ] `GwanClass` — GwanCallable 구현, findMethod, toString
- [ ] `GwanInstance` — get, set, toString
- [ ] `GwanFunction.bind()` — this 바인딩 환경 생성
- [ ] `GwanFunction.isInitializer` — init 반환값 처리
- [ ] `Interpreter` — Stmt.Class 실행 (superclass 환경 포함)
- [ ] `Interpreter` — Expr.Get 평가
- [ ] `Interpreter` — Expr.Set 평가
- [ ] `Interpreter` — Expr.This 평가 (lookUpVariable)
- [ ] `Interpreter` — Expr.Super 평가 (super/this 환경 조회)
- [ ] `Resolver` — Stmt.Class 분석 (this/super 스코프)
- [ ] `Resolver` — Expr.Get, Expr.Set 분석
- [ ] `Resolver` — Expr.This 분석 (클래스 밖 에러)
- [ ] `Resolver` — Expr.Super 분석 (클래스 밖 / 비서브클래스 에러)
- [ ] `Resolver` — 자기 상속 에러
- [ ] `Resolver` — init에서 값 반환 에러
- [ ] `FunctionType` — INITIALIZER, METHOD 추가
- [ ] `ClassType` enum 추가 — NONE, CLASS, SUBCLASS
- [ ] 기존 Phase 1~6 테스트 전체 통과 (회귀 없음)
- [ ] `./gradlew build` 성공
- [ ] `./gradlew test` 전체 통과
- [ ] `examples/classes.gwan` 정상 동작
- [ ] `docs/CHANGELOG.md`에 Phase 7 완료 항목 추가
- [ ] `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 8. 작업 분해 (권장 커밋 단위)

1. `docs: Phase 7 Classes 스펙 문서 작성`
2. `feat: AST 확장 (Expr.Get/Set/This/Super, Stmt.Class)`
3. `feat: Parser 클래스 선언 및 프로퍼티 접근 파싱`
4. `feat: GwanInstance 프로퍼티 get/set 구현`
5. `feat: GwanClass 인스턴스 생성 및 메서드 저장`
6. `feat: GwanFunction.bind()로 this 바인딩`
7. `feat: Interpreter 클래스 선언, Get/Set/This 실행`
8. `feat: init 생성자 및 isInitializer 반환값 처리`
9. `feat: 단일 상속 — superclass 체인, super 환경`
10. `feat: Resolver 클래스/this/super 정적 분석`
11. `test: 클래스 통합 테스트 (상속, 오버라이드, 에러 케이스)`
12. `docs: Phase 7 완료 기록`
