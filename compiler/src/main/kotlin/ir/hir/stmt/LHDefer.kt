package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHDefer(val expr: LHExpression) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.deferStmt(this)
}