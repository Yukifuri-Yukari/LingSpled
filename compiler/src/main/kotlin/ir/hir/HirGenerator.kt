package yukifuri.lang.lingspled.compiler.ir.hir

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.base.StatementWrapper
import yukifuri.lang.lingspled.compiler.ast.clazz.LClass
import yukifuri.lang.lingspled.compiler.ast.conditional.*
import yukifuri.lang.lingspled.compiler.ast.expr.AsExpr
import yukifuri.lang.lingspled.compiler.ast.expr.*
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAccess
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAssign
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.function.LFunctionCall
import yukifuri.lang.lingspled.compiler.ast.function.LambdaExpr
import yukifuri.lang.lingspled.compiler.ast.function.Return
import yukifuri.lang.lingspled.compiler.ast.literal.Literal
import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.module.PackageDeclaration
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.variable.VariableAssign
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHField
import yukifuri.lang.lingspled.compiler.ir.hir.decl.LHParam
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFile
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHModule
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.libs.core.colorama.Fore

class HirGenerator : AstVisitor {
    private val exprStack = ArrayDeque<LHExpression>()
    private val blockStack = ArrayDeque<MutableList<LHStatement>>()

    private val definitions = mutableListOf<LHStatement>()

    fun generate(module: Module): LHFile {
        module.accept(this)
        return LHFile(definitions)
    }

    private fun emit(stmt: LHStatement) = blockStack.last().add(stmt)
    private fun popExpr() = exprStack.removeLast()
    private fun pushExpr(e: LHExpression) = exprStack.addLast(e)

    private fun buildBlock(module: Module): List<LHStatement> {
        blockStack.addLast(mutableListOf())
        for (item in module) {
            item.accept(this)
            if (exprStack.isNotEmpty()) {
                for (expr in exprStack.iterator()) {
                    blockStack.last().add(LHExprStmt(expr))
                }
                exprStack.clear()
            }
        }
        return blockStack.removeLast()
    }

    private fun buildSingle(stmt: Statement): LHStatement? {
        blockStack.addLast(mutableListOf())
        stmt.accept(this)
        return blockStack.removeLast().firstOrNull()
    }

    override fun literal(literal: Literal<*>) {
        pushExpr(LHLiteral(literal.value!!).also { it.at(literal.row, literal.col) })
    }

    override fun variableGet(expr: VariableGet) {
        pushExpr(LHLocalGet(expr.name).also { it.at(expr.row, expr.col) })
    }

    override fun thisRef(expr: ThisExpr) {
        pushExpr(LHThis().also { it.at(expr.row, expr.col) })
    }

    override fun fieldAccess(expr: FieldAccess) {
        expr.receiver.accept(this)
        pushExpr(LHFieldGet(popExpr(), expr.fieldName).also { it.at(expr.row, expr.col) })
    }

    override fun binaryOp(expr: BinaryExpr) {
        expr.l.accept(this)
        expr.r.accept(this)
        val r = popExpr()
        val l = popExpr()
        // Ne 特殊处理: a != b  ->  !(a.equals(b))
        when (expr.op) {
            Operator.Ne -> {
                val eq = LHCall(l, "equals", listOf(r)).also { it.at(expr.row, expr.col) }
                pushExpr(LHCall(eq, "not", emptyList()).also { it.at(expr.row, expr.col) })
            }
            Operator.Is    -> pushExpr(LHCall(l, "is",    listOf(r)).also { it.at(expr.row, expr.col) })
            Operator.NotIs -> pushExpr(LHCall(LHCall(l, "is", listOf(r)).also { it.at(expr.row, expr.col) }, "not", emptyList()).also { it.at(expr.row, expr.col) })
            Operator.In    -> pushExpr(LHCall(l, "in",    listOf(r)).also { it.at(expr.row, expr.col) })
            Operator.NotIn -> pushExpr(LHCall(LHCall(l, "in", listOf(r)).also { it.at(expr.row, expr.col) }, "not", emptyList()).also { it.at(expr.row, expr.col) })
            Operator.Elvis -> pushExpr(LHCall(l, "elvis", listOf(r)).also { it.at(expr.row, expr.col) })
            else -> pushExpr(LHCall(l, expr.op.toMethodName(), listOf(r)).also { it.at(expr.row, expr.col) })
        }
    }

