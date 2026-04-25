package gwanlang

class AstPrinter {
    fun print(expr: Expr): String = when (expr) {
        is Expr.Binary -> parenthesize(expr.op.lexeme, expr.left, expr.right)
        is Expr.Grouping -> parenthesize("group", expr.expression)
        is Expr.Literal -> if (expr.value == null) "nil" else expr.value.toString()
        is Expr.Unary -> parenthesize(expr.op.lexeme, expr.right)
    }

    private fun parenthesize(name: String, vararg exprs: Expr): String {
        val builder = StringBuilder()
        builder.append("(").append(name)
        for (expr in exprs) {
            builder.append(" ").append(print(expr))
        }
        builder.append(")")
        return builder.toString()
    }
}
