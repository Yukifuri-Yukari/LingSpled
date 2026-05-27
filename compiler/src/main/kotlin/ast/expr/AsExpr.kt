package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class AsExpr(
    val expr: Expression,
    val type: LType,
) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.visitAs(this)
    override fun toString() = "($expr as ${type.name})"
}