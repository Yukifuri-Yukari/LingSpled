package yukifuri.lang.lingspled.compiler.ir.hir.base

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor
import yukifuri.lang.lingspled.compiler.ir.sym.Symbol

abstract class LHExpression(
    var resolvedSymbol: Symbol? = null
) {
    var row = 0
    var col = 0
    var inferredType: LType? = null

    fun at(row: Int, col: Int) {
        this.row = row
        this.col = col
    }

    abstract fun accept(visitor: HirVisitor)
}
