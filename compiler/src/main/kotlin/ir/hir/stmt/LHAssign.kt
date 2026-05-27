package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

sealed class LHAssignTarget
data class LHLocalTarget(val name: String) : LHAssignTarget()
data class LHFieldTarget(val receiver: LHExpression, val field: String) : LHAssignTarget()

data class LHAssign(
    val target: LHAssignTarget,
    val value: LHExpression
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.assignStmt(this)
}