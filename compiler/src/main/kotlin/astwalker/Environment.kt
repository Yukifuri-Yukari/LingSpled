package yukifuri.lang.lingspled.compiler.astwalker

/**
 * Scoped environment for variable bindings.
 * Each scope has a parent, forming a chain for lexical scoping.
 */
class Environment(private val parent: Environment? = null) {
    private val variables = mutableMapOf<String, LValue>()
    private val constants = mutableSetOf<String>()

    fun define(name: String, value: LValue, isConstant: Boolean = false) {
        if (variables.containsKey(name)) {
            throw WalkerException("Variable '$name' is already defined in this scope")
        }
        variables[name] = value
        if (isConstant) constants.add(name)
    }

    fun get(name: String): LValue {
        return variables[name] ?: parent?.get(name)
            ?: throw WalkerException("Undefined variable '$name'")
    }

    fun tryGet(name: String): LValue? =
        variables[name] ?: parent?.tryGet(name)

    fun set(name: String, value: LValue) {
        if (name in constants) {
            throw WalkerException("Cannot reassign constant '$name'")
        }
        if (variables.containsKey(name)) {
            variables[name] = value
            return
        }
        parent?.set(name, value)
            ?: throw WalkerException("Undefined variable '$name'")
    }

    fun child(): Environment = Environment(this)
}