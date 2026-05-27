package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHFor(
    val init: LHStatement?,
    val cond: LHExpression,
    val update: LHStatement?,
    val body: List<LHStatement>
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.forStmt(this)
}