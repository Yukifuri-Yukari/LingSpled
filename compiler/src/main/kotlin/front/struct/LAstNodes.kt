package yukifuri.lang.lingspled.compiler.front.struct

import yukifuri.lang.lingspled.compiler.bridge.FQN
import yukifuri.lang.lingspled.compiler.bridge.Position


interface LAVisitor {

    fun declFunction(func: LAFunction)

    fun exprInvocation(expr: LAExprInvocation)
    fun exprFieldRef(expr: LAExprFieldRef)
    fun exprNameRef(expr: LAExprNameRef)
    fun exprLiteral(expr: LAExprLiteral)
}

private inline fun <reified T : LAExpression>
        acceptor(
    crossinline func: LAVisitor.(T) -> Unit,
): (LAExpression, LAVisitor) -> Unit = { node, visitor ->
    visitor.func(node as T)
}

sealed class LAStatement(acceptor: (LAExpression, LAVisitor) -> Unit, position: Position) :
    LAExpression(acceptor, position)

sealed class LAExpression(protected val acceptor: (LAExpression, LAVisitor) -> Unit, open val position: Position) {

    fun getPos(): Position = position

    open fun accept(visitor: LAVisitor) = acceptor(this, visitor)
}

data class LAExprStmt(
    val expr: LAExpression,
) : LAStatement({ _, v -> expr.accept(v) }, expr.position)

data class LAFile(
    val packageName: FQN,
    val imports: List<LAImport>,
    val body: LABlock,
) {
    data class LAImport(
        val fqn: FQN,
        val alias: String?,
        val wildcard: Boolean,
        val position: Position,
    )
}

data class LAParameter(
    val modifier: LParameterModifier?,
    val name: String,
    val type: LTypeReference,
)

data class LABlock(val stmt: List<LAStatement>)

data class LAFunction(
    val name: String,
    val params: List<LAParameter>,
    val returnType: LTypeReference,
    val block: LABlock,
    override val position: Position
) : LAStatement(acceptor(LAVisitor::declFunction), position)

data class LAArgument(
    val name: String?,
    val value: LAExpression,
    val position: Position,
)

data class LAExprInvocation(
    val recv: LAExpression,
    val args: List<LAArgument>,
    val lambda: LAExpression?,
    val typeArgs: List<LTypeReference>,
    override val position: Position,
) : LAExpression(acceptor(LAVisitor::exprInvocation), position)

data class LAExprFieldRef(
    val recv: LAExpression,
    val name: String,
    override val position: Position,
) : LAExpression(acceptor(LAVisitor::exprFieldRef), position)

data class LAExprNameRef(
    val name: String,
    override val position: Position,
) : LAExpression(acceptor(LAVisitor::exprNameRef), position)

data class LAExprLiteral(
    val value: Any?,
    override val position: Position,
) : LAExpression(acceptor(LAVisitor::exprLiteral), position)
