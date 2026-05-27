package yukifuri.lang.lingspled.compiler.ast.base

import yukifuri.lang.lingspled.compiler.ast.type.LType

class Argument(
    val name: String,
    val type: LType,
    val defaultValue: Expression? = null
) {
    override fun toString(): String {
        return "Argument(name='$name', type=$type, defaultValue=$defaultValue)"
    }
}