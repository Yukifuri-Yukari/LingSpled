package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class FieldAssign(
    val receiver: Expression,
    val fieldName: String,
    val value: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) = visitor.fieldAssign(this)
    override fun toString() = "FieldAssign(receiver=$receiver, field=\"$fieldName\", value=$value)"
}