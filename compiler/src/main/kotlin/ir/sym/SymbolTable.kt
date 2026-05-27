package yukifuri.lang.lingspled.compiler.ir.sym

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction

class SymbolTable {
    val root = SymbolScope()

    fun newFunction(name: String, func: LHFunction) {
        root.func[name] = FunctionSym(name, func)
    }

    fun newVariable(name: String, type: LType, value: LHExpression, isConstant: Boolean, row: Int, col: Int) {
        root.vars[name] = VariableSym(name, type, value, isConstant, false, row, col)
    }

    fun newClass(name: String, cls: LHClass) {
        val fields  = cls.fields.associateBy { it.name }
        val methods = cls.methods.filterIsInstance<LHFunction>().associateBy { it.name }
        root.classes[name] = ClassSym(
            name, cls.superClass,
            fields, methods,
            cls.isInterface, cls.typeParams, false,
            cls.row, cls.col,
        )
    }

    fun findFunction(name: String): FunctionSym? = root.findFunction(name, emptyList())
    fun findVariable(name: String): VariableSym? = root.findVariable(name)
    fun findClass(name: String): ClassSym? = root.findClass(name)
}