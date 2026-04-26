package gwanlang

sealed class Expr {
    data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr()
    data class Grouping(val expression: Expr) : Expr()
    data class Literal(val value: Any?) : Expr()
    data class Unary(val op: Token, val right: Expr) : Expr()
    data class Variable(val name: Token) : Expr()
    data class Assign(val name: Token, val value: Expr) : Expr()
    data class Logical(val left: Expr, val op: Token, val right: Expr) : Expr()
}
