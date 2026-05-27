package yukifuri.lang.lingspled.compiler.ast.variable

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

open class VariableDecl(
    val modifier: List<String>,
    val name: String,
    val type: LType,
    val initialize: Expression?,   // null = 无初始化表达式
    val isConstant: Boolean,
    val annotations: List<Annotation> = emptyList()
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.variableDecl(this)
    }

    override fun toString(): String {
        return "VariableDecl(annotations=$annotations, modifier=$modifier, name=\"$name\", type=$type, initialize=$initialize, isConstant=$isConstant)"
    }
}