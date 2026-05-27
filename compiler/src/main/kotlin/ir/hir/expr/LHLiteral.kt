package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHLiteral(val value: Any) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.literal(this)
}