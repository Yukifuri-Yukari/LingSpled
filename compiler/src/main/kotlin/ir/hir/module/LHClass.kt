package yukifuri.lang.lingspled.compiler.ir.hir.module

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHField
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHClass(
    val name: String,
    val modifiers: List<String> = emptyList(),
    val superClass: LType = LType.ANY,
    val fields: List<LHField>,
    val methods: List<LHStatement>,   // LHFunction 实例
    val isInterface: Boolean = false,
    val typeParams: List<String> = emptyList()
) : LHModule(methods) {
    override fun accept(visitor: HirVisitor) = visitor.classDecl(this)
}