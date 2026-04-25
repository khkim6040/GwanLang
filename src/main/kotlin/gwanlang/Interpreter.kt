package gwanlang

class Interpreter {

    fun interpret(expression: Expr) {
        val value = evaluate(expression)
        println(stringify(value))
    }

    /** 테스트용 — evaluate를 외부에서 직접 호출 */
    fun testEvaluate(expr: Expr): Any? = evaluate(expr)

    private fun evaluate(expr: Expr): Any? = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Grouping -> evaluate(expr.expression)
        is Expr.Unary -> TODO()
        is Expr.Binary -> TODO()
    }

    private fun stringify(value: Any?): String {
        if (value == null) return "nil"
        if (value is Double) {
            val text = value.toString()
            if (text.endsWith(".0")) return text.dropLast(2)
            return text
        }
        return value.toString()
    }
}
