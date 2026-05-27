package yukifuri.lang.lingspled.compiler.ast.variable

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class VariableAssign(
    val name: String,
    val operator: Operator,
    val value: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.variableAssign(this)
    }

    override fun toString(): String {
        return "VariableAssign(name=\"$name\", operator=${operator.name}, value=$value)"
    }
}