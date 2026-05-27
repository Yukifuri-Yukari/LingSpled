package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHFieldGet(
    val receiver: LHExpression,
    val field: String
) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.fieldGet(this)
}