    override fun unaryOp(expr: UnaryExpr) {
        expr.expr.accept(this)
        pushExpr(LHCall(popExpr(), expr.op.toMethodName(), emptyList()).also { it.at(expr.row, expr.col) })
    }

    override fun functionCall(f: LFunctionCall) {
        val args = f.arguments.map { it.accept(this); popExpr() }
        val typeArgs = f.typeArgs
        pushExpr(LHCall(null, f.name, args, typeArgs).also { it.at(f.row, f.col) })
    }

    override fun methodCall(expr: MethodCall) {
        expr.receiver.accept(this)
        val receiver = popExpr()
        val args = expr.arguments.map { it.accept(this); popExpr() }
        val typeArgs = expr.typeArgs
        pushExpr(LHCall(receiver, expr.methodName, args, typeArgs).also { it.at(expr.row, expr.col) })
    }

    override fun invokeExpr(expr: InvokeExpr) {
        expr.callee.accept(this)
        val callee = popExpr()
        val args = expr.arguments.map { it.accept(this); popExpr() }
        pushExpr(LHCall(callee, "invoke", args).also { it.at(expr.row, expr.col) })
    }

    override fun functionReturn(funcReturn: Return) {
        funcReturn.expr.accept(this)
        emit(LHReturn(popExpr()).also { it.at(funcReturn.row, funcReturn.col) })
    }

    override fun loopCtrl(controller: LoopControl) {
        emit(LHLoopControl(controller is Break).also { it.at(controller.row, controller.col) })
    }

    override fun variableDecl(decl: VariableDecl) {
        val init = decl.initialize?.let { it.accept(this); popExpr() }
        val hirDecl = LHVarDecl(decl.name, decl.modifier, decl.isConstant, init, decl.type).also { it.at(decl.row, decl.col) }
        if (blockStack.isEmpty()) definitions.add(hirDecl)
        else emit(hirDecl)
    }

    override fun functionDecl(f: LFunction) {
        if ("local" in f.modifier) {
            val params = f.arguments.map { LHParam(it.name, it.type) }
            val body = buildBlock(f.body)
            val captures = analyzeCaptures(f.body, f.arguments.map { it.name }.toSet())
            emit(LHVarDecl(f.name, isConstant = true, init = LHLambda(params, body, captures).also { it.at(f.row, f.col) }).also { it.at(f.row, f.col) })
            return
        }
        val body = buildBlock(f.body)
        val typeParams = f.typeParams.map { it.name }
        val params = f.arguments.map { LHParam(it.name, it.type) }
        definitions.add(LHFunction(f.name, f.modifier, typeParams, params, f.returnType, body).also { it.at(f.row, f.col) })
    }

    override fun variableAssign(assign: VariableAssign) {
        assign.value.accept(this)
        val rhs = popExpr()
        val target = LHLocalTarget(assign.name)
        // 复合赋值脱糖: x += v  ->  x = x.plus(v)
        val value = if (assign.operator == Operator.Assign) rhs
        else LHCall(LHLocalGet(assign.name).also { it.at(assign.row, assign.col) }, assign.operator.extractAssignment().toMethodName(), listOf(rhs)).also { it.at(assign.row, assign.col) }
        emit(LHAssign(target, value).also { it.at(assign.row, assign.col) })
    }

    override fun fieldAssign(expr: FieldAssign) {
        expr.receiver.accept(this)
        val receiver = popExpr()
        expr.value.accept(this)
        emit(LHAssign(LHFieldTarget(receiver, expr.fieldName), popExpr()).also { it.at(expr.row, expr.col) })
    }

    override fun visitIf(condIf: If) {
        condIf.cond.accept(this)
        val cond = popExpr()
        val then = buildBlock(condIf.then)
        val els = condIf.els?.let { buildBlock(it) }
        pushExpr(LHIf(cond, then, els).also { it.at(condIf.row, condIf.col) })
    }

