package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class IndexAccess(val receiver: Expression, val index: Expression) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.indexAccess(this)
    override fun toString() = "IndexAccess($receiver, $index)"
}