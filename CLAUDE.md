# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드이다.

## 프로젝트 개요

GwanLang은 Kotlin으로 구현하는 동적 타입 스크립트 언어(Lox 스타일)의 Tree-walking Interpreter이다.
Crafting Interpreters(Robert Nystrom) Part II를 기반으로 한다. 언어 스펙 상세는 `GWANLANG_SPEC.md` 참고.

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "gwanlang.ScannerTest"

# 특정 테스트 메서드 실행
./gradlew test --tests "gwanlang.ScannerTest.특정_테스트_메서드명"

# .gwan 스크립트 실행
./gradlew run --args="path/to/script.gwan"

# REPL 모드
./gradlew run
```

## 아키텍처

Kotlin (JVM) 프로젝트, Gradle (Kotlin DSL) 빌드. 패키지: `gwanlang`.

**인터프리터 파이프라인:**
```
소스코드 (.gwan) → Scanner → 토큰 리스트 → Parser → AST (Expr/Stmt) → Resolver → Interpreter → 출력
```

**주요 컴포넌트** (모두 `src/main/kotlin/gwanlang/` 하위):
- `GwanLang.kt` — 진입점 (REPL + 파일 실행)
- `Scanner.kt` — 렉서: 소스 문자열 → `List<Token>`
- `Parser.kt` — Recursive Descent: 토큰 → AST
- `Interpreter.kt` — `when` 표현식 기반 Tree-walking 평가기
- `Environment.kt` — 렉시컬 스코프 체인 (변수 맵의 연결 리스트)
- `Resolver.kt` — 정적 변수 바인딩 분석 (인터프리터 실행 전 사전 패스)
- `GwanFunction.kt` / `GwanClass.kt` / `GwanInstance.kt` — `GwanCallable` 구현 런타임 객체

**AST 설계:** `Expr`와 `Stmt` 모두 `sealed class` + `data class` 서브타입. Visitor 패턴 대신 `when`으로 패턴 매칭.

## 구현 Phase

7단계 순차 진행. 다음 Phase 시작 전 이전 Phase 테스트가 모두 통과해야 한다.
진행 상태는 `GWANLANG_SPEC.md` 6절에서 추적.

| Phase | 모듈 | 추가 기능 |
|-------|------|----------|
| 1 | Scanner | 토큰화 |
| 2 | Parser | AST 생성 |
| 3 | Evaluator | 표현식 평가 (산술, 비교, 논리) |
| 4 | Statements | 변수, 스코프, 제어흐름 (if/while/for) |
| 5 | Functions | 함수 선언/호출, 클로저, return |
| 6 | Resolver | 정적 변수 바인딩 |
| 7 | Classes | 클래스, 상속, this, super |

## 코딩 컨벤션

- **AST 노드:** `sealed class` + `data class` 서브타입, `when`으로 매칭
- **네이밍:** 클래스 `PascalCase`, 함수/변수 `camelCase`, 상수 `UPPER_SNAKE`
- **GwanLang 관련 클래스:** `Gwan~` 접두사 (GwanFunction, GwanClass, GwanInstance)
- **nil:** Kotlin `null`로 표현 (nullable 타입 활용)
- **숫자:** 모두 `Double` (정수/실수 구분 없음)
- **에러 처리:**
  - 스캔/파싱 에러: 줄 번호 포함 리포팅, 복구 후 계속 진행
  - 런타임 에러: `RuntimeError` throw → 최상위에서 catch
  - return 문: `Return` 예외로 제어 흐름 탈출 (Crafting Interpreters 방식)
- **테스트:** JUnit 5. 테스트 네이밍: 백틱 감싼 설명적 이름 또는 `@DisplayName`
- **테스트 데이터:** `src/test/kotlin/gwanlang/testdata/`에 `.gwan` 스크립트

## TDD 규칙 (Red-Green-Refactor)

모든 구현은 반드시 TDD 사이클을 따른다.

1. **RED — 실패하는 테스트 먼저 작성**
   - 프로덕션 코드보다 테스트를 먼저 작성한다
   - 테스트를 실행하여 실패를 확인한다 (`./gradlew test`)
   - 테스트가 바로 통과하면 테스트가 잘못된 것이다 — 수정한다
2. **GREEN — 테스트를 통과시키는 최소한의 코드 작성**
   - 테스트를 통과시키기 위한 코드만 작성한다
   - "이왕 하는 김에" 추가 구현을 하지 않는다
3. **REFACTOR — 리팩토링**
   - 중복 제거, 네이밍 개선 등 코드 품질을 높인다
   - 리팩토링 후 테스트가 여전히 통과하는지 확인한다
4. **REPEAT — 다음 기능으로 반복**

**금지 사항:**
- 실패하는 테스트 없이 프로덕션 코드를 작성하지 않는다
- 한 사이클에서 여러 기능을 동시에 구현하지 않는다
- 리팩토링 단계를 건너뛰지 않는다

## 작업 규칙

- 각 Phase 완료 후 `docs/CHANGELOG.md`에 기록
- `examples/`에 새 기능 시연용 `.gwan` 예제 파일 추가
- REPL은 Phase 1부터 동작해야 함 (단계적으로 기능 확장)
