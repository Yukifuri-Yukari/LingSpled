package yukifuri.lang.lingspled.compiler.ir.fst

import yukifuri.lang.lingspled.compiler.ast.LAArgument
import yukifuri.lang.lingspled.compiler.ast.LABinaryExpr
import yukifuri.lang.lingspled.compiler.ast.LABinaryExpr.BinaryOperator
import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAFieldAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAIndexAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAInvokeExpr
import yukifuri.lang.lingspled.compiler.ast.LALiteral
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAStringTemplate
import yukifuri.lang.lingspled.compiler.ast.LAUnaryExpr
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.ast.cls.LAClass
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassAttribute
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructor
import yukifuri.lang.lingspled.compiler.ast.cls.LAClassConstructorParameter
import yukifuri.lang.lingspled.compiler.ast.cls.LAConstructorDelegation
import yukifuri.lang.lingspled.compiler.ast.cls.LADeinitBlock
import yukifuri.lang.lingspled.compiler.ast.cls.LAEnumEntry
import yukifuri.lang.lingspled.compiler.ast.cls.LAInitBlock
import yukifuri.lang.lingspled.compiler.ast.cls.LAPrimaryConstructor
import yukifuri.lang.lingspled.compiler.ast.control.LABreak
import yukifuri.lang.lingspled.compiler.ast.control.LACatch
import yukifuri.lang.lingspled.compiler.ast.control.LAContinue
import yukifuri.lang.lingspled.compiler.ast.control.LADoWhile
import yukifuri.lang.lingspled.compiler.ast.control.LAFor
import yukifuri.lang.lingspled.compiler.ast.control.LAIf
import yukifuri.lang.lingspled.compiler.ast.control.LALambda
import yukifuri.lang.lingspled.compiler.ast.control.LAThrow
import yukifuri.lang.lingspled.compiler.ast.control.LATry
import yukifuri.lang.lingspled.compiler.ast.control.LAWhile
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFile
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.util.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.util.LTypeReference
import yukifuri.lang.lingspled.compiler.util.LTypeParamRef
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.util.Operator

/**
 * AST → FST 的 lowering，以 visitor pattern 分发（[LAVisitor]）。脱糖在此顺手完成
 * （设计：lowering 即脱糖，不另起 FST→FST pass）。
 *
 * [LAVisitor] 方法返回 [Unit]，故用「结果字段回填」：每个 `visit` 把产物写入 [result]，
 * [lowerExpr]/[lowerStmt] 调 `accept` 后读回。两类节点**不走 accept**：
 * - [LAModule] 的 `accept` 是 fan-out 不产值——块体一律用 [lowerModule] 手动降；
 * - 次构造器/`init`/`deinit`/属性是 [LAFunction]/[LAVariableDecl] 子类，经 `accept` 会被
 *   分发到 `funcDecl`/`varDecl` 而丢失子类信息——[LAClass] 成员在 [lowerClass] 内按类型直接降。
 *
 * 已实现脱糖：
 * - **主构造器属性合成**：[LAClass.primaryCtor] 的 `val/var` 参数（`mutable != null`）合成
 *   [LFClassAttribute]（`synthesizedFromCtor = true`）追加到 [LFClass.attr]。`this.x = x` 赋值
 *   注入与 `init` 块交错顺序留给 HIR，FST 阶段只负责声明出属性。
 */
class FstGenerator : LAVisitor {
    private var result: LFExpression? = null

    /** 类型参数作用域栈：进入 class/function/lambda 时 push，离开时 pop。
     * lowering 类型引用时，若名字匹配栈中某层声明的类型参数，则替换为 [LTypeParamRef]。 */
    private val typeParamStack = ArrayDeque<List<LTypeParamDecl>>()

    fun generate(module: LAModule): LFModule = lowerModule(module)

    private companion object {
        /** 复合赋值算符 → 底层二元算符（脱糖 `a op= b` 为 `a = a op b`）。 */
        val COMPOUND_ASSIGN = mapOf(
            Operator.PlusAssign to Operator.Plus,
            Operator.MinusAssign to Operator.Minus,
            Operator.TimesAssign to Operator.Mul,
            Operator.DivAssign to Operator.Div,
            Operator.RemAssign to Operator.Rem,
        )
    }


