package yukifuri.lang.lingspled.compiler.ast.clazz

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.type.TypeParam
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class LClass(
    val name: String,
    val typeParams: List<TypeParam> = emptyList(),
    val inheritances: List<LType>,
    val modifier: List<String>,
    val variables: List<VariableDecl>,
    definitions: List<Statement>,
    val annotations: List<Annotation> = emptyList(),
    val isInterface: Boolean = false,
    /** Arguments passed to the primary super-class constructor: `class Foo : Bar(arg1, arg2)` */
    val superCtorArgs: List<Expression> = emptyList(),
) : Module(definitions) {
    override fun accept(visitor: AstVisitor) {
        visitor.clazz(this)
    }

    override fun toString(): String {
        return "LClass(annotations=$annotations, name='$name', typeParams=$typeParams, inheritances=$inheritances, modifier=$modifier, variables=$variables, functions=$statements)"
    }
}