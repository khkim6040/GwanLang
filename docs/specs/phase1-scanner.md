# Phase 1: Scanner 구현 스펙

> 상위 문서: [`GWANLANG_SPEC.md`](../../GWANLANG_SPEC.md) §2 Phase 1
> 참고: Crafting Interpreters Ch. 4 "Scanning"

## 1. 목적

GwanLang 소스코드 문자열(`String`)을 토큰 리스트(`List<Token>`)로 변환하는
Scanner(Lexer)를 TDD로 구현한다. 이후 Phase 2(Parser)의 입력이 된다.

```
"var x = 1;"  ──►  Scanner  ──►  [VAR, IDENTIFIER(x), EQUAL, NUMBER(1.0), SEMICOLON, EOF]
```

## 2. 범위

### In Scope
- `Token`, `TokenType` 타입 정의
- 단일 문자 토큰: `( ) { } , . - + ; * /`
- 1~2 문자 연산자: `! != = == > >= < <=`
- 리터럴: 문자열(`"..."`), 숫자(`123`, `3.14`)
- 식별자 + 16개 예약어
- 공백/탭/개행 skip, 줄 번호 추적
- 단일 라인 주석 `// ...`
- 에러 리포팅(줄 번호 포함) + 복구 후 계속 스캔
- Phase 1 수준 REPL/파일 실행 진입점 (`GwanLang.kt`)

### Out of Scope (Phase 1에서 다루지 않음)
- 블록 주석 `/* ... */`
- 문자열 이스케이프 시퀀스 (`\n`, `\"`, `\\` 등)
- 숫자 지수 표기 (`1e5`) 및 16진수 리터럴
- Parser / AST (Phase 2)
- 런타임 평가 (Phase 3+)

## 3. 산출물

### 프로덕션 코드 (`src/main/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `TokenType.kt` | `TokenType` enum |
| `Token.kt` | `Token` data class |
| `Scanner.kt` | `Scanner` 클래스 |
| `GwanLang.kt` | 진입점 (REPL + 파일 실행, Phase 1 수준) |

### 테스트 코드 (`src/test/kotlin/gwanlang/`)
| 파일 | 내용 |
|------|------|
| `ScannerTest.kt` | 토큰화 테스트 전체 |

### 기타
- `examples/scanner-demo.gwan` — Scanner 기능 시연 예제
- `docs/CHANGELOG.md` — Phase 1 완료 기록
- `GWANLANG_SPEC.md` §6 진행 추적표 업데이트

## 4. 상세 설계

### 4.1 `TokenType`

```kotlin
enum class TokenType {
    // 단일 문자 (11종)
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // 1~2 문자 (8종)
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // 리터럴 (3종)
    IDENTIFIER, STRING, NUMBER,

    // 키워드 (16종)
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF
}
```

### 4.2 `Token`

```kotlin
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
)
```

- `lexeme`: 원본 소스의 해당 구간 문자열
- `literal`:
  - `STRING` → `String` (따옴표 제외한 내용)
  - `NUMBER` → `Double`
  - 그 외 → `null`
- `line`: 토큰이 `addToken(...)`으로 기록되는 시점의 줄 번호 (1-based)
  - 대부분의 토큰은 시작 줄과 동일하다.
  - 여러 줄에 걸친 문자열 토큰은 종료 줄 번호가 저장된다.

### 4.3 `Scanner`

```kotlin
class Scanner(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0     // 현재 렉심(lexeme)의 첫 문자 인덱스
    private var current = 0   // 다음 읽을 문자 인덱스
    private var line = 1

    fun scanTokens(): List<Token>

    private fun scanToken()
    private fun isAtEnd(): Boolean
    private fun advance(): Char
    private fun match(expected: Char): Boolean
    private fun peek(): Char
    private fun peekNext(): Char
    private fun string()
    private fun number()
    private fun identifier()
    private fun addToken(type: TokenType, literal: Any? = null)
    private fun isDigit(c: Char): Boolean
    private fun isAlpha(c: Char): Boolean
    private fun isAlphaNumeric(c: Char): Boolean

    companion object {
        private val KEYWORDS = mapOf(
            "and" to AND, "class" to CLASS, "else" to ELSE,
            "false" to FALSE, "for" to FOR, "fun" to FUN,
            "if" to IF, "nil" to NIL, "or" to OR,
            "print" to PRINT, "return" to RETURN, "super" to SUPER,
            "this" to THIS, "true" to TRUE, "var" to VAR, "while" to WHILE,
        )
    }
}
```

### 4.4 스캔 알고리즘

```
scanTokens:
    while (!isAtEnd):
        start = current
        scanToken()
    tokens.add(Token(EOF, "", null, line))
    return tokens

scanToken:
    c = advance()
    switch c:
      '('      -> addToken(LEFT_PAREN)
      ...
      '!'      -> addToken(if match('=') BANG_EQUAL else BANG)
      '='      -> addToken(if match('=') EQUAL_EQUAL else EQUAL)
      '/'      -> if match('/'): // 주석, 개행까지 skip
                  else: addToken(SLASH)
      ' ' '\t' '\r' -> skip
      '\n'     -> line++
      '"'      -> string()
      digit    -> number()
      alpha/_  -> identifier()
      else     -> GwanLang.error(line, "Unexpected character.")
```

