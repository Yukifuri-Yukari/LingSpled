package yukifuri.lang.lingspled.compiler.ir.hir

import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHStatement
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFile
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable

/**
 * Hindley-Milner type inferrer operating on the untyped HIR.
 * Reads [LHFile] produced by [HirGenerator], walks every expression,
 * and writes the resolved [LType] into [LHExpression.inferredType].
 * Also resolves all [LType.INFER] placeholders on declarations.
 *
 * HM 类型推断器，作用于未类型化的 HIR。
 * 读取 [HirGenerator] 产出的 [LHFile]，遍历每个表达式节点，
 * 将解析后的 [LType] 写入 [LHExpression.inferredType]，
 * 同时将声明上所有的 [LType.INFER] 占位符替换为真实类型。
 *
 * @param symTable Symbol table populated by [yukifuri.lang.lingspled.compiler.ir.sym.SymbolCollector] (Pass 1).
 *                 由 [yukifuri.lang.lingspled.compiler.ir.sym.SymbolCollector]（第一趟）填充的符号表。
 */
class TypeResolver(private val symTable: SymbolTable) {
    
    private var varCounter = 0
    private fun fresh() = LType("τ${varCounter++}")

    /** Returns true if [t] is an inference type variable (name starts with τ, no type args).
     *  判断 [t] 是否为推断类型变量（以 τ 开头且无类型参数）。 */
    private fun isTypeVar(t: LType) = t.name.startsWith("τ") && t.typeArgs.isEmpty()
    /** Returns true if [t] is the unresolved placeholder emitted by the parser.
     *  判断 [t] 是否为解析器产出的未解析占位符。 */
    private fun isInfer(t: LType) = t == LType.INFER

    /**
     * The global substitution map: type-variable name → resolved type.
     * Unification writes here; [apply] reads here.
     * 全局替换表：类型变量名 → 已解析的类型。
     * 合一（unify）写入此表，apply 从此表读取。
     */
    private val subst = mutableMapOf<String, LType>()

    /**
     * Recursively apply [subst] to [t], following type-variable chains until stable.
     * For example: if subst = {τ0 → τ1, τ1 → Int}, then apply(τ0) = Int.
     *
     * 递归地将 [subst] 应用到 [t] 上，沿类型变量链追踪直到稳定。
     * 例：若 subst = {τ0 → τ1, τ1 → Int}，则 apply(τ0) = Int。
     */
    fun apply(t: LType): LType {
        if (isTypeVar(t)) {
            val resolved = subst[t.name] ?: return t   // unbound var / 未绑定变量，原样返回
            return apply(resolved)                      // chase chain / 沿链追踪
        }
        if (t.typeArgs.isEmpty()) return t
        return t.copy(typeArgs = t.typeArgs.map { apply(it) })
    }

    /**
     * Occurs check: does type variable [v] appear anywhere inside [t]?
     * Prevents binding a variable to a type that contains it (would create infinite type).
     *
     * 出现检查：类型变量 [v] 是否出现在 [t] 的任意位置？
     * 防止将变量绑定到包含它自身的类型（否则会构造出无限类型）。
     */
    private fun occurs(v: String, t: LType): Boolean =
        if (isTypeVar(t)) t.name == v
        else t.typeArgs.any { occurs(v, it) }

    /**
     * Bind type variable [v] to concrete type [t] in [subst].
     * Performs occurs check first; throws if [v] appears in [t].
     *
     * 在 [subst] 中将类型变量 [v] 绑定到具体类型 [t]。
     * 先做出现检查；若 [v] 出现在 [t] 中则抛出异常。
     */
    private fun bind(v: String, t: LType) {
        if (isTypeVar(t) && t.name == v) return            // trivial: same var / 平凡情况，同一变量
        if (occurs(v, t)) error("Occurs check failed: $v in ${t.typename()}")
        subst[v] = t
    }

    /** Width order of numeric types; higher index = wider type.
     *  数值类型宽度顺序；下标越大越宽。 */
    private val numericWidth = mapOf(
        "Byte" to 0, "Short" to 1, "Int" to 2, "Long" to 3, "Float" to 4, "Double" to 5
    )

    /**
     * Return the wider of two numeric types, or null if either is non-numeric.
     * Used instead of strict equality when unifying arithmetic operands.
     *
     * 返回两个数值类型中较宽的那个；若任意一方不是数值类型则返回 null。
     * 合一算术运算数时以此代替严格相等判断。
     */
    private fun promoteNumeric(a: LType, b: LType): LType? {
        val wa = numericWidth[a.name] ?: return null
        val wb = numericWidth[b.name] ?: return null
        return if (wa >= wb) a else b
    }

