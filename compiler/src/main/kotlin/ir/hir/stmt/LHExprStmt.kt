package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

/** 把表达式用作语句（弃值）。stack VM 的 codegen 需在此补 pop 指令。 */
data class LHExprStmt(val expr: LHExpression) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.exprStmt(this)
}