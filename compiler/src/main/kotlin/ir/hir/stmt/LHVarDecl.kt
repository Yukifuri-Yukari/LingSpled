package yukifuri.lang.lingspled.compiler.ir.hir.stmt

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHVarDecl(
    val name: String,
    val modifiers: List<String> = emptyList(),
    val isConstant: Boolean,
    val init: LHExpression?,
    val declaredType: LType = LType.INFER
) : LHStatement() {
    override fun accept(visitor: HirVisitor) = visitor.varDecl(this)
}