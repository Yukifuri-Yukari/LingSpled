package yukifuri.lang.lingspled.compiler.ast.clazz

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl

class LClassAttribute(
    modifier: List<String>,
    name: String, type: LType,
    initialize: Expression?, isConstant: Boolean
) : VariableDecl(modifier, name, type, initialize, isConstant) {
    override fun toString(): String {
        return "LClassAttribute(modifier=$modifier, name='$name', type=$type, initialize=$initialize, isConstant=$isConstant)"
    }
}