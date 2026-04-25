# GwanLang — 나만의 프로그래밍 언어 프로젝트 스펙

> **구현 언어:** Kotlin  
> **언어 유형:** 범용 스크립트 언어 (Lox 스타일)  
> **실행 방식:** Tree-walking Interpreter  
> **참고:** Crafting Interpreters (Robert Nystrom)

---

## 1. 언어 개요

GwanLang은 동적 타입의 스크립트 언어로, 변수, 제어 흐름, 함수, 클로저, 클래스를 지원한다.

```
// Hello World
print "Hello, GwanLang!";

// 변수와 제어 흐름
var name = "World";
if (name == "World") {
  print "Hello, " + name + "!";
}

// 함수와 클로저
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

// 클래스
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
}
```

---

## 2. 마일스톤 로드맵

### Phase 1: Scanner (Lexer) — 토큰화
**목표:** 소스코드 문자열 → 토큰 리스트 변환

| 항목 | 설명 |
|------|------|
| 입력 | 소스코드 문자열 |
| 출력 | `List<Token>` |
| 핵심 개념 | 상태 머신, 문자 소비, 토큰 분류 |

**지원 토큰:**
- 단일 문자: `( ) { } , . - + ; * /`
- 비교/대입: `! != = == > >= < <=`
- 리터럴: 문자열(`"..."`), 숫자(`123`, `3.14`)
- 식별자 및 키워드: `and, class, else, false, for, fun, if, nil, or, print, return, super, this, true, var, while`

**완료 기준:**
- [x] Token, TokenType 정의
- [x] Scanner 클래스 구현
- [x] 에러 리포팅 (줄 번호 포함)
- [x] 테스트: 모든 토큰 타입 스캔 확인

---

### Phase 2: Parser — 구문 분석
**목표:** 토큰 리스트 → AST(Abstract Syntax Tree) 변환

| 항목 | 설명 |
|------|------|
| 입력 | `List<Token>` |
| 출력 | `List<Stmt>` (AST) |
| 핵심 개념 | Recursive Descent, Precedence Climbing |

**문법 (EBNF):**
```ebnf
program        → declaration* EOF ;
declaration    → varDecl | funDecl | classDecl | statement ;
varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
funDecl        → "fun" function ;
function       → IDENTIFIER "(" parameters? ")" block ;
classDecl      → "class" IDENTIFIER ( "<" IDENTIFIER )? "{" function* "}" ;

statement      → exprStmt | printStmt | block | ifStmt | whileStmt | forStmt | returnStmt ;
block          → "{" declaration* "}" ;
ifStmt         → "if" "(" expression ")" statement ( "else" statement )? ;
whileStmt      → "while" "(" expression ")" statement ;
forStmt        → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
returnStmt     → "return" expression? ";" ;

expression     → assignment ;
assignment     → ( call "." )? IDENTIFIER "=" assignment | logic_or ;
logic_or       → logic_and ( "or" logic_and )* ;
logic_and      → equality ( "and" equality )* ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary | call ;
call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
primary        → NUMBER | STRING | "true" | "false" | "nil" | "this"
               | "(" expression ")" | IDENTIFIER | "super" "." IDENTIFIER ;
```

**완료 기준:**
- [ ] Expr sealed class 계층 정의
- [ ] Stmt sealed class 계층 정의
- [ ] Parser 클래스 (Recursive Descent)
- [ ] 구문 에러 복구 (synchronize)
- [ ] 테스트: 파싱 + AST 출력 (Pretty Printer)

---

### Phase 3: Evaluator — 표현식 평가
**목표:** AST의 표현식 노드를 평가하여 값을 반환

| 항목 | 설명 |
|------|------|
| 입력 | `Expr` (AST 노드) |
| 출력 | `Any?` (런타임 값) |
| 핵심 개념 | Visitor 패턴 (Kotlin에서는 when 표현식), 타입 검사 |

**타입 시스템 (런타임):**
- `nil` → Kotlin `null`
- `Boolean` → Kotlin `Boolean`
- `Number` → Kotlin `Double`
- `String` → Kotlin `String`

**완료 기준:**
- [ ] 산술/비교/논리 연산 평가
- [ ] 런타임 타입 에러 처리
- [ ] Truthiness 규칙: `nil`, `false` → falsy, 나머지 → truthy
- [ ] 문자열 연결 (`+`)
- [ ] 테스트: REPL에서 표현식 평가

---

### Phase 4: Statements & State — 문장과 상태
**목표:** 변수 선언, 대입, 스코프, 제어 흐름 구현

**완료 기준:**
- [ ] Environment (스코프 체인) 구현
- [ ] 변수 선언 / 대입
- [ ] print 문
- [ ] 블록 스코프 `{ ... }`
- [ ] if / else
- [ ] while / for 루프
- [ ] 테스트: 피보나치 출력 등

---

### Phase 5: Functions & Closures — 함수
**목표:** 일급 함수, 클로저, return 문 구현

**완료 기준:**
- [ ] 함수 선언 및 호출
- [ ] 매개변수 바인딩
- [ ] return 문 (예외 기반 제어 흐름)
- [ ] 클로저 (환경 캡처)
- [ ] 네이티브 함수 (`clock()` 등)
- [ ] 테스트: 재귀, 클로저 카운터

---

### Phase 6: Resolver — 정적 분석
**목표:** 변수 바인딩을 정적으로 해석하여 성능/정확성 개선

