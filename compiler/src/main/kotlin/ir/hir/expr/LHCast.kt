package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHCast(val expr: LHExpression, val targetType: LType) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.castExpr(this)
}