    /**
     * Unify types [a] and [b]: find a substitution that makes them equal,
     * update [subst] in place, and return the unified type.
     *
     * Rules (applied after chasing variable chains via [apply]):
     *  - Equal types → trivially unified.
     *  - Type variable on either side → bind it (with occurs check).
     *  - Both numeric → apply promotion rules, return the wider type.
     *  - Both function types (->) → unify slot by slot.
     *  - Same constructor, same arity → unify type arguments slot by slot.
     *  - Otherwise → type error.
     *
     * 合一类型 [a] 和 [b]：找到使二者相等的替换，原地更新 [subst]，返回合一后的类型。
     *
     * 规则（经 [apply] 展开类型变量链后适用）：
     *  - 相等类型 → 平凡合一。
     *  - 任意一侧为类型变量 → 绑定（含出现检查）。
     *  - 双方均为数值类型 → 应用提升规则，返回较宽类型。
     *  - 双方均为函数类型（->）→ 逐槽合一。
     *  - 相同构造器、相同元数 → 逐槽合一类型参数。
     *  - 其他情况 → 类型错误。
     */
    fun unify(a: LType, b: LType): LType {
        val ra = apply(a)
        val rb = apply(b)
        return when {
            ra == rb -> ra
            isTypeVar(ra) -> { bind(ra.name, rb); apply(rb) }
            isTypeVar(rb) -> { bind(rb.name, ra); apply(ra) }
            ra.name in numericWidth && rb.name in numericWidth ->
                promoteNumeric(ra, rb)!!
            ra.name == "->" && rb.name == "->" -> {
                require(ra.typeArgs.size == rb.typeArgs.size) {
                    "Arity mismatch: ${ra.typename()} vs ${rb.typename()}"
                }
                LType("->", ra.typeArgs.zip(rb.typeArgs).map { (x, y) -> unify(x, y) })
            }
            ra.name == rb.name && ra.typeArgs.size == rb.typeArgs.size ->
                LType(ra.name, ra.typeArgs.zip(rb.typeArgs).map { (x, y) -> unify(x, y) })
            else -> error("Type mismatch: ${ra.typename()} vs ${rb.typename()}")
        }
    }
    
    /**
     * Local variable type environment (name → type).
     * Saved/restored manually around function and class bodies to implement lexical scoping.
     *
     * 局部变量类型环境（变量名 → 类型）。
     * 在函数体和类体前后手动保存/恢复，以实现词法作用域。
     */
    private val env = mutableMapOf<String, LType>()

    /**
     * Type of `this` in the current class-method scope; null at top level.
     * Set when entering [inferClass], restored on exit.
     *
     * 当前类方法作用域中 `this` 的类型；顶层为 null。
     * 进入 [inferClass] 时设置，退出时还原。
     */
    private var thisType: LType? = null

    /**
     * A fresh type variable holding the expected return type for the current function.
     * Every [LHReturn] node unifies its value type against this variable.
     * Set at the start of each [inferFunction], restored on exit.
     *
     * 代表当前函数期望返回类型的新鲜类型变量。
     * 每个 [LHReturn] 节点将其值类型与该变量合一。
     * 在 [inferFunction] 开始时设置，退出时还原。
     */
    private var returnTypeVar: LType = LType.UNIT

    /**
     * Run type inference over the entire file.
     * After this call every [LHExpression] in the tree has a non-null [LHExpression.inferredType].
     *
     * 对整个文件运行类型推断。
     * 调用结束后，树中每个 [LHExpression] 的 [LHExpression.inferredType] 均非 null。
     */
    fun inferFile(file: LHFile) {
        for (module in file.statements) {
            when (module) {
                is LHClass    -> inferClass(module)
                is LHFunction -> inferFunction(module)
                is LHVarDecl  -> inferVarDecl(module)
                else          -> {}
            }
        }
    }

    /**
     * Infer types for a class: register field types, then infer each method body.
     * `this` is set to the class type for the duration of method inference.
     *
     * 推断类的类型：录入字段类型，然后推断每个方法体。
     * 在方法推断期间 `this` 被设置为当前类的类型。
     */
    private fun inferClass(cls: LHClass) {
        val savedThis = thisType
        thisType = LType(cls.name)

        for (field in cls.fields) {
            // If the declared type is a placeholder, allocate a fresh var.
            // 若声明类型是占位符，则分配一个新鲜变量。
            val declared = if (isInfer(field.type)) fresh() else field.type
            env[field.name] = declared
            field.init?.let { initExpr ->
                val initType = infer(initExpr)
                unify(declared, initType)
                val resolved = apply(declared)
                env[field.name] = resolved
                field.type = resolved
            }
        }

        for (method in cls.methods.filterIsInstance<LHFunction>()) {
            inferFunction(method)
        }

        thisType = savedThis
    }

