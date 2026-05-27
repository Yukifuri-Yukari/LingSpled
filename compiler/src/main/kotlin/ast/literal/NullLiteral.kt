package yukifuri.lang.lingspled.compiler.ast.literal

import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class NullLiteral : Literal<Nothing?>(null) {
    override fun accept(visitor: AstVisitor) = visitor.literal(this)
    override fun toString() = "NullLiteral"
}