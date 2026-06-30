package yukifuri.lang.lingspled.compiler.ast.decl

import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.util.Modifiers

open class LAVariableDecl(
    val annotations: List<LAAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Property>,
    val mutable: Boolean,
    val name: String,
    val type: LTypeRef?,
    val init: LAExpression?,
    val delegator: LAExpression?,
    override val position: Position
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.varDecl(this)

    override fun toString() = buildString {
        append("LAVariableDecl(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        if (!mutable) append("mutable=$mutable, ")
        append("name='$name'")
        if (type != null) append(", type=$type")
        if (init != null) append(", init=$init")
        if (delegator != null) append(", delegator=$delegator")
        append(", position=$position")
        append(")")
    }
}