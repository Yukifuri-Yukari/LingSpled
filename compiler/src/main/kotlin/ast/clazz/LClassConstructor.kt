package yukifuri.lang.lingspled.compiler.ast.clazz

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType

class LClassConstructor(
    modifier: List<String>,
    arguments: List<Argument>,
    body: Module
) : LFunction(modifier, "<constructor>", emptyList(), arguments, LType.AUTO, body) {
    override fun toString(): String {
        return "LClassConstructor(modifier=$modifier, arguments=$arguments, body=$body)"
    }
}
