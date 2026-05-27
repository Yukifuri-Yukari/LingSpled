package yukifuri.lang.lingspled.compiler.ast.function

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class LFunctionCall(
    val name: String,
    val arguments: List<Expression>,
    val typeArgs: List<LType> = emptyList()
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.functionCall(this)
    }

    override fun toString(): String {
        return "LFunctionCall(name=\"$name\", arguments=$arguments)"
    }
}