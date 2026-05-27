package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHParam
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

data class LHLambda(
    val params: List<LHParam>,
    val body: List<LHStatement>,
    val captures: List<String>   // 由后续 ClosureAnalysisPass 填充
) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.lambdaExpr(this)
}