    /**
     * Infer types for a single function or method.
     * Saves and restores [env] and [returnTypeVar] around the body.
     *
     * 推断单个函数或方法的类型。
     * 在函数体前后保存并还原 [env] 和 [returnTypeVar]。
     */
    private fun inferFunction(func: LHFunction) {
        val savedEnv       = env.toMutableMap()
        val savedReturnVar = returnTypeVar

        returnTypeVar = if (isInfer(func.returnType)) fresh() else func.returnType

        val typeParamMap = func.typeParams.associateWith { fresh() }

        for (param in func.params) {
            val declared = if (isInfer(param.type)) fresh() else param.type
            val resolved = substituteTypeParams(declared, typeParamMap)
            env[param.name] = resolved
            // do NOT write back into param.type - func may be a shared
            // symbol-table node reused across multiple call sites.
        }

        inferBlock(func.body)
        func.returnType = apply(returnTypeVar)
        finalizeInferredTypes(func.body)   // resolve any stale type-var references

        env.clear()
        env.putAll(savedEnv)
        returnTypeVar = savedReturnVar
    }

    private fun inferVarDecl(decl: LHVarDecl) {
        if (decl.init == null) {
            val declared = if (isInfer(decl.declaredType)) fresh() else decl.declaredType
            env[decl.name] = declared
            decl.inferredType = declared
        } else {
            val initType = infer(decl.init)
            val resolved = if (isInfer(decl.declaredType)) {
                apply(initType)
            } else {
                val typeParamMap = decl.declaredType.typeArgs.associate { it.name to fresh() }
                val declared = substituteTypeParams(decl.declaredType, typeParamMap)
                unify(declared, initType)
                apply(declared)
            }
            env[decl.name] = resolved
            decl.inferredType = resolved
        }
    }

    /** Re-apply [subst] to every inferredType in [stmts] and all sub-expressions after the body
     *  is fully inferred. Fixes stale τN references stored before their binding was established. */
    private fun finalizeInferredTypes(stmts: List<LHStatement>) {
        for (stmt in stmts) {
            stmt.inferredType?.let { stmt.inferredType = apply(it) }
            when (stmt) {
                is LHReturn   -> stmt.value?.let { finalizeExpr(it) }
                is LHExprStmt -> finalizeExpr(stmt.expr)
                is LHVarDecl  -> stmt.init?.let { finalizeExpr(it) }
                is LHAssign   -> {
                    finalizeExpr(stmt.value)
                    (stmt.target as? LHFieldTarget)?.let { finalizeExpr(it.receiver) }
                }
                is LHDefer    -> finalizeExpr(stmt.expr)
                is LHIf       -> {
                    finalizeExpr(stmt.cond)
                    finalizeInferredTypes(stmt.then)
                    stmt.els?.let { finalizeInferredTypes(it) }
                }
                is LHWhile    -> { finalizeExpr(stmt.cond); finalizeInferredTypes(stmt.body) }
                is LHFor      -> {
                    stmt.init?.let { finalizeInferredTypes(listOf(it)) }
                    finalizeExpr(stmt.cond)
                    (stmt.update as? LHExpression)?.let { finalizeExpr(it) }
                    finalizeInferredTypes(stmt.body)
                }
                is LHFunction -> finalizeInferredTypes(stmt.body)
                is LHWhen -> {
                    stmt.subject?.let { finalizeExpr(it) }
                    for (b in stmt.branches) {
                        if (b is LHExprBranch) finalizeExpr(b.cond)
                        b.guard?.let { finalizeExpr(it) }
                        finalizeInferredTypes(b.body)
                    }
                    stmt.elseBranch?.let { finalizeInferredTypes(it) }
                }
                else -> {}
            }
        }
    }

