package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

open class Module(
    val statements: List<Statement>
) : Statement() {
    class Builder {
        val statements = mutableListOf<Statement>()

        fun add(statement: Statement): Builder {
            statements.add(statement)
            return this
        }

        fun extend(vararg stmt: Statement) {
            statements.addAll(stmt)
        }

        fun extend(module: Module): Builder {
            statements.addAll(module.statements)
            return this
        }

        fun extend(module: Builder): Builder {
            statements.addAll(module.statements)
            return this
        }

        fun build(): Module {
            return Module(statements)
        }
    }

    override fun accept(visitor: AstVisitor) {
        for (stmt in statements) {
            stmt.accept(visitor)
        }
    }

    operator fun iterator() = statements.iterator()

    override fun toString(): String {
        return "Module(statements=$statements)"
    }
}