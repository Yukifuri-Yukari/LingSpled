package yukifuri.lang.lingspled.compiler.ast.control

import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position

/** `if (cond) then else elseBranch`——表达式。else-if 链脱糖为 elseBranch 内嵌套一个 [LAIf]。 */
data class LAIf(
    val condition: LAExpression,
    val then: LAModule,
    val elseBranch: LAModule?,
    override val position: Position
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.ifExpr(this)
}

data class LAWhile(
    val condition: LAExpression,
    val body: LAModule,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.whileStmt(this)
}

data class LADoWhile(
    val body: LAModule,
    val condition: LAExpression,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.doWhileStmt(this)
}

/**
 * `for (variable[: type] in iterable) body`。FST 阶段保持结构化（`LFFor`），**脱糖留给 HIR**：
 * 协议选择（迭代器 `iterator()/hasNext()/next()` vs 索引 `[]`+`size`）依赖 iterable 的类型，
 * 须在类型推断后才能定，且语义检查要在原始 for 上验证 iterable 是否可迭代。
 */
data class LAFor(
    val variable: String,
    val type: LTypeRef?,
    val iterable: LAExpression,
    val body: LAModule,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.forStmt(this)
}

/** `try body (catch ...)* (finally ...)?`——表达式（同 Kotlin）。 */
data class LATry(
    val body: LAModule,
    val catches: List<LACatch>,
    val finallyBlock: LAModule?,
    override val position: Position
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.tryExpr(this)
}

data class LACatch(
    val name: String,
    val type: LTypeRef,
    val body: LAModule,
    val position: Position
)

data class LAThrow(
    val expr: LAExpression,
    override val position: Position
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.throwExpr(this)
}

data class LABreak(override val position: Position) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.breakStmt(this)
}

data class LAContinue(override val position: Position) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.continueStmt(this)
}

data class LALambda(
    val params: List<LAParameter>,
    val body: LAModule,
    override val position: Position
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.lambdaExpr(this)
}