    private fun lowerModule(m: LAModule): LFModule =
        LFModule(m.statements.map(::lowerStmt), m.position)

    private fun lowerTypeRef(ref: LTypeRef): LTypeReference {
        // 占位符单例直传：保持 === 身份，供 TypeInferencePass/ResolutionPass 的 identity 检查使用
        if (ref === LTypeRef.any || ref === LTypeRef.unit || ref === LTypeRef.infer) return ref

        val loweredArgs = ref.tp.map {
            when (it) {
                is LTypeRef -> lowerTypeRef(it)
                else -> it
            }
        }
        val base = if (loweredArgs == ref.tp) ref else ref.copy(tp = loweredArgs)
        if (base.tp.isNotEmpty()) return base
        for (layer in typeParamStack.reversed()) {
            layer.find { it.id == base.name }?.let {
                return LTypeParamRef(it.id, base.variance, base.nullable)
            }
        }
        return base
    }

    private inline fun <R> withTypeParams(tp: List<LTypeParamDecl>, block: () -> R): R {
        typeParamStack.addLast(tp)
        val r = block()
        typeParamStack.removeLast()
        return r
    }

    private fun lowerStmt(s: LAStatement): LFStatement {
        val lowered = lowerExpr(s)
        return lowered as? LFStatement ?: LFExprStatement(lowered)
    }

    private fun lowerExpr(e: LAExpression): LFExpression {
        e.accept(this)
        return result!!
    }

    private fun lowerInvoke(e: LAInvokeExpr): LFInvokeExpr = lowerExpr(e) as LFInvokeExpr


    override fun packageDecl(decl: LAFile.LAPackageDeclaration) {
        result = LFFile.LFPackageDeclaration(decl.part, decl.position)
    }
    
    override fun importDecl(decl: LAFile.LAImportDeclaration) {
        result = LFFile.LFImportDeclaration(decl.part, decl.wildcard, decl.alias, decl.position)
    }

    override fun funcDecl(decl: LAFunction) {
        result = lowerFunction(decl)
    }

    override fun varDecl(decl: LAVariableDecl) {
        result = LFVariableDecl(
            decl.annotations.map(::lowerAnno), decl.access, decl.modifiers, decl.mutable, decl.name,
            decl.type?.let(::lowerTypeRef), decl.init?.let(::lowerExpr), decl.delegator?.let(::lowerExpr), decl.position,
        )
    }

    override fun clsDecl(decl: LAClass) {
        result = lowerClass(decl)
    }

    override fun ifExpr(expr: LAIf) {
        val cond = lowerExpr(expr.condition)
        val then = lowerModule(expr.then)
        val elseBranch = expr.elseBranch?.let(::lowerModule)
        result = LFIf(cond, then, elseBranch, expr.position)
    }

    override fun whileStmt(stmt: LAWhile) {
        val cond = lowerExpr(stmt.condition)
        result = LFWhile(cond, lowerModule(stmt.body), stmt.position)
    }

    override fun doWhileStmt(stmt: LADoWhile) {
        val body = lowerModule(stmt.body)
        result = LFDoWhile(body, lowerExpr(stmt.condition), stmt.position)
    }

    // for 保持结构化（LFFor），到 HIR（类型推断后）再按实例类型降级——见 forStmt 设计注记
    override fun forStmt(stmt: LAFor) {
        val iterable = lowerExpr(stmt.iterable)
        result = LFFor(stmt.variable, stmt.type?.let(::lowerTypeRef), iterable, lowerModule(stmt.body), stmt.position)
    }

    override fun tryExpr(expr: LATry) {
        val body = lowerModule(expr.body)
        val catches = expr.catches.map(::lowerCatch)
        val finallyBlock = expr.finallyBlock?.let(::lowerModule)
        result = LFTry(body, catches, finallyBlock, expr.position)
    }

    override fun throwExpr(expr: LAThrow) {
        result = LFThrow(lowerExpr(expr.expr), expr.position)
    }

    override fun lambdaExpr(expr: LALambda) {
        val params = expr.params.map(::lowerParam)
        result = LFLambda(params, lowerModule(expr.body), expr.position)
    }

    override fun breakStmt(stmt: LABreak) {
        result = LFBreak(stmt.position)
    }