    private fun finalizeExpr(expr: LHExpression) {
        expr.inferredType?.let { expr.inferredType = apply(it) }
        when (expr) {
            is LHFieldGet  -> finalizeExpr(expr.receiver)
            is LHCall      -> { expr.receiver?.let { finalizeExpr(it) }; expr.args.forEach { finalizeExpr(it) } }
            is LHLambda    -> finalizeInferredTypes(expr.body)
            is LHCast      -> finalizeExpr(expr.expr)
            is LHIf        -> {
                finalizeExpr(expr.cond)
                finalizeInferredTypes(expr.then)
                expr.els?.let { finalizeInferredTypes(it) }
            }
            is LHAssign    -> {
                finalizeExpr(expr.value)
                (expr.target as? LHFieldTarget)?.let { finalizeExpr(it.receiver) }
            }
            is LHExprStmt  -> finalizeExpr(expr.expr)
            else           -> {}
        }
    }

    /**
     * Infer all statements in [stmts] in order.
     * Returns the type of the last statement (useful for block-as-expression), or [LType.UNIT].
     *
     * 按顺序推断 [stmts] 中所有语句。
     * 返回最后一条语句的类型（用于块作为表达式的场景），若为空则返回 [LType.UNIT]。
     */
    private fun inferBlock(stmts: List<LHStatement>): LType {
        var last: LType = LType.UNIT
        for (stmt in stmts) last = inferStmt(stmt)
        return last
    }

    /**
     * Infer a single statement. Returns a type (usually [LType.UNIT] for pure statements,
     * or the expression type for expression-statements used as values).
     *
     * 推断单条语句。纯语句通常返回 [LType.UNIT]，
     * 用作值的表达式语句返回其表达式类型。
     */
    private fun inferStmt(stmt: LHStatement): LType = when (stmt) {

        is LHVarDecl -> {
            val declared = if (isInfer(stmt.declaredType)) fresh() else stmt.declaredType
            val initType = stmt.init?.let { infer(it) } ?: declared
            val resolved = apply(unify(declared, initType))
            env[stmt.name] = resolved
            stmt.inferredType = resolved   // expose for diagnostics / 暴露给诊断打印
            LType.UNIT
        }

        is LHReturn -> {
            // Unify the value type with the current function's return type var.
            // 将返回值类型与当前函数的返回类型变量合一。
            val valType = stmt.value?.let { infer(it) } ?: LType.UNIT
            unify(returnTypeVar, valType)
            LType.UNIT
        }

        is LHAssign -> {
            val valType = infer(stmt.value)
            when (val tgt = stmt.target) {
                is LHLocalTarget -> env[tgt.name]?.let { unify(it, valType) }
                is LHFieldTarget -> {
                    val recvType = infer(tgt.receiver)
                    lookupFieldType(recvType, tgt.field)?.let { unify(it, valType) }
                }
            }
            LType.UNIT
        }

        is LHExprStmt -> {
            // Return the expression's type — inferBlock uses the last stmt's type as the
            // block's value (Kotlin-style implicit return). Codegen emits POP separately.
            // 返回表达式的类型——inferBlock 把最后一条语句的类型作为块的值
            // （Kotlin 风格的隐式返回）。codegen 单独处理弃值的 POP。
            infer(stmt.expr)
        }

        is LHIf -> {
            infer(stmt.cond)
            val thenType = inferBlock(stmt.then)
            stmt.els?.let { inferBlock(it) }
            thenType   // result type of the if-expression / if 表达式的结果类型
        }

        is LHWhile -> {
            infer(stmt.cond)
            inferBlock(stmt.body)
            LType.UNIT
        }

        is LHFor -> {
            stmt.init?.let { inferStmt(it) }
            infer(stmt.cond)
            stmt.update?.let { inferStmt(it) }
            inferBlock(stmt.body)
            LType.UNIT
        }

        is LHDefer -> { infer(stmt.expr); LType.UNIT }

        is LHLoopControl -> LType.UNIT

        is LHFunction -> {
            // Local function declaration inside a body / 函数体内的本地函数声明
            inferFunction(stmt)
            LType.UNIT
        }

        is LHWhen -> {
            val subjectType = stmt.subject?.let { infer(it) }
            var resultType  = LType.UNIT

            for (branch in stmt.branches) {
                when (branch) {
                    is LHTypeBranch -> {
                        // Bind destructured vars using the class's field declaration order.
                        // 按类字段声明顺序绑定解构变量。
                        val fieldList = symTable.root.classes[branch.typeName]
                            ?.fields?.values?.toList() ?: emptyList()
                        branch.destructured.forEachIndexed { i, name ->
                            val ft = fieldList.getOrNull(i)?.type ?: fresh()
                            env[name] = if (isInfer(ft)) fresh() else ft
                        }
                    }
                    is LHExprBranch -> {
                        val condType = infer(branch.cond)
                        // Subject-ful when: each branch cond must have the same type as subject.
                        // 有 subject 的 when：分支条件类型必须与 subject 类型一致。
                        if (subjectType != null) unify(subjectType, condType)
                    }
                }
                branch.guard?.let { infer(it) }
                resultType = inferBlock(branch.body)
            }

            stmt.elseBranch?.let { resultType = inferBlock(it) }
            stmt.inferredType = apply(resultType)
            resultType
        }

        // Fallthrough for unrecognized statement kinds / 未识别的语句类型
        else -> LType.UNIT
    }
    
