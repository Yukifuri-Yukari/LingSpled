package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

class LHThis : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.thisRef(this)
}
