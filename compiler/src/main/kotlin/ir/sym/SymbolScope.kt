package yukifuri.lang.lingspled.compiler.ir.sym

import yukifuri.lang.lingspled.compiler.ast.type.LType

data class SymbolScope(
    val parent: SymbolScope? = null,
    val children: MutableList<SymbolScope> = mutableListOf(),
    val func: MutableMap<String, FunctionSym> = mutableMapOf(),
    val vars: MutableMap<String, VariableSym> = mutableMapOf(),
    val classes: MutableMap<String, ClassSym> = mutableMapOf(),
) {
    fun findFunction(name: String, arguments: List<LType>): FunctionSym? {
        return func[name] ?: parent?.findFunction(name, arguments)
    }

    fun findVariable(name: String): VariableSym? {
        return vars[name] ?: parent?.findVariable(name)
    }

    fun findClass(name: String): ClassSym? {
        return classes[name] ?: parent?.findClass(name)
    }
}
