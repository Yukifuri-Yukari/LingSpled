package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class Defer(val expr: Expression) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.visitDefer(this)
    }

    override fun toString(): String {
        return "Defer(expr=$expr)"
    }
}