package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class IndexAssign(
    val receiver: Expression,
    val index: Expression,
    val value: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) = visitor.indexAssign(this)
    override fun toString() = "IndexAssign($receiver, $index, $value)"
}