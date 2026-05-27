package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class While(
    val cond: Expression,
    val body: Module
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.visitWhile(this)
    }

    override fun toString(): String {
        return "While(cond=$cond, body=$body)"
    }
}