    /**
     * Infer the type of [expr], store the result in [LHExpression.inferredType], and return it.
     * This is the main recursive dispatch for expressions.
     *
     * 推断 [expr] 的类型，将结果存入 [LHExpression.inferredType] 并返回。
     * 这是表达式推断的主递归分发函数。
     */
    fun infer(expr: LHExpression): LType {
        val t = when (expr) {
            is LHLiteral  -> inferLiteral(expr)
            is LHLocalGet -> inferLocalGet(expr)
            is LHThis     -> thisType ?: fresh()
            is LHFieldGet -> inferFieldGet(expr)
            is LHCall     -> inferCall(expr)
            is LHLambda   -> inferLambda(expr)
            is LHCast     -> { infer(expr.expr); expr.targetType }
            is LHIf       -> {
                // if used as an expression (pushed onto the expression stack by HirGenerator)
                // if 用作表达式（由 HirGenerator 压入表达式栈）
                infer(expr.cond)
                val thenType = inferBlock(expr.then)
                val elsType  = expr.els?.let { inferBlock(it) } ?: LType.UNIT
                if (expr.els != null) unify(thenType, elsType) else thenType
            }
            else -> fresh()  // unrecognized node: leave as an unbound fresh var / 未识别节点：留为未绑定新鲜变量
        }
        val resolved = apply(t)
        expr.inferredType = resolved
        return resolved
    }

    /**
     * Map a literal value to its primitive [LType].
     * 将字面量值映射到对应的基本 [LType]。
     */
    private fun inferLiteral(lit: LHLiteral): LType = when (lit.value) {
        is Int     -> LType.INT
        is Long    -> LType.LONG
        is Float   -> LType.FLOAT
        is Double  -> LType.DOUBLE
        is Boolean -> LType.BOOLEAN
        is String  -> LType.STRING
        else       -> LType.ANY
    }

    /**
     * Look up a variable by name.
     * Search order: local [env] → class names (for static-style access).
     * If completely unknown, allocate a fresh type var and register it so
     * later uses of the same name are consistent.
     *
     * 按名称查找变量。
     * 查找顺序：本地 [env] → 类名（用于静态式访问）。
     * 若完全未知，则分配新鲜类型变量并注册，使同名的后续引用保持一致。
     */
    private fun inferLocalGet(expr: LHLocalGet): LType {
        env[expr.name]?.let { return apply(it) }
        // Class name used as a value (e.g. static field access: ClassName.field)
        // 类名用作值（如静态字段访问：ClassName.field）
        symTable.root.classes[expr.name]?.let { return LType(it.name) }
        // Unknown: allocate and register a fresh var so all occurrences agree
        // 未知：分配并注册新鲜变量，使所有出现一致
        return fresh().also { env[expr.name] = it }
    }

    /**
     * Infer a field access `receiver.field`.
     * Resolves the receiver type first, then looks up the field in the class definition.
     *
     * 推断字段访问 `receiver.field`。
     * 先解析接收者类型，然后在类定义中查找字段。
     */
    private fun inferFieldGet(expr: LHFieldGet): LType {
        val recvType = infer(expr.receiver)
        return lookupFieldType(apply(recvType), expr.field) ?: fresh()
    }

