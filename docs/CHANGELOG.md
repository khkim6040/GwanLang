# Changelog

## [Unreleased]

### Phase 6: Resolver — 정적 변수 바인딩 분석
- `Resolver` 클래스 — AST 순회, 스코프 스택 기반 변수 바인딩 거리 계산
  - 블록/함수/매개변수 스코프 분석
  - `resolveLocal()` — 변수 참조에서 선언까지의 스코프 거리 계산
  - `resolveFunction()` — 함수 본문 분석, `currentFunction` 추적
- 정적 에러 검출
  - 같은 스코프 중복 선언: "Already a variable with this name in this scope."
  - 자기 참조 초기화: "Can't read local variable in its own initializer."
  - 최상위 `return`: "Can't return from top-level code."
- `FunctionType` enum — NONE, FUNCTION (return 문맥 검증용)
- `Environment` 확장
  - `getAt(distance, name)` — 거리 기반 변수 조회
  - `assignAt(distance, name, value)` — 거리 기반 변수 대입
  - `ancestor(distance)` — N단계 상위 환경 반환
- `Interpreter` 변경
  - `locals: IdentityHashMap<Expr, Int>` — Resolver가 계산한 거리 저장
  - `resolve(expr, depth)` — Resolver에서 호출
  - `lookUpVariable(name, expr)` — 거리 기반 변수 조회 분기
  - `Expr.Variable`, `Expr.Assign` — 거리 기반 조회/대입으로 변경
- `GwanLang.kt` 파이프라인 변경: Scanner → Parser → Resolver → Interpreter
- `examples/resolver-demo.gwan` — 클로저 스코프 정확성, 카운터, 섀도잉 시연
- 테스트: `ResolverTest`, `EnvironmentTest`, `InterpreterTest` 확장 — TDD 사이클 15개 기반

### Phase 5: Functions & Closures — 함수와 클로저
- `Expr.Call` — 함수 호출 표현식 AST 노드
- `Stmt.Function` — 함수 선언문, `Stmt.Return` — return 문
- `GwanCallable` 인터페이스 — 호출 가능 객체 추상화 (arity, call)
- `GwanFunction` 클래스 — 사용자 정의 함수 런타임 객체
  - 클로저: 선언 시점의 Environment 캡처
  - 매개변수 바인딩, 본문 실행, Return 예외 처리
- `Return` 예외 클래스 — return 제어 흐름 (스택 트레이스 생략)
- `Parser` 확장
  - `fun` 선언 파싱 (매개변수 포함, 255개 제한)
  - call 표현식 파싱 (인자 255개 제한, 연쇄 호출)
  - `return` 문 파싱 (값 있음/없음)
- `Interpreter` 확장
  - 함수 선언 실행 (환경 캡처), 함수 호출 (GwanCallable 디스패치)
  - 인자 개수 불일치 / 호출 불가 값 RuntimeError
  - 네이티브 함수 `clock()` 등록
- `examples/functions-demo.gwan` — 함수, 클로저, 재귀, 고차 함수 시연
- 테스트: `GwanFunctionTest`, `FunctionParserTest`, `InterpreterTest` 확장 — TDD 사이클 22개 기반

### Phase 4: Statements & State — 문장과 상태
- `Stmt` sealed class — 6종 (Expression, Print, Var, Block, If, While)
- `Expr` 확장 — Variable, Assign, Logical 서브타입 추가
- `Environment` 클래스 — 렉시컬 스코프 체인 (변수 맵의 연결 리스트)
  - define/get/assign, 스코프 섀도잉, 미정의 변수 RuntimeError
- `Parser` 확장 — 문장/선언 파싱
  - print, expression, var 선언, 블록, if/else, while, for 파싱
  - for → while 디슈가링 (별도 AST 노드 없음)
  - assignment, 논리 연산자 (and, or)
  - 에러 복구 (synchronize)
- `Interpreter` 확장 — 문장 실행
  - execute(Stmt), executeBlock, Environment 기반 변수/스코프
  - if/else, while 실행
  - 논리 연산자 short-circuit 평가 (원래 값 반환)
