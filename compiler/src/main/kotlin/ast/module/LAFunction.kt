package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.util.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.util.Modifiers

open class LAFunction(
    val annotations: List<LAAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Function>,
    val tp: List<LTypeParamDecl>,
    val receiver: LTypeRef?,
    val name: String,
    val params: List<LAParameter>,
    val ret: LTypeRef,
    val body: LAModule?,
    override val position: Position
) : LAStatement(position) {

    /* val native get() = modifiers.contains(Function.Native)
    val inline get() = modifiers.contains(Function.Inline)
    val suspend get() = modifiers.contains(Function.Suspend)
    val override get() = modifiers.contains(Function.Override)
    val vararg get() = modifiers.contains(Function.Vararg)
    val static get() = modifiers.contains(Function.Static)
    val operator get() = modifiers.contains(Function.Operator)
    val infix get() = modifiers.contains(Function.Infix)
    val tailrec get() = modifiers.contains(Function.Tailrec) */

    override fun accept(visitor: LAVisitor) = visitor.funcDecl(this)

    override fun toString() = buildString {
        append("LAFunction(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        if (tp.isNotEmpty()) append("tp=$tp, ")
        if (receiver != null) append("receiver=$receiver, ")
        append("name=\"$name\", params=$params, ret=$ret")
        if (body != null) append(", body=$body")
        append(")")
    }

    data class LAReturnStmt(
        val expr: LAExpression,
        override val position: Position
    ) : LAStatement(position) {

        override fun accept(visitor: LAVisitor) = visitor.ret(this)
    }
}