package yukifuri.lang.lingspled.compiler.ir.hir.expr

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

/** 统一所有调用：方法调用、自由函数调用、运算符脱糖、invoke。
 *  receiver = null 表示作用域内的自由函数调用。 */
data class LHCall(
    val receiver: LHExpression?,
    val name: String,
    val args: List<LHExpression>,
    val typeArgs: List<LType> = emptyList()
) : LHExpression() {
    override fun accept(visitor: HirVisitor) = visitor.call(this)
}