package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class For(
    val init: Statement,
    val cond: Expression,
    val update: Statement,
    val body: Module
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.visitFor(this)
    }

    override fun toString(): String {
        return "For(init=$init, cond=$cond, update=$update, body=$body)"
    }
}