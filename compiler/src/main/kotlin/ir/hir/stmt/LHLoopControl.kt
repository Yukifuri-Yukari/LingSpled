package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHLoopControl(val isBreak: Boolean) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.loopControl(this)
}