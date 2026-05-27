package yukifuri.lang.lingspled.compiler.ast.function

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class Return(
    val expr: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.functionReturn(this)
    }

    override fun toString(): String {
        return "Return(expr=$expr)"
    }
}