    override fun visitWhile(whl: While) {
        whl.cond.accept(this)
        val cond = popExpr()
        emit(LHWhile(cond, buildBlock(whl.body)).also { it.at(whl.row, whl.col) })
    }

    override fun visitFor(forLoop: For) {
        val init = buildSingle(forLoop.init)
        forLoop.cond.accept(this)
        val cond = popExpr()
        val update = buildSingle(forLoop.update)
        emit(LHFor(init, cond, update, buildBlock(forLoop.body)).also { it.at(forLoop.row, forLoop.col) })
    }

    override fun lambdaExpr(expr: LambdaExpr) {
        val params = expr.arguments.map { LHParam(it.name, it.type) }
        val body = buildBlock(expr.body)
        // captures 留空, 由后续 ClosureAnalysisPass 填充
        pushExpr(LHLambda(params, body, emptyList()).also { it.at(expr.row, expr.col) })
    }

    override fun clazz(klass: LClass) {
        val fields = klass.variables.map { v ->
            val init = v.initialize?.let { it.accept(this); popExpr() }
            LHField(v.name, v.modifier, v.isConstant, v.type, init)
        }
        val methods = klass.statements.filterIsInstance<LFunction>().map { f ->
            val typeParams = f.typeParams.map { it.name }
            val params = f.arguments.map { LHParam(it.name, it.type) }
            val body = buildBlock(f.body)
            LHFunction(f.name, f.modifier, typeParams, params, f.returnType, body).also { it.at(f.row, f.col) }
        }
        // For interfaces, all entries in inheritances are super-interfaces (no super-class).
        // 对于接口，inheritances 中所有条目均为父接口（无父类）。
        val superClass = if (klass.isInterface) LType.ANY
                         else klass.inheritances.firstOrNull() ?: LType.ANY
        val classTypeParams = klass.typeParams.map { it.name }
        definitions.add(LHClass(klass.name, klass.modifier, superClass, fields, methods, klass.isInterface, classTypeParams).also { it.at(klass.row, klass.col) })
    }

    override fun visitDefer(defer: Defer) {
        defer.expr.accept(this)
        emit(LHDefer(popExpr()).also { it.at(defer.row, defer.col) })
    }

    override fun indexAccess(expr: IndexAccess) {
        expr.receiver.accept(this)
        val recv = popExpr()
        expr.index.accept(this)
        pushExpr(LHCall(recv, "get", listOf(popExpr())).also { it.at(expr.row, expr.col) })
    }

    override fun indexAssign(expr: IndexAssign) {
        expr.receiver.accept(this)
        val recv = popExpr()
        expr.index.accept(this)
        val idx = popExpr()
        expr.value.accept(this)
        emit(LHExprStmt(LHCall(recv, "set", listOf(idx, popExpr())).also { it.at(expr.row, expr.col) }).also { it.at(expr.row, expr.col) })
    }

    override fun visitWhen(expr: When) {
        val subject = expr.expr?.let { it.accept(this); popExpr() }

        val branches: List<LHWhenBranch> = expr.branches.map { branch ->
            when (branch) {
                is When.TypeBranch -> {
                    val guard = branch.guard?.let { it.accept(this); popExpr() }
                    LHTypeBranch(branch.typename, branch.destructured, guard, buildBlock(branch.module))
                }
                is When.ExprBranch -> {
                    branch.expr.accept(this)
                    val cond = popExpr()
                    val guard = branch.guard?.let { it.accept(this); popExpr() }
                    LHExprBranch(cond, guard, buildBlock(branch.module))
                }
            }
        }

        val elseBranch = expr.elseBranch?.let { buildBlock(it) }
        pushExpr(LHWhen(subject, branches, elseBranch))
    }

    override fun visitAs(expr: AsExpr) {
        expr.expr.accept(this)
        pushExpr(LHCast(popExpr(), expr.type).also { it.at(expr.row, expr.col) })
    }

    override fun pkg(declaration: PackageDeclaration) {}
    override fun importDecl(decl: ImportDeclaration) {}

