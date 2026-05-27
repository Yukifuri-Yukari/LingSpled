package yukifuri.lang.lingspled.compiler.ir.hir.module

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHParam
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHFunction(
    val name: String,
    val modifiers: List<String> = emptyList(),
    val typeParams: List<String> = emptyList(),
    val params: List<LHParam>,
    var returnType: LType,
    val body: List<LHStatement>
) : LHModule(body) {
    override fun accept(visitor: HirVisitor) = visitor.funDecl(this)
}