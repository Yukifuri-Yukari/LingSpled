package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class When(
    val expr: Expression?,
    val branches: List<Branch>,
    val elseBranch: Module? = null
) : Statement() {

    override fun accept(visitor: AstVisitor) {
        visitor.visitWhen(this)
    }

    sealed class Branch(
        open val guard: Expression?,
        open val module: Module,
    )

    data class TypeBranch(
        val typename: String,
        val destructured: List<String>,
        override val guard: Expression?,
        override val module: Module,
    ) : Branch(guard, module)

    data class ExprBranch(
        val expr: Expression,
        override val guard: Expression?,
        override val module: Module,
    ) : Branch(guard, module)
}