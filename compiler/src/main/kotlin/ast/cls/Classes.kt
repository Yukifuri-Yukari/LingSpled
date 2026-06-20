package yukifuri.lang.lingspled.compiler.ast.cls

import yukifuri.lang.lingspled.compiler.ast.LAArgument
import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAInvokeExpr
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.general.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.util.Modifiers

data class LAClass(
    val annotations: List<LAAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Class>,
    val name: String,
    val tp: List<LTypeParamDecl>,
    val superclass: LAInvokeExpr,
    val interfaces: List<LTypeRef>,
    val primaryCtor: LAPrimaryConstructor?,
    val ctors: List<LAClassConstructor>,
    val functions: List<LAFunction>,
    val attr: List<LAClassAttribute>,
    val inits: List<LAInitBlock>,
    val deinit: LADeinitBlock?,
    val nested: List<LAClass>,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.clsDecl(this)
}

// class Foo private constructor(val x: Int, y: String = "")
data class LAPrimaryConstructor(
    val annotations: List<LAAnnotation>,
    val access: Modifiers.Access,
    val params: List<LAClassConstructorParameter>,
    val position: Position
)

sealed class LAConstructorDelegation {
    data class This(val args: List<LAArgument>) : LAConstructorDelegation()
    data class Super(val args: List<LAArgument>) : LAConstructorDelegation()
}

data class LAClassConstructorParameter(
    val annotations: List<LAAnnotation>,
    val access: Modifiers.Access,
    val mutable: Boolean?,
    val name: String,
    val type: LTypeRef,
    val default: LAExpression?,
    val position: Position
)

class LAClassConstructor(
    annotations: List<LAAnnotation>,
    access: Modifiers.Access,
    params: List<LAParameter>,
    val delegation: LAConstructorDelegation? = null,
    body: LAModule?,
    position: Position
) : LAFunction(
    annotations, access, emptyList(), emptyList(),
    null, "<constructor>", params, LTypeRef.unit,
    body, position
) {

    override fun toString() = buildString {
        append("LAClassConstructor(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        append("<constructor>, params=$params")
        if (delegation != null) append(", delegation=$delegation")
        if (body != null) append(", body=$body")
        append(")")
    }
}

/**
 * `init { }`——实例初始化块。lower 成 `<init>`，并入 `<constructor>` 体在字段赋值后执行。
 * 一个类可有多个，按 [position] 归并保留源序。
 */
class LAInitBlock(
    body: LAModule,
    position: Position
) : LAFunction(
    emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
    null, "<init>", emptyList(), LTypeRef.unit,
    body, position
) {

    override fun toString() = "LAInitBlock(body=$body)"
}

/**
 * `deinit { }`——析构前调用的块。lower 成 `<deinit>`，与 `<init>`/`<clinit>`/`<constructor>` 相对。
 * 一个类至多一个。
 */
class LADeinitBlock(
    body: LAModule,
    position: Position
) : LAFunction(
    emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
    null, "<deinit>", emptyList(), LTypeRef.unit,
    body, position
) {

    override fun toString() = "LADeinitBlock(body=$body)"
}

class LAClassAttribute(
    annotations: List<LAAnnotation>,
    access: Modifiers.Access,
    modifiers: List<Modifiers.Property>,
    mutable: Boolean,
    name: String,
    type: LTypeRef?,
    init: LAExpression?,
    delegator: LAExpression?,
    val getter: LAFunction?,
    val setter: LAFunction?,
    position: Position
) : LAVariableDecl(annotations, access, modifiers, mutable, name, type, init, delegator, position) {

    override fun toString() = buildString {
        append("LAClassAttribute(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        append("mutable=$mutable, ")
        append("name='$name'")
        if (type != null) append(", type=$type")
        if (init != null) append(", init=$init")
        if (delegator != null) append(", delegator=$delegator")
        if (getter != null) append(", getter=$getter")
        if (setter != null) append(", setter=$setter")
        append(", position=$position")
        append(")")
    }
}