**완료 기준:**
- [ ] Resolver 클래스 (AST 순회)
- [ ] 변수 사용 전 선언 검증
- [ ] 같은 스코프 중복 선언 에러
- [ ] 클로저 변수 거리 계산
- [ ] 테스트: 스코프 관련 엣지 케이스

---

### Phase 7: Classes — 클래스와 상속
**목표:** 클래스 선언, 인스턴스, 메서드, 상속 구현

**완료 기준:**
- [ ] 클래스 선언 및 인스턴스 생성
- [ ] 프로퍼티 get/set
- [ ] 메서드와 `this` 바인딩
- [ ] `init()` 생성자
- [ ] 단일 상속 (`<`)
- [ ] `super` 키워드
- [ ] 테스트: 상속 체인, 메서드 오버라이드

---

## 3. 프로젝트 구조

```
gwanlang/
├── build.gradle.kts
├── src/
│   ├── main/kotlin/gwanlang/
│   │   ├── GwanLang.kt         # 진입점 (REPL + 파일 실행)
│   │   ├── Token.kt            # Token, TokenType
│   │   ├── Scanner.kt          # Phase 1: Lexer
│   │   ├── Expr.kt             # Expression AST 노드
│   │   ├── Stmt.kt             # Statement AST 노드
│   │   ├── Parser.kt           # Phase 2: Parser
│   │   ├── Interpreter.kt      # Phase 3-5: Tree-walking Interpreter
│   │   ├── Environment.kt      # Phase 4: 스코프/변수 관리
│   │   ├── Resolver.kt         # Phase 6: 정적 분석
│   │   ├── GwanFunction.kt     # Phase 5: 함수 객체
│   │   ├── GwanClass.kt        # Phase 7: 클래스 객체
│   │   ├── GwanInstance.kt     # Phase 7: 인스턴스 객체
│   │   └── RuntimeError.kt     # 런타임 에러
│   └── test/kotlin/gwanlang/
│       ├── ScannerTest.kt
│       ├── ParserTest.kt
│       ├── InterpreterTest.kt
│       └── testdata/           # .gwan 테스트 스크립트
│           ├── expressions.gwan
│           ├── scoping.gwan
│           ├── closures.gwan
│           └── classes.gwan
└── docs/
    ├── SPEC.md                 # 이 문서
    ├── GRAMMAR.md              # 문법 정의 (EBNF)
    └── CHANGELOG.md            # 구현 진행 로그
```

---

## 4. 핵심 Kotlin 설계 패턴

### AST 노드 — sealed class + data class

```kotlin
sealed class Expr {
    data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Literal(val value: Any?) : Expr()
    data class Unary(val op: Token, val right: Expr) : Expr()
    data class Variable(val name: Token) : Expr()
    data class Assign(val name: Token, val value: Expr) : Expr()
    data class Logical(val left: Expr, val op: Token, val right: Expr) : Expr()
    data class Call(val callee: Expr, val paren: Token, val arguments: List<Expr>) : Expr()
    data class Get(val obj: Expr, val name: Token) : Expr()
    data class Set(val obj: Expr, val name: Token, val value: Expr) : Expr()
    data class This(val keyword: Token) : Expr()
    data class Super(val keyword: Token, val method: Token) : Expr()
}
```

### 평가 — when 표현식

```kotlin
fun evaluate(expr: Expr): Any? = when (expr) {
    is Expr.Literal  -> expr.value
    is Expr.Grouping -> evaluate(expr.expression)
    is Expr.Unary    -> {
        val right = evaluate(expr.right)
        when (expr.op.type) {
            TokenType.MINUS -> -(right as Double)
            TokenType.BANG  -> !isTruthy(right)
            else -> null
        }
    }
    is Expr.Binary   -> evaluateBinary(expr)
    // ... 나머지 케이스
}
```

### Environment — 스코프 체인

```kotlin
class Environment(val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        values[name.lexeme]?.let { return it }
        enclosing?.let { return it.get(name) }
        throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun assign(name: Token, value: Any?) {
        if (name.lexeme in values) { values[name.lexeme] = value; return }
        enclosing?.assign(name, value) ?: throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }
}
```

---

## 5. 학습 참고 자료

| 자료 | 설명 |
|------|------|
| [Crafting Interpreters](https://craftinginterpreters.com) | Lox 언어 전체 구현 (Java → C). 이 프로젝트의 주 참고서 |
| [Writing An Interpreter In Go](https://interpreterbook.com) | Go로 Monkey 언어 구현. 다른 시각 제공 |
| [Build Your Own Lisp](http://buildyourownlisp.com) | C로 Lisp 구현. 더 간단한 문법 |
| [PL/0 Wikipedia](https://en.wikipedia.org/wiki/PL/0) | 컴파일러 교육용 미니 언어 참고 |

---

## 6. 진행 추적

| Phase | 상태 | 시작일 | 완료일 | 비고 |
|-------|------|--------|--------|------|
| 1. Scanner | ✅ 완료 | 2026-04-11 | 2026-04-11 | TDD 사이클 12개, 테스트 26건 |
| 2. Parser | ✅ 완료 | 2026-04-19 | 2026-04-19 | 표현식 전용, TDD 사이클 12개 |
| 3. Evaluator | ⬜ 미시작 | | | |
| 4. Statements | ⬜ 미시작 | | | |
| 5. Functions | ⬜ 미시작 | | | |
| 6. Resolver | ⬜ 미시작 | | | |
| 7. Classes | ⬜ 미시작 | | | |
