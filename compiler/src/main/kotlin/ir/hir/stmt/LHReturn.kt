package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHReturn(val value: LHExpression?) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.returnStmt(this)
}