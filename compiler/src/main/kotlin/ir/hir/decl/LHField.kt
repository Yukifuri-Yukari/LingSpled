package yukifuri.lang.lingspled.compiler.ir.hir.decl

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression

data class LHField(
    val name: String,
    val modifiers: List<String> = emptyList(),
    val isConstant: Boolean,
    var type: LType,
    val init: LHExpression?
)