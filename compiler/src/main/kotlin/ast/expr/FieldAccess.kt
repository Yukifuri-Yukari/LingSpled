package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class FieldAccess(
    val receiver: Expression,
    val fieldName: String,
    val safe: Boolean = false
) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.fieldAccess(this)

    override fun toString() = "FieldAccess(receiver=$receiver, field=\"$fieldName\", safe=$safe)"
}