    // Capturer
    // 扫描 lambda/local function 的 body, 返回引用了但未在内部声明的变量名. 
    // 遇到嵌套 lambda/local fun 时不递归进去(它们自己管自己的 captures).

    private fun analyzeCaptures(body: Module, params: Set<String>): List<String> {
        val declared = params.toMutableSet()
        val referenced = mutableSetOf<String>()
        scanModule(body, declared, referenced)
        return (referenced - declared).toList()
    }

    private fun scanModule(module: Module, declared: MutableSet<String>, referenced: MutableSet<String>) {
        for (stmt in module.statements) scanStmt(stmt, declared, referenced)
    }

    private fun scanStmt(stmt: Statement, declared: MutableSet<String>, referenced: MutableSet<String>) {
        when (stmt) {
            is VariableDecl -> {
                stmt.initialize?.let { scanExpr(it, declared, referenced) }; declared.add(stmt.name)
            }

            is LFunction -> declared.add(stmt.name)   // 嵌套 local fn: 不递归, 只标记名字
            is Return -> scanExpr(stmt.expr, declared, referenced)
            is If -> {
                scanExpr(stmt.cond, declared, referenced); scanModule(
                    stmt.then,
                    declared,
                    referenced
                ); stmt.els?.let { scanModule(it, declared, referenced) }
            }

            is While -> {
                scanExpr(stmt.cond, declared, referenced); scanModule(stmt.body, declared, referenced)
            }

            is For -> {
                scanStmt(stmt.init, declared, referenced); scanExpr(
                    stmt.cond,
                    declared,
                    referenced
                ); scanStmt(stmt.update, declared, referenced); scanModule(stmt.body, declared, referenced)
            }

            is VariableAssign -> scanExpr(stmt.value, declared, referenced)
            is FieldAssign -> {
                scanExpr(stmt.receiver, declared, referenced); scanExpr(stmt.value, declared, referenced)
            }

            is StatementWrapper -> scanExpr(stmt.expression, declared, referenced)
            is Defer -> scanExpr(stmt.expr, declared, referenced)
            is When -> scanExpr(stmt, declared, referenced)
            else -> {}
        }
    }

    private fun scanExpr(expr: Expression, declared: MutableSet<String>, referenced: MutableSet<String>) {
        when (expr) {
            is VariableGet -> referenced.add(expr.name)
            is BinaryExpr -> {
                scanExpr(expr.l, declared, referenced); scanExpr(expr.r, declared, referenced)
            }

            is UnaryExpr -> scanExpr(expr.expr, declared, referenced)
            is LFunctionCall -> expr.arguments.forEach { scanExpr(it, declared, referenced) }
            is MethodCall -> {
                scanExpr(expr.receiver, declared, referenced); expr.arguments.forEach {
                    scanExpr(
                        it,
                        declared,
                        referenced
                    )
                }
            }

            is FieldAccess -> scanExpr(expr.receiver, declared, referenced)
            is InvokeExpr -> {
                scanExpr(expr.callee, declared, referenced); expr.arguments.forEach {
                    scanExpr(
                        it,
                        declared,
                        referenced
                    )
                }
            }

            is Module -> scanModule(expr, declared, referenced)
            is LambdaExpr -> {}   // 嵌套 lambda: 不递归
            is When -> {
                expr.expr?.let { scanExpr(it, declared, referenced) }
                for (branch in expr.branches) {
                    branch.guard?.let { scanExpr(it, declared, referenced) }
                    val branchDeclared = declared.toMutableSet()
                    when (branch) {
                        is When.TypeBranch -> {
                            // 解构绑定的变量名在 branch body 内可见
                            branchDeclared.addAll(branch.destructured)
                            scanModule(branch.module, branchDeclared, referenced)
                        }
                        is When.ExprBranch -> {
                            scanExpr(branch.expr, declared, referenced)
                            scanModule(branch.module, branchDeclared, referenced)
                        }
                    }
                }
                expr.elseBranch?.let { scanModule(it, declared, referenced) }
            }
            else -> {}
        }
    }
}