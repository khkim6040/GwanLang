package gwanlang

class Resolver(private val interpreter: Interpreter) {

    private val scopes = ArrayDeque<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE

    fun resolve(statements: List<Stmt>) {
        for (statement in statements) {
            resolve(statement)
        }
    }

    private fun resolve(stmt: Stmt) {
        when (stmt) {
            is Stmt.Block -> {
                beginScope()
                resolve(stmt.statements)
                endScope()
            }
            is Stmt.Var -> {
                declare(stmt.name)
                if (stmt.initializer != null) {
                    resolve(stmt.initializer)
                }
                define(stmt.name)
            }
            is Stmt.Function -> {
                declare(stmt.name)
                define(stmt.name)
                resolveFunction(stmt, FunctionType.FUNCTION)
            }
            is Stmt.Expression -> resolve(stmt.expression)
            is Stmt.If -> {
                resolve(stmt.condition)
                resolve(stmt.thenBranch)
                if (stmt.elseBranch != null) {
                    resolve(stmt.elseBranch)
                }
            }
            is Stmt.Print -> resolve(stmt.expression)
            is Stmt.Return -> {
                if (currentFunction == FunctionType.NONE) {
                    GwanLang.error(stmt.keyword, "Can't return from top-level code.")
                }
                if (stmt.value != null) {
                    resolve(stmt.value)
                }
            }
            is Stmt.While -> {
                resolve(stmt.condition)
                resolve(stmt.body)
            }
            is Stmt.Class -> {
                declare(stmt.name)
                define(stmt.name)

                if (stmt.superclass != null) {
                    resolve(stmt.superclass)
                    beginScope()
                    scopes.last()["super"] = true
                }

                beginScope()
                scopes.last()["this"] = true

                for (method in stmt.methods) {
                    resolveFunction(method, FunctionType.FUNCTION)
                }

                endScope()

                if (stmt.superclass != null) {
                    endScope()
                }
            }
        }
    }

    private fun resolve(expr: Expr) {
        when (expr) {
            is Expr.Variable -> {
                if (scopes.isNotEmpty() && scopes.last()[expr.name.lexeme] == false) {
                    GwanLang.error(expr.name, "Can't read local variable in its own initializer.")
                }
                resolveLocal(expr, expr.name)
            }
            is Expr.Assign -> {
                resolve(expr.value)
                resolveLocal(expr, expr.name)
            }
            is Expr.Binary -> {
                resolve(expr.left)
                resolve(expr.right)
            }
            is Expr.Call -> {
                resolve(expr.callee)
                for (argument in expr.arguments) {
                    resolve(argument)
                }
            }
            is Expr.Grouping -> resolve(expr.expression)
            is Expr.Literal -> {}
            is Expr.Logical -> {
                resolve(expr.left)
                resolve(expr.right)
            }
            is Expr.Unary -> resolve(expr.right)
            is Expr.Get -> resolve(expr.obj)
            is Expr.Set -> {
                resolve(expr.value)
                resolve(expr.obj)
            }
            is Expr.This -> resolveLocal(expr, expr.keyword)
            is Expr.Super -> resolveLocal(expr, expr.keyword)
        }
    }

    private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()

        currentFunction = enclosingFunction
    }

    private fun beginScope() {
        scopes.addLast(mutableMapOf())
    }

    private fun endScope() {
        scopes.removeLast()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.last()
        if (scope.containsKey(name.lexeme)) {
            GwanLang.error(name, "Already a variable with this name in this scope.")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        scopes.last()[name.lexeme] = true
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }
}

enum class FunctionType {
    NONE,
    FUNCTION
}