    override fun continueStmt(stmt: LAContinue) {
        result = LFContinue(stmt.position)
    }

    override fun literalExpr(expr: LALiteral<*>) {
        result = when (expr) {
            is LALiteral.LALInteger -> LFLiteral.LFLInteger(expr.value, expr.position)
            is LALiteral.LALLong -> LFLiteral.LFLLong(expr.value, expr.position)
            is LALiteral.LAFloat -> LFLiteral.LFLFloat(expr.value, expr.position)
            is LALiteral.LADouble -> LFLiteral.LFLDouble(expr.value, expr.position)
            is LALiteral.LABoolean -> LFLiteral.LFLBoolean(expr.value, expr.position)
            is LALiteral.LALNull -> LFLiteral.LFLNull(expr.position)
            is LALiteral.LALString -> LFLiteral.LFLString(expr.value, expr.position)
            is LALiteral.LAThis -> LFLiteral.LFThis(expr.position)
        }
    }

    // 字符串插值脱糖：`"a${x}b"` → `"" + "a" + x + "b"`（纯语法脱糖，结果恒为 String）
    override fun stringTemplate(node: LAStringTemplate) {
        var acc: LFExpression = LFLiteral.LFLString("", node.position)
        for (part in node.parts)
            acc = LFBinaryExpr(acc, BinaryOperator.op(Operator.Plus), lowerExpr(part), node.position)
        result = acc
    }

    override fun fieldAccessExpr(expr: LAFieldAccessExpr) {
        val receiver = expr.receiver?.let(::lowerExpr)
        result = LFFieldAccessExpr(receiver, expr.field, expr.position)
    }

    override fun indexAccessExpr(expr: LAIndexAccessExpr) {
        val receiver = lowerExpr(expr.receiver)
        result = LFIndexAccessExpr(receiver, lowerExpr(expr.index), expr.position)
    }

    override fun unaryExpr(expr: LAUnaryExpr) {
        val target = lowerExpr(expr.expr)
        result = if (expr.op == Operator.Inc || expr.op == Operator.Dec) {
            LFIncDec(expr.op, target, expr.prefix, expr.position)
        } else {
            LFUnaryExpr(expr.op, target, expr.prefix, expr.position)
        }
    }

    override fun binaryExpr(expr: LABinaryExpr) {
        val op = expr.op.op
        result = when {
            // `target = value`
            op == Operator.Assign ->
                LFAssign(lowerExpr(expr.left), lowerExpr(expr.right), expr.position)

            // 复合赋值 `a op= b` → `a = a op b`（target 两侧各 lower 一次得独立实例）
            op in COMPOUND_ASSIGN -> {
                val combined = LFBinaryExpr(
                    lowerExpr(expr.left),
                    BinaryOperator.op(COMPOUND_ASSIGN.getValue(op)),
                    lowerExpr(expr.right),
                    expr.position,
                )
                LFAssign(lowerExpr(expr.left), combined, expr.position)
            }

            else -> {
                val left = lowerExpr(expr.left)
                LFBinaryExpr(left, expr.op, lowerExpr(expr.right), expr.position)
            }
        }
    }

    override fun invokeExpr(expr: LAInvokeExpr) {
        val receiver = lowerExpr(expr.receiver)
        result = LFInvokeExpr(receiver, expr.arg.map(::lowerArg), expr.position)
    }

    override fun ret(stmt: LAFunction.LAReturnStmt) {
        result = LFFunction.LFReturnStmt(lowerExpr(stmt.expr), stmt.position)
    }

