package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

/** 对一个表达式求值后直接调用，如 `{ x -> x }(5)` 或 `getF()(5)`。 */
class InvokeExpr(
    val callee: Expression,
    val arguments: List<Expression>
) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.invokeExpr(this)

    override fun toString() = "InvokeExpr(callee=$callee, arguments=$arguments)"
}