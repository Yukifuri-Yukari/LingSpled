package yukifuri.lang.lingspled.compiler.imports.declaration

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHParam

sealed class Declaration(
    protected open val shortenName: String,
    protected open val type: LType,
    protected open val fqn: String,
    /** FQN: Fully Qualified Name (like class a.b.C or function a.b.f or attribute a.b.attr, for inner class, uses a.b.Outer#Inner) */
)

data class VariableDeclaration(
    val modifiers: List<String>,
    override val shortenName: String,
    override val type: LType,
    val isConstant: Boolean,
    override val fqn: String,
) : Declaration(shortenName, type, fqn)

data class FunctionDeclaration(
    val modifiers: List<String>,
    override val shortenName: String,
    override val type: LType,
    val typeParams: List<String>,
    val params: List<LHParam>,
    override val fqn: String,
) : Declaration(shortenName, type, fqn)

data class ClassDeclaration(
    val modifiers: List<String>,
    override val shortenName: String,
    override val type: LType,
    val typeParams: List<String>,
    val superClass: LType = LType.ANY,
    val isInterface: Boolean = false,
    val members: List<Declaration>,
    override val fqn: String,
) : Declaration(shortenName, type, fqn)