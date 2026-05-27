package yukifuri.lang.lingspled.compiler.ir.checker

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel
import yukifuri.lang.lingspled.compiler.diagnostics.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHField
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.*
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor
import yukifuri.lang.lingspled.compiler.ir.sym.ClassSym
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable

class SemanticChecker(
    private val symTable: SymbolTable,
    private val diagnostics: Diagnostics,
) : HirVisitor {
    
    private val scopes = ArrayDeque<MutableMap<String, Boolean>>()
    private var currentClass: ClassSym? = null

    fun check(file: LHFile) = file.statements.forEach { it.accept(this) }

    private fun pushScope() = scopes.addLast(mutableMapOf())
    private fun popScope()  = scopes.removeLast()
    private fun declareLocal(name: String, isConstant: Boolean) = 
        scopes.lastOrNull()?.set(name, isConstant)

    private fun findLocalConst(name: String): Boolean? {
        for (i in scopes.indices.reversed()) scopes[i][name]?.let { return it }
        return null
    }

    private fun isLocalDeclared(name: String) = findLocalConst(name) != null
    private fun checkExpr(expr: LHExpression)          = expr.accept(this)
    private fun checkBlock(stmts: List<LHStatement>)   = stmts.forEach { it.accept(this) }
    
    override fun literal(lit: LHLiteral)          {}
    override fun thisRef(expr: LHThis)            {}
    override fun loopControl(ctrl: LHLoopControl) {}

    override fun localGet(expr: LHLocalGet) {
        val name = expr.name
        if (isLocalDeclared(name))               return
        if (symTable.findVariable(name) != null) return
        if (symTable.findFunction(name) != null) return
        if (symTable.findClass(name)    != null) return  // 类名作为构造器引用
        error("Unresolved reference: '$name'", expr.row, expr.col)
    }

    override fun fieldGet(expr: LHFieldGet) {
        checkExpr(expr.receiver)
        val cls = resolveClass(expr.receiver) ?: return  // 原始类型 / 未知 → 跳过
        if (!cls.hasField(expr.field))
            error("Unresolved field '${expr.field}' on '${cls.name}'", expr.row, expr.col)
    }

    override fun call(expr: LHCall) {
        expr.args.forEach { checkExpr(it) }
        if (expr.receiver == null) {
            val name = expr.name
            if (symTable.findClass(name)    != null) return  // 构造器
            if (name in BUILTINS)                    return
            if (symTable.findFunction(name) != null) return
            if (isLocalDeclared(name))               return  // 局部 lambda / local fun
            error("Unresolved function: '$name'", expr.row, expr.col)
        } else {
            checkExpr(expr.receiver)
            val cls = resolveClass(expr.receiver) ?: return  // 原始类型 → 跳过
            if (!cls.hasMethod(expr.name) && !cls.hasStaticField(expr.name))
                error("Unresolved method '${expr.name}' on '${cls.name}'", expr.row, expr.col)
        }
    }

    override fun returnStmt(stmt: LHReturn) { stmt.value?.let { checkExpr(it) } }

    override fun exprStmt(stmt: LHExprStmt) = checkExpr(stmt.expr)

    override fun varDecl(decl: LHVarDecl) {
        decl.init?.let { checkExpr(it) }
        // 顶层 var 已由 SymbolCollector 注册，只在函数体内才加入局部作用域
        if (scopes.isNotEmpty()) declareLocal(decl.name, decl.isConstant)
    }

    override fun funDecl(decl: LHFunction) {
        // local fun 已被 HirGenerator 降级为 LHVarDecl+LHLambda，此分支保留备用
        if (scopes.isNotEmpty()) declareLocal(decl.name, isConstant = true)
        pushScope()
        decl.params.forEach { declareLocal(it.name, isConstant = true) }
        checkBlock(decl.body)
        popScope()
    }

    override fun classDecl(decl: LHClass) {
        val saved = currentClass
        currentClass = symTable.findClass(decl.name)
        pushScope()
        decl.fields.forEach { f ->
            f.init?.let { checkExpr(it) }
            declareLocal(f.name, f.isConstant)
        }
        decl.methods.filterIsInstance<LHFunction>().forEach { funDecl(it) }
        popScope()
        currentClass = saved
    }

    override fun ifStmt(stmt: LHIf) {
        checkExpr(stmt.cond)
        pushScope(); checkBlock(stmt.then); popScope()
        stmt.els?.let { pushScope(); checkBlock(it); popScope() }
    }

    override fun whileStmt(stmt: LHWhile) {
        checkExpr(stmt.cond)
        pushScope(); checkBlock(stmt.body); popScope()
    }

    override fun forStmt(stmt: LHFor) {
        pushScope()
        stmt.init?.accept(this)
        checkExpr(stmt.cond)
        stmt.update?.accept(this)
        pushScope(); checkBlock(stmt.body); popScope()
        popScope()
    }

    override fun lambdaExpr(expr: LHLambda) {
        pushScope()
        expr.params.forEach { declareLocal(it.name, isConstant = true) }
        checkBlock(expr.body)
        popScope()
    }

    override fun castExpr(expr: LHCast)   = checkExpr(expr.expr)

    override fun deferStmt(stmt: LHDefer) = checkExpr(stmt.expr)

    override fun assignStmt(stmt: LHAssign) {
        checkExpr(stmt.value)
        when (val tgt = stmt.target) {
            is LHLocalTarget -> {
                val localConst = findLocalConst(tgt.name)
                when {
                    localConst != null ->
                        if (localConst) error("Cannot reassign val '${tgt.name}'", stmt.row, stmt.col)
                    symTable.findVariable(tgt.name) != null ->
                        if (symTable.findVariable(tgt.name)!!.isConstant)
                            error("Cannot reassign val '${tgt.name}'", stmt.row, stmt.col)
                    else ->
                        error("Unresolved reference: '${tgt.name}'", stmt.row, stmt.col)
                }
            }
            is LHFieldTarget -> {
                checkExpr(tgt.receiver)
                val cls = resolveClass(tgt.receiver) ?: return
                val field = cls.findField(tgt.field)
                if (field == null)
                    error("Unresolved field '${tgt.field}' on '${cls.name}'", stmt.row, stmt.col)
                else if (field.isConstant)
                    error("Cannot reassign val field '${tgt.field}'", stmt.row, stmt.col)
            }
        }
    }

    override fun whenStmt(stmt: LHWhen) {
        stmt.subject?.let { checkExpr(it) }
        for (branch in stmt.branches) {
            pushScope()
            when (branch) {
                is LHTypeBranch -> {
                    val cls = symTable.findClass(branch.typeName)
                    if (cls == null) {
                        error("Unresolved type '${branch.typeName}' in when branch", stmt.row, stmt.col)
                    } else {
                        val fieldList = cls.fields.values.toList()
                        branch.destructured.forEachIndexed { i, name ->
                            if (i >= fieldList.size)
                                error("No field at index $i in '${cls.name}' for destructuring", stmt.row, stmt.col)
                            declareLocal(name, isConstant = true)
                        }
                    }
                }
                is LHExprBranch -> checkExpr(branch.cond)
            }
            branch.guard?.let { checkExpr(it) }
            checkBlock(branch.body)
            popScope()
        }
        stmt.elseBranch?.let { pushScope(); checkBlock(it); popScope() }
    }

    private fun resolveClass(expr: LHExpression): ClassSym? {
        if (expr is LHThis) return currentClass
        return symTable.findClass(expr.inferredType?.name ?: return null)
    }

    private fun ClassSym.hasStaticField(name: String): Boolean =
        fields[name]?.modifiers?.contains("static") == true

    private fun ClassSym.hasField(name: String): Boolean =
        name in fields ||
            (superClass != LType.ANY && symTable.findClass(superClass.name)?.hasField(name) == true)

    private fun ClassSym.hasMethod(name: String): Boolean =
        name in methods ||
            (superClass != LType.ANY && symTable.findClass(superClass.name)?.hasMethod(name) == true)

    private fun ClassSym.findField(name: String): LHField? =
        fields[name] ?: if (superClass == LType.ANY) null
        else symTable.findClass(superClass.name)?.findField(name)

    private fun error(msg: String, row: Int, col: Int) =
        diagnostics.add(msg, DiagnosticLevel.Error, "", row, col)

    companion object {
        private val BUILTINS = setOf("println", "print", "toString", "toInt", "toDecimal", "readLine")
    }
}