    /**
     * Infer a call expression. Handles five cases:
     *  1. Constructor call — receiver is null, name matches a class.
     *  2. Builtin function call — receiver is null, name is a known builtin.
     *  3. User-defined function call — receiver is null, name is in the symbol table.
     *  4. `invoke` on a function type — receiver is a lambda/closure.
     *  5. Arithmetic/comparison operator — desugared method on a numeric type.
     *  6. General method call — look up in the receiver's class.
     *
     * 推断调用表达式。处理六种情况：
     *  1. 构造函数调用 — 接收者为 null，且名称匹配某个类。
     *  2. 内置函数调用 — 接收者为 null，且名称为已知内置函数。
     *  3. 用户定义函数调用 — 接收者为 null，且名称在符号表中。
     *  4. 对函数类型调用 invoke — 接收者为 lambda/闭包。
     *  5. 算术/比较运算符 — 脱糖为数值类型上的方法调用。
     *  6. 普通方法调用 — 在接收者的类中查找。
     */
    private fun inferCall(expr: LHCall): LType {
        // Infer all argument types up front so side-effects on [subst] propagate correctly.
        // 预先推断所有实参类型，确保对 [subst] 的副作用正确传播。
        val argTypes = expr.args.map { infer(it) }

        // ── Case 1–3: free function (no receiver) ──────────────────────────
        if (expr.receiver == null) {
            // Case 1: constructor — instantiate class type params, unify with ctor args,
            // and return LType(name, resolvedTypeArgs) for generic classes.
            // 情况 1：构造函数 — 实例化类型参数并与构造实参合一，泛型类返回带类型参数的具体类型。
            symTable.root.classes[expr.name]?.let { cls ->
                if (cls.typeParams.isEmpty()) return LType(cls.name)
                val typeParamMap = cls.typeParams.associateWith { fresh() }
                cls.methods["<constructor>"]?.params?.zip(argTypes)?.forEach { (p, a) ->
                    unify(substituteTypeParams(p.type, typeParamMap), a)
                }
                return LType(cls.name, cls.typeParams.map { apply(typeParamMap[it]!!) })
            }
            // Case 2: builtin / 情况 2：内置函数
            builtinReturnType(expr.name, argTypes)?.let { return it }
            // Case 3: user-defined / 情况 3：用户定义函数
            symTable.root.func[expr.name]?.let { sym ->
                return callFunction(sym.func, argTypes, expr.typeArgs)
            }
            return fresh()  // completely unknown / 完全未知
        }

        // ── Cases 4–6: method call (receiver present) ──────────────────────
        val recvType = apply(infer(expr.receiver))

        // Case 4: invoke on a function type / 情况 4：对函数类型调用 invoke
        if (expr.name == "invoke" && recvType.name == "->") {
            // Convention: typeArgs = [param1, param2, …, returnType], last slot is return.
            // 约定：typeArgs = [param1, param2, …, returnType]，最后一槽为返回类型。
            val paramTypes = recvType.typeArgs.dropLast(1)
            val retType    = recvType.typeArgs.last()
            paramTypes.zip(argTypes).forEach { (p, a) -> unify(p, a) }
            return apply(retType)
        }

        // Case 5: arithmetic operators — use numeric promotion instead of strict unify.
        // 情况 5：算术运算符 — 使用数值提升而非严格合一。
        if (expr.name in ARITHMETIC_OPS && recvType.name in numericWidth) {
            val other = argTypes.firstOrNull() ?: return apply(recvType)
            return promoteNumeric(apply(recvType), apply(other)) ?: apply(recvType)
        }

        // Comparison / equality operators always produce Boolean.
        // 比较/相等运算符始终产生 Boolean。
        if (expr.name in COMPARISON_OPS) return LType.BOOLEAN

        // Universal builtin methods (toString on any type, String concatenation, etc.)
        builtinMethodReturnType(recvType, expr.name, argTypes)?.let { return it }

        // Case 6: look up the method in the receiver's class definition.
        // 情况 6：在接收者的类定义中查找方法。
        val cls = symTable.root.classes[recvType.name]

        lookupMethod(recvType, expr.name)?.let { method ->
            val classMapping: Map<String, LType> =
                if (cls != null && cls.typeParams.isNotEmpty() &&
                    recvType.typeArgs.size == cls.typeParams.size)
                    cls.typeParams.zip(recvType.typeArgs).toMap()
                else emptyMap()
            return callFunction(method, argTypes, expr.typeArgs, classMapping)
        }

        // Static field call: ClassName.staticField(args) — field holds a lambda.
        cls?.fields?.get(expr.name)?.let { field ->
            if ("static" in field.modifiers) {
                val ft = apply(field.type)
                if (ft.name == "->" && ft.typeArgs.isNotEmpty()) {
                    val paramTypes = ft.typeArgs.dropLast(1)
                    val retType    = ft.typeArgs.last()
                    paramTypes.zip(argTypes).forEach { (p, a) -> unify(p, a) }
                    return apply(retType)
                }
                return ft
            }
        }

        return fresh()  // method not found in class / 方法未在类中找到
    }

