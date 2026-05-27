package yukifuri.lang.lingspled.compiler.ast.function

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class LambdaExpr(
    val arguments: List<Argument>,
    val body: Module
) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.lambdaExpr(this)

    override fun toString(): String =
        "LambdaExpr(arguments=$arguments, body=$body)"
}