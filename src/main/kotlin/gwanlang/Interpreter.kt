package gwanlang

class Interpreter {

    fun interpret(expression: Expr) {
        try {
            val value = evaluate(expression)
            println(stringify(value))
        } catch (error: RuntimeError) {
            GwanLang.runtimeError(error)
        }
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
                else -> throw IllegalStateException("Unknown unary operator: ${expr.op.type}")
            }
        }
        is Expr.Binary -> {
            val left = evaluate(expr.left)
            val right = evaluate(expr.right)
            when (expr.op.type) {
                TokenType.MINUS -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) - (right as Double)
                }
                TokenType.STAR -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) * (right as Double)
                }
                TokenType.SLASH -> {
                    checkNumberOperands(expr.op, left, right)
                    if (right as Double == 0.0) {
                        throw RuntimeError(expr.op, "Division by zero.")
                    }
                    (left as Double) / right
                }
                TokenType.PLUS -> {
                    if (left is Double && right is Double) left + right
                    else if (left is String && right is String) left + right
                    else throw RuntimeError(expr.op, "Operands must be two numbers or two strings.")
                }
                TokenType.GREATER -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) > (right as Double)
                }
                TokenType.GREATER_EQUAL -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) >= (right as Double)
                }
                TokenType.LESS -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) < (right as Double)
                }
                TokenType.LESS_EQUAL -> {
                    checkNumberOperands(expr.op, left, right)
                    (left as Double) <= (right as Double)
                }
                TokenType.EQUAL_EQUAL -> isEqual(left, right)
                TokenType.BANG_EQUAL -> !isEqual(left, right)
                else -> throw IllegalStateException("Unknown binary operator: ${expr.op.type}")
            }
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun checkNumberOperands(op: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(op, "Operands must be numbers.")
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false
        return a == b
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
