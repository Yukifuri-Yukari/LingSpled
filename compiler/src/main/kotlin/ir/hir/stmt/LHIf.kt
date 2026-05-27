package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHIf(
    val cond: LHExpression,
    val then: List<LHStatement>,
    val els: List<LHStatement>?
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.ifStmt(this)
}