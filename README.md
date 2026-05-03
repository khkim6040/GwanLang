# GwanLang

Kotlin으로 구현하는 동적 타입 스크립트 언어의 Tree-walking Interpreter.
[Crafting Interpreters](https://craftinginterpreters.com/) (Robert Nystrom) Part II를 기반으로 한다.

## 언어 미리보기

```
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

// 클래스와 상속
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

var dog = Dog("Rex");
dog.speak(); // Rex barks!
```

## 주요 기능

- **동적 타입 시스템** — `nil`, `Boolean`, `Number`(Double), `String`
- **변수와 렉시컬 스코프** — 블록 스코프, 섀도잉
- **제어 흐름** — `if`/`else`, `while`, `for`
- **일급 함수와 클로저** — 함수를 값으로 전달, 환경 캡처
- **클래스와 단일 상속** — `this`, `super`, `init()` 생성자
- **정적 변수 바인딩** — Resolver를 통한 사전 분석
- **네이티브 함수** — `clock()`

## 빌드 및 실행

**요구 사항:** JDK 17+

```bash
# 빌드
./gradlew build

# .gwan 스크립트 실행
./gradlew run --args="path/to/script.gwan"

# REPL 모드
./gradlew run

# 테스트
./gradlew test
```

## 인터프리터 파이프라인

```
소스코드 (.gwan) → Scanner → 토큰 리스트 → Parser → AST → Resolver → Interpreter → 출력
```

| 컴포넌트 | 역할 |
|----------|------|
| Scanner | 소스 문자열을 토큰 리스트로 변환 |
| Parser | Recursive Descent 방식으로 AST 생성 |
| Resolver | 정적 변수 바인딩 거리 계산 |
| Interpreter | `when` 표현식 기반 Tree-walking 평가 |

## 프로젝트 구조

```
src/main/kotlin/gwanlang/
├── GwanLang.kt          # 진입점 (REPL + 파일 실행)
├── Token.kt             # Token, TokenType
├── Scanner.kt           # 렉서
├── Expr.kt              # Expression AST 노드 (sealed class)
├── Stmt.kt              # Statement AST 노드 (sealed class)
├── Parser.kt            # Recursive Descent 파서
├── Interpreter.kt       # Tree-walking 평가기
├── Environment.kt       # 렉시컬 스코프 체인
├── Resolver.kt          # 정적 변수 바인딩 분석
├── GwanFunction.kt      # 함수 런타임 객체
├── GwanClass.kt         # 클래스 런타임 객체
├── GwanInstance.kt      # 인스턴스 런타임 객체
└── RuntimeError.kt      # 런타임 에러
```

## 구현 진행 상황

| Phase | 내용 | 상태 |
|-------|------|------|
| 1 | Scanner — 토큰화 | Done |
| 2 | Parser — 구문 분석 | Done |
| 3 | Evaluator — 표현식 평가 | Done |
| 4 | Statements — 변수, 스코프, 제어흐름 | Done |
| 5 | Functions — 함수, 클로저, return | Done |
| 6 | Resolver — 정적 변수 바인딩 | Done |
| 7 | Classes — 클래스, 상속, this, super | Done |

각 Phase의 상세 스펙은 `docs/specs/` 하위 문서를 참고한다.

## 문법 (EBNF 요약)

```ebnf
program     → declaration* EOF ;
declaration → classDecl | funDecl | varDecl | statement ;
classDecl   → "class" IDENTIFIER ( "<" IDENTIFIER )? "{" function* "}" ;
funDecl     → "fun" function ;
varDecl     → "var" IDENTIFIER ( "=" expression )? ";" ;
statement   → exprStmt | printStmt | block | ifStmt | whileStmt | forStmt | returnStmt ;
```

전체 문법 정의는 [GWANLANG_SPEC.md](GWANLANG_SPEC.md) 참고.

## 참고 자료

- [Crafting Interpreters](https://craftinginterpreters.com/) — Robert Nystrom
