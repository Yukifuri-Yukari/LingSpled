package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class MethodCall(
    val receiver: Expression,
    val methodName: String,
    val arguments: List<Expression>,
    val typeArgs: List<LType> = emptyList(),
    val safe: Boolean = false
) : Expression() {
    override fun accept(visitor: AstVisitor) = visitor.methodCall(this)

    override fun toString() = "MethodCall(receiver=$receiver, method=\"$methodName\", typeArgs=$typeArgs, arguments=$arguments, safe=$safe)"
}