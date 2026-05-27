package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHWhile(
    val cond: LHExpression,
    val body: List<LHStatement>
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.whileStmt(this)
}