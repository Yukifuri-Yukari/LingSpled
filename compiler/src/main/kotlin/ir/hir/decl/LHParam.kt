package yukifuri.lang.lingspled.compiler.ir.hir.decl

import yukifuri.lang.lingspled.compiler.ast.type.LType

data class LHParam(val name: String, var type: LType)