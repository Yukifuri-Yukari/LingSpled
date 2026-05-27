package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class If(
    val cond: Expression,
    val then: Module,
    val els: Module?
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.visitIf(this)
    }

    override fun toString(): String {
        return "If(cond=$cond, then=$then, els=$els)"
    }
}