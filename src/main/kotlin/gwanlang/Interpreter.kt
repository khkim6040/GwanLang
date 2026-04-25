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
        is Expr.Unary -> {
            val right = evaluate(expr.right)
            when (expr.op.type) {
                TokenType.MINUS -> {
                    checkNumberOperand(expr.op, right)
                    -(right as Double)
                }
                TokenType.BANG -> !isTruthy(right)
                else -> null
            }
        }
        is Expr.Binary -> TODO()
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun checkNumberOperand(op: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(op, "Operand must be a number.")
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
