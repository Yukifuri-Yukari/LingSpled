package yukifuri.lang.lingspled.compiler.ast.base

import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class StatementWrapper(
    val expression: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        expression.accept(visitor)
    }

    override fun toString(): String {
        return expression.toString()
    }
}