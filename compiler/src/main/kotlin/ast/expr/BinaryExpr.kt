package yukifuri.lang.lingspled.compiler.ast.expr

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class BinaryExpr(
    val l: Expression,
    val op: Operator,
    val r: Expression
) : Expression() {
    override fun accept(visitor: AstVisitor) {
        visitor.binaryOp(this)
    }

    override fun toString(): String {
        return "BinaryExpr(l=$l, op=${op.name}, r=$r)"
    }
}