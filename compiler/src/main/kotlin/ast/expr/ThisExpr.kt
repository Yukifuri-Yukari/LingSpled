package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class ThisExpr : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.thisRef(this)
    override fun toString() = "ThisExpr"
}