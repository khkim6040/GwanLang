package gwanlang

class AstPrinter {
    fun print(expr: Expr): String = when (expr) {
        is Expr.Binary -> parenthesize(expr.op.lexeme, expr.left, expr.right)
        is Expr.Grouping -> parenthesize("group", expr.expression)
        is Expr.Literal -> if (expr.value == null) "nil" else expr.value.toString()
        is Expr.Unary -> parenthesize(expr.op.lexeme, expr.right)
        is Expr.Variable -> expr.name.lexeme
        is Expr.Assign -> parenthesize("= ${expr.name.lexeme}", expr.value)
        is Expr.Logical -> parenthesize(expr.op.lexeme, expr.left, expr.right)
        is Expr.Call -> parenthesize("call ${print(expr.callee)}", *expr.arguments.toTypedArray())
        is Expr.Get -> "(. ${print(expr.obj)} ${expr.name.lexeme})"
        is Expr.Set -> "(= ${print(expr.obj)}.${expr.name.lexeme} ${print(expr.value)})"
        is Expr.This -> "this"
        is Expr.Super -> "super.${expr.method.lexeme}"
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
