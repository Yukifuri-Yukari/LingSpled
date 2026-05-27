package yukifuri.lang.lingspled.compiler.ir.hir.module

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement

data class LHFile(
    override val statements: List<LHStatement>
) : LHModule(statements)