### 4.5 문자열 / 숫자 / 식별자 세부

- **문자열**: `"` 만나면 종료 `"` 까지 소비. 중간 개행 허용 + `line++`.
  종료 전에 EOF면 `GwanLang.error(line, "Unterminated string.")` 후 반환.
- **숫자**: 연속 digit → 선택적 `.` + digit → `NUMBER`. literal은 `String.toDouble()`.
  Crafting Interpreters 규칙에 따라 `.5`, `5.`는 별개 토큰 분리(숫자 앞뒤 반드시 digit).
- **식별자**: `isAlpha(_first) + isAlphaNumeric(*)`. 완성된 lexeme을 `KEYWORDS`에서 조회,
  없으면 `IDENTIFIER`.

### 4.6 에러 리포팅

`GwanLang.kt`에 최소 에러 채널을 둔다:

```kotlin
object GwanLang {
    var hadError: Boolean = false
    fun error(line: Int, message: String) {
        System.err.println("[line $line] Error: $message")
        hadError = true
    }
}
```

- 에러 발생해도 Scanner는 계속 진행(복구).
- 파일 실행 시 `hadError == true`이면 종료 코드 65 반환.
- REPL은 에러 후 `hadError = false`로 리셋하고 다음 입력 대기.

### 4.7 진입점 (`GwanLang.kt`, Phase 1 수준)

- `main(args)`:
  - `args.size > 1` → usage 출력
  - `args.size == 1` → `runFile(args[0])`
  - `args.isEmpty()` → `runPrompt()` (REPL)
- `run(source)`: `Scanner(source).scanTokens()` → 토큰 각각 `println`

## 5. 테스트 계획 (TDD 사이클)

순서대로 각 사이클을 Red → Green → Refactor로 진행한다.
각 사이클은 테스트 1~수 개 + 그에 대응하는 최소 구현을 포함한다.

| # | 사이클 | 주요 테스트 |
|---|--------|-------------|
| 1 | Token, TokenType 정의 | Token 생성/필드 접근, TokenType enum 존재 |
| 2 | 빈 입력 | `""` → `[EOF]` |
| 3 | 단일 문자 토큰 | `(){},.+-;*` → 각 TokenType |
| 4 | `/` & 주석 | `/` → SLASH / `// hi` → EOF만 |
| 5 | 1~2 문자 연산자 | `! != = == > >= < <=` |
| 6 | 공백/개행 | 공백 skip + `\n` 시 line 증가 |
| 7 | 문자열 리터럴 | `"hello"` → STRING(hello) / 미종료 문자열 에러 |
| 8 | 숫자 리터럴 | `123` → 123.0 / `3.14` → 3.14 |
| 9 | 식별자 | `foo`, `_bar`, `abc123` → IDENTIFIER |
| 10 | 키워드 16종 | 각 키워드 → 해당 TokenType |
| 11 | 에러 처리 | `@` → error 보고 + 이후 스캔 계속 |
| 12 | 통합 | `var x = 1;`, `examples/scanner-demo.gwan` 전체 |

### 테스트 작성 규약
- JUnit 5 사용. 테스트 이름은 백틱 감싼 한글 설명 또는 `@DisplayName`.
- 헬퍼: `fun scan(src: String): List<Token>` 정도만 ScannerTest 상단에 두고 사용.
- 각 테스트는 독립적이고 1~3 assert 이내로 유지.

## 6. 완료 기준 (Definition of Done)

- [x] 위 테스트 계획의 모든 사이클 테스트 존재 및 통과
- [x] `./gradlew build` 성공 (경고 0)
- [x] `./gradlew run` 으로 REPL 진입 → 입력 → 토큰 출력 확인
- [x] `./gradlew run --args="examples/scanner-demo.gwan"` 정상 동작
- [x] 에러 케이스(미종료 문자열, 잘못된 문자)에서 line 포함 에러 메시지 출력 + 종료 코드 65
- [x] `docs/CHANGELOG.md`에 Phase 1 완료 항목 추가
- [x] `GWANLANG_SPEC.md` §6 진행 추적표에서 Phase 1을 ✅ 로 업데이트

## 7. 작업 분해 (권장 커밋 단위)

CLAUDE.md의 커밋 granularity 규칙에 맞춰 아래 단위로 커밋한다.

1. `feat: TokenType enum 및 Token data class 추가`
2. `feat: Scanner 골격 + 빈 입력/EOF 처리`
3. `feat: 단일 문자 토큰 스캔`
4. ``feat: `/` 연산자 및 단일 라인 주석 처리``
5. `feat: 1~2 문자 비교/대입 연산자 스캔`
6. `feat: 공백 skip 및 줄 번호 추적`
7. `feat: 문자열 리터럴 스캔 + 미종료 에러`
8. `feat: 숫자 리터럴 스캔`
9. `feat: 식별자 및 키워드 스캔`
10. `feat: 에러 리포팅 채널 및 복구 스캔`
11. `feat: GwanLang 진입점 — REPL 및 파일 실행`
12. `docs: Phase 1 완료 기록 및 예제 추가`