    private fun lowerClass(c: LAClass): LFClass = withTypeParams(c.tp) {
        // 主构造器属性合成：val/var 参数 → LFClassAttribute（无初值，赋值注入归 HIR）
        val synthesized = c.primaryCtor?.params.orEmpty()
            .filter { it.mutable != null }
            .map { p ->
                LFClassAttribute(
                    annotations = p.annotations.map(::lowerAnno),
                    access = p.access,
                    modifiers = emptyList(),
                    mutable = p.mutable!!,
                    name = p.name,
                    type = lowerTypeRef(p.type),
                    init = null,
                    delegator = null,
                    getter = null,
                    setter = null,
                    synthesizedFromCtor = true,
                    position = p.position,
                )
            }

        LFClass(
            annotations = c.annotations.map(::lowerAnno),
            access = c.access,
            modifiers = c.modifiers,
            kind = c.kind,
            name = c.name,
            tp = c.tp,
            superclass = lowerInvoke(c.superclass),
            interfaces = c.interfaces.map { lowerTypeRef(it) as LTypeRef },
            primaryCtor = c.primaryCtor?.let(::lowerPrimaryCtor),
            ctors = c.ctors.map(::lowerCtor),
            functions = c.functions.map(::lowerFunction),
            attr = c.attr.map(::lowerAttr) + synthesized,
            inits = c.inits.map(::lowerInit),
            deinit = c.deinit?.let(::lowerDeinit),
            nested = c.nested.map(::lowerClass),
            entries = c.entries.map(::lowerEnumEntry),
            position = c.position,
        )
    }

    private fun lowerEnumEntry(e: LAEnumEntry): LFEnumEntry = LFEnumEntry(
        e.annotations.map(::lowerAnno), e.name, e.args.map(::lowerArg), e.members.map(::lowerStmt), e.position,
    )

    private fun lowerPrimaryCtor(pc: LAPrimaryConstructor): LFPrimaryConstructor =
        LFPrimaryConstructor(pc.annotations.map(::lowerAnno), pc.access, pc.params.map(::lowerCtorParam), pc.position)

    private fun lowerCtorParam(p: LAClassConstructorParameter): LFClassConstructorParameter =
        LFClassConstructorParameter(
            p.annotations.map(::lowerAnno), p.access, p.mutable, p.name, lowerTypeRef(p.type),
            p.default?.let(::lowerExpr), p.position,
        )

    private fun lowerCtor(c: LAClassConstructor): LFClassConstructor =
        LFClassConstructor(
            c.annotations.map(::lowerAnno), c.access, c.params.map(::lowerParam),
            c.delegation?.let(::lowerDelegation), c.body?.let(::lowerModule), c.position,
        )

    private fun lowerInit(b: LAInitBlock): LFInitBlock = LFInitBlock(lowerModule(b.body!!), b.position)

    private fun lowerDeinit(b: LADeinitBlock): LFDeinitBlock = LFDeinitBlock(lowerModule(b.body!!), b.position)

    private fun lowerAttr(a: LAClassAttribute): LFClassAttribute =
        LFClassAttribute(
            annotations = a.annotations.map(::lowerAnno),
            access = a.access,
            modifiers = a.modifiers,
            mutable = a.mutable,
            name = a.name,
            type = a.type?.let(::lowerTypeRef),
            init = a.init?.let(::lowerExpr),
            delegator = a.delegator?.let(::lowerExpr),
            getter = a.getter?.let(::lowerFunction),
            setter = a.setter?.let(::lowerFunction),
            synthesizedFromCtor = false,
            position = a.position,
        )

    private fun lowerDelegation(d: LAConstructorDelegation): LFConstructorDelegation = when (d) {
        is LAConstructorDelegation.This -> LFConstructorDelegation.This(d.args.map(::lowerArg))
        is LAConstructorDelegation.Super -> LFConstructorDelegation.Super(d.args.map(::lowerArg))
    }

    private fun lowerFunction(f: LAFunction): LFFunction = withTypeParams(f.tp) {
        LFFunction(
            f.annotations.map(::lowerAnno), f.access, f.modifiers, f.tp,
            f.receiver?.let(::lowerTypeRef),
            f.name, f.params.map(::lowerParam), lowerTypeRef(f.ret),
            f.body?.let(::lowerModule), f.position,
        )
    }

    private fun lowerParam(p: LAParameter): LFParameter = LFParameter(p.name, lowerTypeRef(p.type), p.vararg, p.default?.let(::lowerExpr))

    private fun lowerArg(a: LAArgument): LFArgument = LFArgument(a.name, lowerExpr(a.value))

    private fun lowerAnno(a: LAAnnotation): LFAnnotation = LFAnnotation(a.name, a.args.map(::lowerArg))

    private fun lowerCatch(c: LACatch): LFCatch = LFCatch(c.name, lowerTypeRef(c.type), lowerModule(c.body), c.position)
}