- `GwanLang.kt` 파이프라인 변경: `List<Stmt>` 실행, 전역 Interpreter 인스턴스
- `examples/statements-demo.gwan` — 변수, 스코프, 제어흐름 시연 예제
- 테스트: `StmtTest`, `EnvironmentTest`, `StatementParserTest`, `InterpreterTest` 확장 — TDD 사이클 27개 기반

### Phase 3: Evaluator — 표현식 평가
- `Interpreter` 클래스 — `when` 표현식 기반 Tree-walking 평가기
  - 산술 연산: `+`, `-`, `*`, `/`
  - 비교 연산: `>`, `>=`, `<`, `<=`
  - 동등 연산: `==`, `!=`
  - 단항 연산: `-` (부호 반전), `!` (논리 부정)
  - 문자열 연결: `+` (양쪽 모두 String일 때)
  - Truthiness: `nil`, `false`만 falsy
- `RuntimeError` 예외 클래스 — 런타임 타입 에러 리포팅
  - 0으로 나누기 RuntimeError
  - 타입 불일치 RuntimeError (줄 번호 포함)
- `GwanLang.kt` 파이프라인 변경: Scanner → Parser → Interpreter
  - `runtimeError()` 메서드, `hadRuntimeError` 플래그, exit code 70
  - REPL에서 `hadRuntimeError` 리셋
- `examples/evaluator-demo.gwan` — 표현식 평가 시연 예제
- 테스트: `InterpreterTest` — TDD 사이클 14개 기반

### Phase 2: Parser (표현식) — 구문 분석
- `Expr` sealed class — 4종 (Binary, Grouping, Literal, Unary)
- `Parser` 클래스 — Recursive Descent 방식 표현식 파싱
  - 6단계 우선순위: equality → comparison → term → factor → unary → primary
  - 좌결합 이항 연산자, 우결합 단항 연산자
  - 리터럴: 숫자, 문자열, true, false, nil
  - 그룹화: `(expression)`
  - 파싱 에러 시 토큰 위치 포함 에러 메시지 + null 반환
- `AstPrinter` — S-expression 형식 AST 출력 (예: `(+ 1.0 (* 2.0 3.0))`)
- `GwanLang.kt` 파이프라인 확장: Scanner → Parser → AstPrinter
- `GwanLang.error(token, message)` 오버로드 추가
- `examples/parser-demo.gwan` — 표현식 파싱 시연 예제
- 테스트: `ExprTest`, `ParserTest`, `AstPrinterTest` — TDD 사이클 12개 기반

### Phase 1: Scanner (Lexer) — 토큰화
- `TokenType` enum — 39종(단일 문자 11, 연산자 8, 리터럴 3, 키워드 16, EOF)
- `Token` data class — type/lexeme/literal/line 필드
- `Scanner` 클래스 — 소스 문자열 → `List<Token>`
  - 단일 문자 토큰, 1~2 문자 비교/대입 연산자
  - 단일 라인 주석 `// ...`
  - 공백/탭/캐리지리턴 skip, 개행 시 줄 번호 증가
  - 문자열/숫자/식별자 리터럴, 16개 예약어 인식
  - 미종료 문자열 및 예상치 못한 문자에 대한 에러 리포트 + 복구 스캔
- `GwanLang` — 전역 에러 채널(`hadError`) 및 진입점
  - `main`: 인자 1개면 파일 실행, 없으면 REPL
  - 파일 실행 중 에러 발생 시 exit code 65
- `examples/scanner-demo.gwan` — Scanner 시연 예제
- 테스트: `TokenTest`, `ScannerTest` — 빈 입력부터 통합 스캔까지 26개 케이스 TDD 사이클 12개 기반

### Phase 0: 프로젝트 초기 설정
- Gradle (Kotlin DSL) 프로젝트 구조 생성
- JUnit 5 테스트 환경 설정
- 디렉토리 구조: `src/main/kotlin/gwanlang/`, `src/test/kotlin/gwanlang/testdata/`, `docs/`, `examples/`
