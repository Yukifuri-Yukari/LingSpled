package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHLocalGet(val name: String) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.localGet(this)
}