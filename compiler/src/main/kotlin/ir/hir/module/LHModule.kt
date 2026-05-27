package yukifuri.lang.lingspled.compiler.ir.hir.module

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

open class LHModule(
    open val statements: List<LHStatement>
) : LHStatement() {
    override fun accept(visitor: HirVisitor) {
        for (statement in statements) {
            statement.accept(visitor)
        }
    }
}