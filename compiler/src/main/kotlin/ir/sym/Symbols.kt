package yukifuri.lang.lingspled.compiler.ir.sym

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHField
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction

open class Symbol(
    open val name: String,
    open var used: Boolean = false,
)

data class FunctionSym(
    override val name: String,
    val func: LHFunction,
    override var used: Boolean = false,
) : Symbol(name, used)

data class VariableSym(
    override val name: String,
    val type: LType,
    var value: LHExpression,
    val isConstant: Boolean,
    override var used: Boolean = false,
    val row: Int,
    val col: Int,
) : Symbol(name, used)

data class ClassSym(
    override val name: String,
    val superClass: LType,
    val fields: Map<String, LHField>,
    val methods: Map<String, LHFunction>,
    val isInterface: Boolean = false,
    val typeParams: List<String> = emptyList(),
    override var used: Boolean = false,
    val row: Int,
    val col: Int,
) : Symbol(name, used)
