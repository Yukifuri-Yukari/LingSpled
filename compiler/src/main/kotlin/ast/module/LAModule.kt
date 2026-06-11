package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.lexer.Position

data class LAModule(
    val statements: List<LAStatement>,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) {
        for (stmt in statements) {
            stmt.accept(visitor)
        }
    }

    override fun toString(): String {
        return "LAModule(statements=$statements)"
    }

    class Builder(
        val position: Position,
        val init: MutableList<LAStatement> = mutableListOf(),
    ) {

        fun add(statement: LAStatement): Builder {
            init.add(statement)
            return this
        }

        fun add(module: LAModule) {
            init.addAll(module.statements)
        }

        fun add(statements: List<LAStatement>): Builder {
            init.addAll(statements)
            return this
        }

        fun build() = LAModule(init, position)
    }
}