package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

sealed class LHWhenBranch(
    open val guard: LHExpression?,
    open val body: List<LHStatement>,
)

data class LHTypeBranch(
    val typeName: String,
    val destructured: List<String>,
    override val guard: LHExpression?,
    override val body: List<LHStatement>,
) : LHWhenBranch(guard, body)

data class LHExprBranch(
    val cond: LHExpression,
    override val guard: LHExpression?,
    override val body: List<LHStatement>,
) : LHWhenBranch(guard, body)

data class LHWhen(
    val subject: LHExpression?,
    val branches: List<LHWhenBranch>,
    val elseBranch: List<LHStatement>?,
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.whenStmt(this)
}