    /**
     * Simulate calling [func] with [argTypes] and optional explicit [callTypeArgs].
     *
     * Steps:
     *  1. Map declared type params to either the explicit call-site type args or fresh vars.
     *  2. Substitute type params throughout the parameter types and return type.
     *  3. Unify each (formal param, actual arg) pair — this resolves the type vars.
     *  4. Apply [subst] to the return type and return.
     *
     * 模拟用 [argTypes] 调用 [func]，可带显式 [callTypeArgs]。
     *
     * 步骤：
     *  1. 将声明的类型参数映射到调用位置显式传入的类型实参，或分配新鲜变量。
     *  2. 将类型参数替换到形参类型和返回类型中。
     *  3. 逐对合一（形参, 实参）— 这一步解析类型变量。
     *  4. 对返回类型应用 [subst] 并返回。
     */
    private fun callFunction(
        func: LHFunction,
        argTypes: List<LType>,
        callTypeArgs: List<LType>,
        classTypeMapping: Map<String, LType> = emptyMap(),
    ): LType {
        val typeParamNames = func.typeParams.toSet()

        val typeParamMap: Map<String, LType> =
            if (callTypeArgs.size == func.typeParams.size)
                func.typeParams.zip(callTypeArgs).toMap()
            else
                func.typeParams.associateWith { fresh() }

        // use fresh() for any residual named type params left in param/return
        // types that are NOT in this function's own typeParams list.
        // This handles cases where LType("T") leaks from symbol-table sharing
        // (e.g. a method on a generic class uses the class-level T but the
        // method itself declares no type params).
        //
        // IMPORTANT: strayMap is LOCAL to this call — it must NOT be `subst`
        // because subst is global and would persist across call-sites, causing
        // the second call to `map` (with a different T) to reuse the first
        // call's binding and trigger a spurious type mismatch.
        val strayMap = mutableMapOf<String, LType>()
        fun deepSubst(t: LType): LType {
            // Apply class-level type args first (e.g. A→Int for Pair<Int,String>),
            // then the function's own type params.
            val afterMapping = substituteTypeParams(substituteTypeParams(t, classTypeMapping), typeParamMap)
            // If the result is still a bare named type that looks like a type param
            // (single uppercase letter or PascalCase with no typeArgs and not a known class),
            // and it wasn't covered by typeParamMap, allocate a fresh var for it so it
            // doesn't collide with an old binding in `subst`.
            return if (afterMapping.typeArgs.isEmpty()
                && afterMapping.name !in numericWidth
                && afterMapping != LType.BOOLEAN
                && afterMapping != LType.STRING
                && afterMapping != LType.UNIT
                && afterMapping != LType.ANY
                && symTable.root.classes[afterMapping.name] == null
                && afterMapping.name !in typeParamNames
                && !afterMapping.name.startsWith("τ")
            ) {
                // Stray type-param: give it a fresh var scoped to this call only.
                apply(strayMap.getOrPut(afterMapping.name) { fresh() })
            } else {
                afterMapping.copy(typeArgs = afterMapping.typeArgs.map { deepSubst(it) })
            }
        }

        val paramTypes = func.params.map { deepSubst(it.type) }
        val retType    = deepSubst(func.returnType)

        paramTypes.zip(argTypes).forEach { (p, a) -> unify(p, a) }

        return apply(retType)
    }

    /**
     * Infer a lambda expression.
     * For each parameter without a declared type, allocate a fresh type var.
     * Infer the body, then build a function type from resolved param types and body type.
     *
     * 推断 lambda 表达式。
     * 对每个没有声明类型的参数分配新鲜类型变量。
     * 推断函数体，然后用解析后的参数类型和函数体类型构造函数类型。
     */
    private fun inferLambda(expr: LHLambda): LType {
        val savedEnv       = env.toMutableMap()
        val savedReturnVar = returnTypeVar
        returnTypeVar      = fresh()  // fresh var for the lambda's own return type / 为 lambda 的返回类型分配新鲜变量

        val paramTypes = expr.params.map { param ->
            val t = if (isInfer(param.type)) fresh() else param.type
            env[param.name] = t
            t
        }

        // The body type is the type of the last statement.
        // If there are explicit returns, [returnTypeVar] has been unified with them.
        // 函数体类型是最后一条语句的类型。
        // 若存在显式 return，[returnTypeVar] 已与之合一。
        val bodyType = inferBlock(expr.body)

        // If returnTypeVar is still unbound (no explicit return), unify with body type.
        // 若 returnTypeVar 仍未绑定（无显式 return），则与函数体类型合一。
        val resolvedReturn =
            if (apply(returnTypeVar) == returnTypeVar) bodyType
            else apply(returnTypeVar)
        unify(returnTypeVar, resolvedReturn)

        env.clear()
        env.putAll(savedEnv)
        returnTypeVar = savedReturnVar

        val resolvedParams = paramTypes.map { apply(it) }
        return LType("->", resolvedParams + apply(resolvedReturn))
    }

