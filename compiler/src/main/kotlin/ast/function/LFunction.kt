package yukifuri.lang.lingspled.compiler.ast.function

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.type.TypeParam
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

open class LFunction(
    val modifier: List<String>,
    val name: String,
    val typeParams: List<TypeParam> = emptyList(),
    val arguments: List<Argument>,
    val returnType: LType,
    val body: Module,
    val annotations: List<Annotation> = emptyList()
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.functionDecl(this)
    }

    override fun toString(): String {
        return "LFunction(annotations=$annotations, modifier=$modifier, name=\"$name\", arguments=$arguments, returnType=$returnType, body=$body)"
    }
}