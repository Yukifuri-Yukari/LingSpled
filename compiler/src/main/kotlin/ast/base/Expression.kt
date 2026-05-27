package yukifuri.lang.lingspled.compiler.ast.base

import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

abstract class Expression(
    var row: Int = 0,
    var col: Int = 0
) {
    abstract fun accept(visitor: AstVisitor)

    fun at(row: Int, col: Int) {
        this.row = row
        this.col = col
    }
}