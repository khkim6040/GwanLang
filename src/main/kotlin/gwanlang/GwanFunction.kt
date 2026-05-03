package gwanlang

class GwanFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
    private val isInitializer: Boolean = false
) : GwanCallable {

    fun bind(instance: GwanInstance): GwanFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return GwanFunction(declaration, environment, isInitializer)
    }

    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: Return) {
            if (isInitializer) return closure.getAt(0, "this")
            return returnValue.value
        }
        if (isInitializer) return closure.getAt(0, "this")
        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}
