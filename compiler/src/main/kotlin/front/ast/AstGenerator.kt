package yukifuri.lang.lingspled.compiler.front.ast

import yukifuri.lang.lingspled.compiler.bridge.FQN
import yukifuri.lang.lingspled.compiler.front.struct.*

class AstGenerator : LCVisitor {

    val blockStack = ArrayDeque<LABlock>()
    val exprStack = ArrayDeque<LAExpression>()

    fun process(cst: LCFile): LAFile {
        blockStack.clear()
        exprStack.clear()
        cst.module.accept(this)

        return LAFile(
            cst.pkg?.fqn.asFqn(),
            cst.imports.map { LAFile.LAImport(it.fqn.asFqn(), it.alias, it.wildcard, it.pos) },
            blockStack.rl()
            )
    }

    override fun declFunc(func: LCFunction) {
        func.module.accept(this)

        exprStack.al(LAFunction(
            func.name, func.params.map { LAParameter(it.modifier, it.name, it.type) },
            func.ret,
            blockStack.rl(),
            func.position
        ))
    }

    override fun declVar(decl: LCVariableDecl) { TODO() }

    override fun declClass(klass: LCClass) { TODO() }

    override fun ctrlFor(stmt: LCCtrlFor) { TODO() }

    override fun ctrlWhile(stmt: LCCtrlWhile) { TODO() }

    override fun ctrlIf(stmt: LCCtrlIf) { TODO() }

    override fun ctrlReturn(stmt: LCCtrlReturn) { TODO() }

    override fun ctrlBreak(stmt: LCCtrlBreak) { TODO() }

    override fun ctrlContinue(stmt: LCCtrlContinue) { TODO() }

    override fun exprAs(expr: LCExprAs) { TODO() }

    override fun exprUnary(expr: LCExprUnary) { TODO() }

    override fun exprBinary(expr: LCExprBinary) { TODO() }

    override fun exprLambda(lambda: LCLambda) { TODO() }

    override fun exprLiteral(literal: LCLiteral<*>) {
        exprStack.al(LAExprLiteral(literal.value, literal.position))
    }

    override fun exprInvoke(invocation: LCExprInvoke) {

        exprStack.al(LAExprInvocation(
            lowerExpr(invocation.recv),
            invocation.args.map {
                LAArgument(it.name, lowerExpr(it.value), it.position)
            },
            invocation.lambda?.let(::lowerExpr),
            invocation.typeArgs,
            invocation.position
        ))
    }

    override fun exprIndex(index: LCExprIndex) { TODO() }

    override fun exprMemberAccess(access: LCExprMemberAccess) {
        exprStack.al(LAExprFieldRef(
            lowerExpr(access.recv),
            access.name,
            access.position
        ))
    }

    override fun exprVarGet(get: LCExprVariableGet) {
        exprStack.al(LAExprNameRef(get.name, get.position))
    }

    override fun exprStmt(stmt: LCExprStatement) { stmt.expr.accept(this) }

    override fun module(module: LCModule) {
        val collected = mutableListOf<LAStatement>()
        for (stmt in module.stmt) {
            collected.add(lowerExpr(stmt).asStmt())
        }
        blockStack.al(LABlock(collected.toList()))
    }

    private fun <T> ArrayDeque<T>.rl(): T = this.removeLast()

    private fun <T> ArrayDeque<T>.al(s: T) = this.addLast(s)

    private fun lowerExpr(expr: LCExpression): LAExpression {
        expr.accept(this)
        return exprStack.rl()
    }

    private fun LCConfirmedName?.asFqn() = FQN(this?.parts ?: listOf())

    private fun LAExpression.asStmt() = this as? LAStatement ?: LAExprStmt(this)
}
