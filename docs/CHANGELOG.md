# Changelog

## [Unreleased]

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
