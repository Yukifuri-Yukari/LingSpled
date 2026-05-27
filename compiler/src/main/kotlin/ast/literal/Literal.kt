package yukifuri.lang.lingspled.compiler.ast.literal

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

abstract class Literal<T>(val value: T) : Expression() {
    override fun accept(visitor: AstVisitor) {
        visitor.literal(this)
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(value=$value)"
    }
}