    /**
     * Look up the declared type of [fieldName] on a resolved [recvType].
     * Returns null if the class or field is not found.
     *
     * 在已解析的 [recvType] 上查找 [fieldName] 的声明类型。
     * 若类或字段未找到则返回 null。
     */
    private fun lookupFieldType(recvType: LType, fieldName: String): LType? {
        val cls = symTable.root.classes[recvType.name] ?: return null
        val rawType = cls.fields[fieldName]?.type ?: return null
        if (cls.typeParams.isEmpty() || recvType.typeArgs.size != cls.typeParams.size) return rawType
        val mapping = cls.typeParams.zip(recvType.typeArgs).toMap()
        return substituteTypeParams(rawType, mapping)
    }

    /**
     * Look up the [LHFunction] for [methodName] on a resolved [recvType].
     * Returns null if the class or method is not found.
     *
     * 在已解析的 [recvType] 上查找 [methodName] 对应的 [LHFunction]。
     * 若类或方法未找到则返回 null。
     */
    private fun lookupMethod(recvType: LType, methodName: String): LHFunction? =
        symTable.root.classes[recvType.name]?.methods?.get(methodName)

    /**
     * Replace named type parameters (e.g. "T", "R") in [t] with their [mapping] entries.
     * Used when instantiating a generic function at a call site.
     * Leaf types not present in [mapping] are returned unchanged.
     *
     * 将 [t] 中的命名类型参数（如 "T"、"R"）替换为 [mapping] 中对应的类型。
     * 用于在调用位置实例化泛型函数。
     * 不在 [mapping] 中的叶节点类型原样返回。
     */
    private fun substituteTypeParams(t: LType, mapping: Map<String, LType>): LType {
        if (t.typeArgs.isEmpty()) return mapping[t.name] ?: t
        return t.copy(typeArgs = t.typeArgs.map { substituteTypeParams(it, mapping) })
    }

    /**
     * Return the return type for known builtin functions, or null if [name] is not a builtin.
     * These functions exist at runtime (provided by the Walker) but have no HIR definition.
     *
     * 返回已知内置函数的返回类型；若 [name] 不是内置函数则返回 null。
     * 这些函数在运行时由 Walker 提供，在 HIR 中没有定义。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun builtinMethodReturnType(recv: LType, name: String, @Suppress("UNUSED_PARAMETER") args: List<LType>): LType? = when {
        name == "toString"                     -> LType.STRING
        recv == LType.STRING && name == "plus" -> LType.STRING
        else                                   -> null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun builtinReturnType(name: String, args: List<LType>): LType? = when (name) {
        "println", "print"  -> LType.UNIT
        "toString"          -> LType.STRING
        "toInt"             -> LType.INT
        "toDecimal"         -> LType.DOUBLE
        "readLine"          -> LType.STRING
        else                -> null
    }

    companion object {
        /**
         * Method names produced by `Operator.toMethodName()` that follow numeric promotion rules.
         * Derived directly from Operator.kt — update here if new operators are added.
         * 由 `Operator.toMethodName()` 产生的、遵循数值提升规则的方法名。
         * 直接来自 Operator.kt，新增运算符时同步更新。
         */
        private val ARITHMETIC_OPS = setOf(
            "plus", "minus", "times", "div", "rem",   // Add Sub Mul Div Mod
            "shl", "shr", "ushr",                     // Shl Shr UShr
            "and", "or", "xor",                       // BitAnd BitOr BitXor (and And Or share names)
            "inv",                                     // BitNot (~)
            "inc", "dec"                               // Increment Decrement
        )

        /**
         * Method names that always return [LType.BOOLEAN] regardless of receiver type.
         * `not` is unary (Not operator), lt/gt/le/ge are comparisons — all from `Operator.toMethodName()`.
         * 始终返回 [LType.BOOLEAN] 的方法名（来自 Operator.toMethodName()）。
         * not 是一元运算符，lt/gt/le/ge 是比较运算符。
         */
        private val COMPARISON_OPS = setOf(
            "equals",          // Eq  (Ne is desugared to !(a.equals(b)) in HirGenerator)
            "not",             // Not (unary !)
            "lt", "gt", "le", "ge"  // Lt Gt Le Ge
        )
    }
}