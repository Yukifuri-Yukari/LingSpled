package yukifuri.lang.lingspled.compiler.ast.variable

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class VariableGet(
    val name: String
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.variableGet(this)
    }

    override fun toString(): String {
        return "VariableGet(name=\"$name\")"
    }
}