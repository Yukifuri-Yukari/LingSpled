package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class UnaryExpr(
    val op: Operator,
    val expr: Expression
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.unaryOp(this)
    }

    override fun toString(): String {
        return "UnaryExpr(op=${op.name}, expr=$expr)"
    }
}