package gwanlang

class GwanClass(
    val name: String,
    val superclass: GwanClass?,
    private val methods: Map<String, GwanFunction>
) : GwanCallable {

    fun findMethod(name: String): GwanFunction? {
        if (methods.containsKey(name)) {
            return methods[name]
        }
        if (superclass != null) {
            return superclass.findMethod(name)
        }
        return null
    }

    override fun arity(): Int {
        val initializer = findMethod("init")
        return initializer?.arity() ?: 0
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = GwanInstance(this)
        val initializer = findMethod("init")
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments)
        }
        return instance
    }

    override fun toString(): String = name
}
