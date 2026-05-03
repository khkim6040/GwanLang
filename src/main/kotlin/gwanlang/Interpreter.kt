package gwanlang

class Interpreter {
    private val globals = Environment()
    private var environment = globals
    private val locals = java.util.IdentityHashMap<Expr, Int>()

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) {
            environment.getAt(distance, name.lexeme)
        } else {
            globals.get(name)
        }
    }

    init {
        globals.define("clock", object : GwanCallable {
            override fun arity(): Int = 0
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return System.currentTimeMillis().toDouble() / 1000.0
            }
            override fun toString(): String = "<native fn>"
        })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RuntimeError) {
            GwanLang.runtimeError(error)
        } catch (_: Return) {
            System.err.println("RuntimeError: Can't return from top-level code.")
            GwanLang.hadRuntimeError = true
        }
    }

    /** REPL용 — 표현식을 평가하여 값을 반환한다 */
    fun interpretExpr(expr: Expr): Any? {
        return try {
            evaluate(expr)
        } catch (error: RuntimeError) {
            GwanLang.runtimeError(error)
            null
        }
    }

    /** 테스트용 — evaluate를 외부에서 직접 호출 */
    internal fun testEvaluate(expr: Expr): Any? = evaluate(expr)

    private fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.Expression -> evaluate(stmt.expression)
            is Stmt.Print -> {
                val value = evaluate(stmt.expression)
                println(stringify(value))
            }
            is Stmt.Var -> {
                val value = if (stmt.initializer != null) evaluate(stmt.initializer) else null
                environment.define(stmt.name.lexeme, value)
            }
            is Stmt.Block -> executeBlock(stmt.statements, Environment(environment))
            is Stmt.If -> {
                if (isTruthy(evaluate(stmt.condition))) {
                    execute(stmt.thenBranch)
                } else if (stmt.elseBranch != null) {
                    execute(stmt.elseBranch)
                }
            }
            is Stmt.While -> {
                while (isTruthy(evaluate(stmt.condition))) {
                    execute(stmt.body)
                }
            }
            is Stmt.Function -> {
                val function = GwanFunction(stmt, environment)
                environment.define(stmt.name.lexeme, function)
            }
            is Stmt.Return -> {
                val value = if (stmt.value != null) evaluate(stmt.value) else null
                throw Return(value)
            }
            is Stmt.Class -> TODO("Phase 7")
        }
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            this.environment = previous
        }
    }

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
                    val leftNum = left as Double
                    val rightNum = right as Double
                    if (rightNum == 0.0) {
                        throw RuntimeError(expr.op, "Division by zero.")
                    }
                    leftNum / rightNum
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
        is Expr.Variable -> lookUpVariable(expr.name, expr)
        is Expr.Assign -> {
            val value = evaluate(expr.value)
            val distance = locals[expr]
            if (distance != null) {
                environment.assignAt(distance, expr.name, value)
            } else {
                globals.assign(expr.name, value)
            }
            value
        }
        is Expr.Call -> {
            val callee = evaluate(expr.callee)
            val arguments = expr.arguments.map { evaluate(it) }

            if (callee !is GwanCallable) {
                throw RuntimeError(expr.paren, "Can only call functions and classes.")
            }

            if (arguments.size != callee.arity()) {
                throw RuntimeError(expr.paren,
                    "Expected ${callee.arity()} arguments but got ${arguments.size}.")
            }

            callee.call(this, arguments)
        }
        is Expr.Logical -> {
            val left = evaluate(expr.left)
            if (expr.op.type == TokenType.OR) {
                if (isTruthy(left)) left else evaluate(expr.right)
            } else {
                if (!isTruthy(left)) left else evaluate(expr.right)
            }
        }
        is Expr.Get -> TODO("Phase 7")
        is Expr.Set -> TODO("Phase 7")
        is Expr.This -> TODO("Phase 7")
        is Expr.Super -> TODO("Phase 7")
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

    fun stringify(value: Any?): String {
        if (value == null) return "nil"
        if (value is Double) {
            val text = value.toString()
            if (text.endsWith(".0")) return text.dropLast(2)
            return text
        }
        return value.toString()
    }
}
