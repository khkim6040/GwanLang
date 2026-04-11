package gwanlang

/**
 * GwanLang 인터프리터의 진입점 및 전역 에러 채널.
 *
 * Phase 1 수준: Scanner 결과 출력까지.
 * 파서/인터프리터는 이후 Phase에서 추가된다.
 */
object GwanLang {
    var hadError: Boolean = false

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    private fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hadError = true
    }
}
