package yukifuri.lang.lingspled.compiler.type

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.util.ClassKind
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.util.LTypeParamRef
import yukifuri.lang.lingspled.compiler.ir.fst.*
import yukifuri.lang.lingspled.compiler.symbol.BoundVariableSymbol
import yukifuri.lang.lingspled.compiler.symbol.ClassSymbol
import yukifuri.lang.lingspled.compiler.symbol.EnumEntrySymbol
import yukifuri.lang.lingspled.compiler.symbol.FunctionSymbol
import yukifuri.lang.lingspled.compiler.symbol.LocalVariableSymbol
import yukifuri.lang.lingspled.compiler.symbol.ParameterSymbol
import yukifuri.lang.lingspled.compiler.symbol.PropertySymbol
import yukifuri.lang.lingspled.compiler.symbol.Symbol
import yukifuri.lang.lingspled.compiler.symbol.SymbolProvider
import yukifuri.lang.lingspled.compiler.symbol.TypeParameterSymbol
import yukifuri.lang.lingspled.compiler.type.LType
import yukifuri.lang.lingspled.compiler.util.LTypeReference
import yukifuri.lang.lingspled.compiler.util.Operator

class TypeInferencePass(
    private val provider: SymbolProvider,
    private val diag: Diagnostics,
) {

    private val inferredLocals = mutableMapOf<LocalVariableSymbol, LType>()
    private val inferredProps = mutableMapOf<PropertySymbol, LType>()
    private val propsInProgress = mutableSetOf<PropertySymbol>()
    private val boundScopes = ArrayDeque<Map<String, BoundVariableSymbol>>()
    private val boundVarTypes = mutableMapOf<BoundVariableSymbol, LType>()
    private val inferredParams = mutableMapOf<ParameterSymbol, LType>()
    private val classStack = ArrayDeque<ClassSymbol>()
    private val thisTypeStack = ArrayDeque<LType>()

    /** Smart cast 上下文栈：每个分支 push 该分支成立的缩窄映射。 */
    private val smartCastStack = ArrayDeque<Map<Symbol, LType>>()
    private val smartCasts: Map<Symbol, LType> get() = smartCastStack.lastOrNull() ?: emptyMap()

    /** 隐式 it 参数栈：无显式参数的 lambda 在期望函数类型为单参时注入 `it`。 */
    private val implicitItStack = ArrayDeque<ParameterSymbol>()

    private fun pushSmartCasts(casts: Map<Symbol, LType>) {
        val merged = (smartCastStack.lastOrNull() ?: emptyMap()) + casts
        smartCastStack.addLast(merged)
    }

    private fun popSmartCasts() {
        if (smartCastStack.isNotEmpty()) smartCastStack.removeLast()
    }

    private fun lookupBoundVar(name: String): BoundVariableSymbol? {
        for (i in boundScopes.indices.reversed()) boundScopes[i][name]?.let { return it }
        return null
    }

    fun infer(module: LFModule) {
        for (stmt in module.statements) when (stmt) {
            is LFClass -> inferClass(stmt)
            is LFFunction -> inferFunction(stmt)
            is LFVariableDecl -> inferProperty(stmt)
            else -> {}
        }
    }

    private fun inferClass(cls: LFClass) {
        if (cls.kind == ClassKind.Annotation) return // 注解类不参与类型推断
        cls.symbol?.let { classStack.addLast(it) }
        cls.primaryCtor?.params?.forEach { p -> p.default?.let { inferExpr(it, p.type.lower()) } }
        for (a in cls.attr) inferProperty(a)
        for (f in cls.functions) inferFunction(f)
        for (c in cls.ctors) inferFunction(c)
        for (i in cls.inits) inferFunction(i)
        cls.deinit?.let { inferFunction(it) }
        for (n in cls.nested) inferClass(n)
        // TODO: Anonymous Object (Should be flatten to top-level like Anonymous_xxx)
        for (entry in cls.entries) for (a in entry.args) inferExpr(a.value, null)
        cls.symbol?.let { classStack.removeLast() }
    }

    private val returnTypeInProgress = mutableSetOf<LFFunction>()

    private fun inferFunction(fn: LFFunction) {
        if (fn is LFClassConstructor) when (val d = fn.delegation) {
            is LFConstructorDelegation.This -> for (a in d.args) inferExpr(a.value, null)
            is LFConstructorDelegation.Super -> for (a in d.args) inferExpr(a.value, null)
            null -> {}
        }
        val ext = fn.receiver?.lower()?.also { thisTypeStack.addLast(it) } // External Function: this == receiver
        for (p in fn.params) p.default?.let { inferExpr(it, p.type.lower()) }
        fn.body?.let { inferModule(it) }
        ensureReturnTypeInferred(fn)
        if (ext != null) thisTypeStack.removeLast()
    }

    private fun ensureReturnTypeInferred(fn: LFFunction) {
        if (fn.ret !== LTypeRef.infer) return
        if (!returnTypeInProgress.add(fn)) {
            fn.ret = LTypeRef("/cycle")
            return
        }
        try {
            fn.body?.let { inferModule(it) }
            fn.ret = inferReturnType(fn)
        } finally {
            returnTypeInProgress.remove(fn)
        }
    }

    private fun inferReturnType(fn: LFFunction): LTypeRef {
        val body = fn.body ?: return LTypeRef.unit
        val last = body.statements.lastOrNull() ?: return LTypeRef.unit
        val t = when (last) {
            is LFExprStatement -> last.expr.inferredType ?: LType.Unit
            is LFFunction.LFReturnStmt -> last.expr.inferredType ?: LType.Unit
            else -> LType.Unit
        }
        println("[inferReturnType] ${fn.name} last=$last t=$t")
        return typeToLTypeRef(t)
    }

    private fun typeToLTypeRef(t: LType): LTypeRef = when (t) {
        LType.Unit -> LTypeRef.unit
        LType.Int -> LTypeRef.primInt
        LType.Long -> LTypeRef.primLong
        LType.Float -> LTypeRef.primFloat
        LType.Double -> LTypeRef.primDouble
        LType.Boolean -> LTypeRef.primBoolean
        is LType.Primitive -> LTypeRef(t.kind.name)
        is LType.Class -> LTypeRef(t.symbol.fqn, t.args.map { typeToLTypeRef(it) }, t.nullable)
            .also { it.symbol = t.symbol }
        is LType.TypeParam -> LTypeRef(t.symbol.name, nullable = t.nullable)
            .also { it.symbol = t.symbol }
        is LType.Function -> LTypeRef("Function",
            (t.params.map { typeToLTypeRef(it) } + typeToLTypeRef(t.ret)).map { it as LTypeReference },
            t.nullable)
        is LType.Builtin -> when (t.name) {
            "Any" -> LTypeRef.any
            else -> LTypeRef(t.name, nullable = t.nullable)
        }
        is LType.Nothing -> LTypeRef("Nothing", nullable = t.nullable)
        is LType.Error -> LTypeRef("/error", nullable = t.nullable)
    }

    private fun inferProperty(v: LFVariableDecl) {
        val declared = v.type?.lower()
        val sym = v.symbol as? PropertySymbol
        when {
            // 显式类型: propertyType 直接给类型，但 init 子节点仍要推（填 inferredType，供 dump/校验）
            declared != null -> v.init?.let { inferExpr(it, declared) }
            // 无类型: 走 lazy 路径推 init（顺带记忆化）。此处是「在正确的 enclosing 上下文里预热缓存」
            sym != null -> propertyType(sym)
            else -> v.init?.let { inferExpr(it, null) }
        }
        v.delegator?.let { inferExpr(it, null) }
        if (v is LFClassAttribute) {
            v.getter?.let { inferFunction(it) }
            v.setter?.let { inferFunction(it) }
        }
    }

    private fun propertyType(symbol: PropertySymbol): LType {
        symbol.declaration.type?.lower()?.let { return it }
        inferredProps[symbol]?.let { return it }
        val init = symbol.declaration.init
        if (init == null && symbol.declaration.type == null) return LType.Error.untypeUninitProp
        if (!propsInProgress.add(symbol)) return LType.Error.propCycle
        val t = if (init != null) {
            try {
                inferExpr(init, null)
            } finally {
                propsInProgress.remove(symbol)
            }
        } else symbol.declaration.type!!.lower()
        inferredProps[symbol] = t
        return t
    }

    private fun inferModule(module: LFModule) {
        for (stmt in module.statements) inferStmt(stmt)
    }

    private fun inferStmt(stmt: LFStatement) {
        when (stmt) {
            is LFModule -> inferModule(stmt)
            is LFExprStatement -> inferExpr(stmt.expr, null)
            is LFAssign -> {
                val t = inferExpr(stmt.target, null)
                inferExpr(stmt.value, t)
            }
            is LFWhile -> {
                inferExpr(stmt.condition, LType.Boolean)
                pushSmartCasts(extractSmartCasts(stmt.condition, positive = true))
                inferModule(stmt.body)
                popSmartCasts()
            }
            is LFDoWhile -> { inferModule(stmt.body); inferExpr(stmt.condition, LType.Boolean) }
            is LFFor -> {
                val iterT = inferExpr(stmt.iterable, null)
                // 循环变量类型: 显式 `for (x: T in ...)` 优先，否则按迭代器协议从 iterable 取元素类型
                val elemT = stmt.type?.lower() ?: elementTypeOf(iterT)
                val sym = BoundVariableSymbol(stmt.variable)
                boundVarTypes[sym] = elemT
                boundScopes.addLast(mapOf(stmt.variable to sym))
                inferModule(stmt.body)
                boundScopes.removeLast()
            }
            is LFFunction.LFReturnStmt -> inferExpr(stmt.expr, null)
            is LFVariableDecl -> inferLocalVar(stmt)
            is LFFunction -> inferFunction(stmt)  // 局部函数
            is LFClass -> inferClass(stmt)        // 局部类
            is LFBreak, is LFContinue -> {}
            is LFFile.LFPackageDeclaration, is LFFile.LFImportDeclaration -> {}
            else -> inferExpr(stmt, null) // LFInvokeExpr 等表达式语句
        }
    }

    private fun inferLocalVar(v: LFVariableDecl) {
        val declared = v.type?.lower()
        val initType = v.init?.let { inferExpr(it, declared) }
        println("[inferLocalVar] ${v.name} initType=$initType")
        v.delegator?.let { inferExpr(it, null) }
        if (declared == null && initType != null) (v.symbol as? LocalVariableSymbol)?.let {
            inferredLocals[it] = initType
        }
    }

    private fun inferExpr(expr: LFExpression, expected: LType?): LType {
        val t = when (expr) {
            is LFLiteral<*> -> inferLiteral(expr, expected)
            is LFFieldAccessExpr -> inferFieldAccess(expr, expected)
            is LFIndexAccessExpr -> inferIndexAccess(expr)
            is LFInvokeExpr -> inferInvoke(expr, expected)
            is LFUnaryExpr -> inferExpr(expr.expr, expected)
                .let { if (expr.op == Operator.Not) LType.Boolean else it } // -x/+x 同操作数，!x → Boolean
            is LFIncDec -> inferExpr(expr.target, expected)
            is LFBinaryExpr -> inferBinary(expr)
            is LFIf -> inferIf(expr, expected)
            is LFTry -> inferTry(expr, expected)
            is LFThrow -> { inferExpr(expr.expr, null); LType.Nothing() }
            is LFLambda -> inferLambda(expr, expected)
            else -> LType.Error.UnHandled(expr::class.simpleName)
        }
        expr.inferredType = t
        return t
    }

    private fun inferLiteral(expr: LFLiteral<*>, expected: LType?): LType = when (expr) {
        is LFLiteral.LFLInteger -> LType.Int
        is LFLiteral.LFLLong -> LType.Long
        is LFLiteral.LFLFloat -> LType.Float
        is LFLiteral.LFLDouble -> LType.Double
        is LFLiteral.LFLBoolean -> LType.Boolean
        is LFLiteral.LFLString -> stdClass("ling.std.String") ?: LType.Error.primitiveNotFound
        is LFLiteral.LFLNull -> if (expected != null && expected.nullable) expected else LType.NullLit
        is LFLiteral.LFThis -> thisTypeStack.lastOrNull()
            ?: classStack.lastOrNull()?.let { LType.Class(it) } ?: LType.Error.noThisContext
    }

    private fun inferFieldAccess(expr: LFFieldAccessExpr, expected: LType?): LType {
        if (expr.receiver == null) {
            // 内层 for/catch 绑定变量优先于 Resolution 绑的外层名
            lookupBoundVar(expr.field)?.let { bv ->
                expr.symbol = bv
                return boundVarTypes[bv] ?: LType.Error.boundVar
            }
            // 隐式 it：lambda 省略参数且期望类型为单参函数
            if (expr.symbol == null && expr.field == "it" && implicitItStack.isNotEmpty()) {
                val itSym = implicitItStack.last()
                expr.symbol = itSym
                return inferredParams[itSym] ?: LType.Error.infer
            }
            val base = typeOfSymbolRef(expr.symbol)
            // Smart cast：当前分支内该符号被缩窄到更具体/非空类型
            return smartCasts[expr.symbol]?.let { cast ->
                // 保留原类型不可被 smart cast 扩大的可空性（cast 可进一步缩窄为空/非空）
                if (cast.nullable == base.nullable) cast else cast.withNullability(base.nullable && cast.nullable)
            } ?: base
        }
        val rt = inferExpr(expr.receiver, null)
        // 限定名 a.b（a 是类/包）: Resolution 已绑 expr.symbol，直接按符号算类型。
        expr.symbol?.let { return typeOfSymbolRef(it) }
        // 成员访问（a 是值）: 按 receiver 的类类型查成员，补绑 expr.symbol，作泛型实参替换。
        // 裸 `T` 受体沿上界、Primitive/Builtin 落 stdlib 包装类，统一经 receiverClass。
        receiverClass(rt)?.let { recv ->
            val member = recv.symbol.resolveMember(expr.field).firstOrNull()
            expr.symbol = member
            return substituteTypeArgs(typeOfSymbolRef(member), recv)
        }
        return LType.Error.memberAccess
    }

    private fun typeOfSymbolRef(symbol: Symbol?): LType = when (symbol) {
        is ParameterSymbol -> (inferredParams[symbol] ?: symbol.declaration.type.lower())
            .let { if (symbol.declaration.vararg) varargArrayType(it) else it } // vararg 体内是 Array<E>

        is LocalVariableSymbol -> inferredLocals[symbol] ?: symbol.declaration.type?.lower() ?: LType.Error.localUntyped
        is BoundVariableSymbol -> boundVarTypes[symbol] ?: LType.Error.boundVar
        is PropertySymbol -> propertyType(symbol) // lazy: 未算则现场推 init，见 propertyType
        is ClassSymbol -> LType.Class(symbol) // 类引用（如 `Foo` 作被调者）→ 该类类型
        is EnumEntrySymbol -> LType.Class(symbol.enclosing) // `Color.RED` → 该 enum 类型
        is FunctionSymbol -> functionType(symbol) // 函数引用（`a.b` 取方法作值）→ Function 类型
        else -> LType.Error.unbound
    }

    private fun functionType(fn: FunctionSymbol): LType {
        ensureReturnTypeInferred(fn.declaration)
        return LType.Function(fn.declaration.params.map { it.type.lower() }, fn.declaration.ret.lower())
    }

    private fun receiverClass(t: LType): LType.Class? = when (t) {
        is LType.Class -> t
        is LType.Primitive -> stdClass("ling.std.${t.kind}")
        is LType.Builtin -> stdClass("ling.std.${t.name}")
        is LType.TypeParam -> t.boundClass()
        else -> null
    }

    private fun stdClass(fqn: String): LType.Class? = provider.findClass(fqn)?.let { LType.Class(it) }

    /** 反查符号在 [provider] 中注册的全限定名（仅顶层符号）。 */
    private fun fqnOf(symbol: Symbol): String? {
        for ((fqn, syms) in provider.symbols) {
            if (symbol in syms) return fqn
        }
        return null
    }

    private fun inferIndexAccess(expr: LFIndexAccessExpr): LType {
        val rt = inferExpr(expr.receiver, null)
        inferExpr(expr.index, null)
        val recv = receiverClass(rt) ?: return LType.Error.unbound
        val getters = recv.symbol.resolveMember("get").filterIsInstance<FunctionSymbol>()
        val chosen = selectOverload(getters, listOf(LFArgument(null, expr.index))) ?: return LType.Error.NoOverloads("operator get")
        ensureReturnTypeInferred(chosen.declaration)
        return substituteTypeArgs(chosen.declaration.ret.lower(), recv)
    }

    private fun inferInvoke(expr: LFInvokeExpr, expected: LType?): LType {
        // 先推断各实参（类型回填到 LFExpression.inferredType），保留命名信息供重载决议使用
        for (a in expr.arg) inferExpr(a.value, null)
        val callee = expr.receiver
        if (callee !is LFFieldAccessExpr) {
            // f(x)(y) 之类: 被调者非字段访问，按其类型粗判
            return when (val ct = inferExpr(callee, null)) {
                is LType.Function -> ct.ret
                is LType.Class -> ct
                else -> LType.Error.UnHandled("Invoke")
            }
        }
        return inferCall(callee, expr.arg)
    }

    private fun inferCall(callee: LFFieldAccessExpr, arguments: List<LFArgument>): LType {
        var candidates: List<FunctionSymbol> = emptyList()
        var owner: LType.Class? = null        // 成员调用的 receiver 类型（泛型替换用）
        var recvType: LType? = null           // 值 receiver 类型（扩展函数泛型替换用）
        var ctorClass: ClassSymbol? = null

        if (callee.receiver == null) {
            val sym = lookupBoundVar(callee.field)?.also { callee.symbol = it } ?: callee.symbol
            when (sym) {
                is FunctionSymbol -> {
                    // 顶层函数重载：同一 FQN 下可能注册了多个 FunctionSymbol，全部纳入候选
                    val fqn = fqnOf(sym)
                    candidates = if (fqn != null) provider.findFunction(fqn) else listOf(sym)
                }
                is ClassSymbol -> ctorClass = sym // 构造调用 Foo()
                else -> {}
            }
        } else {
            val recv = inferExpr(callee.receiver, null)
            when (val s = callee.symbol) {              // 限定名 a.b（a 为类/包），Resolution 已绑
                is FunctionSymbol -> candidates = listOf(s)
                is ClassSymbol -> ctorClass = s
                else -> {                               // 成员调用（a 是值，含裸 T / Primitive / Builtin 受体）
                    recvType = recv
                    receiverClass(recv)?.let { ro ->
                        owner = ro
                        val members = ro.symbol.resolveMember(callee.field)
                        candidates = members.filterIsInstance<FunctionSymbol>()
                        ctorClass = members.firstNotNullOfOrNull { it as? ClassSymbol }
                    }
                    // 成员之外再并入扩展函数候选（成员排前，selectOverload 取首个应用者 → 成员遮蔽扩展）。
                    candidates = candidates + extensionCandidates(recv, callee.field)
                }
            }
        }

        ctorClass?.let {
            callee.symbol = it
            callee.inferredType = LType.Class(it)
            return LType.Class(it)
        }
        if (candidates.isNotEmpty()) {
            val chosen = selectOverload(candidates, arguments)
            chosen?.let { callee.symbol = it }
            callee.inferredType = chosen?.let(::functionType) ?: LType.Error.UnHandled("callee")
            val ret = chosen?.let {
                ensureReturnTypeInferred(it.declaration)
                it.declaration.ret.lower()
            } ?: LType.Error.NoOverloads("operator invoke")
            return when {
                chosen == null -> ret
                // 扩展函数: 返回类型里的 receiver 类型参数按调用 receiver 实参替换（`Array<T>.first(): T` → Int）
                chosen.declaration.receiver != null ->
                    substitute(ret, extReceiverSubst(chosen.declaration.receiver.lower(), recvType))
                else -> owner?.let { substituteTypeArgs(ret, it) } ?: ret
            }
        }
        // 兜底: 被调者是函数型的值（参数/局部/绑定变量）
        val ct = typeOfSymbolRef(callee.symbol).also { callee.inferredType = it }
        return when (ct) {
            is LType.Function -> ct.ret
            is LType.Class -> ct
            else -> LType.Error.UnHandled("Invoke")
        }
    }

    private fun selectOverload(candidates: List<FunctionSymbol>, arguments: List<LFArgument>): FunctionSymbol? {
        if (candidates.size == 1) return candidates.first()
        val byArity = candidates.filter { matchesArity(it, arguments) }
        if (byArity.isEmpty()) return candidates.firstOrNull()
        val applicable = byArity.filter { applicable(it, arguments) }
        if (applicable.size <= 1) return applicable.firstOrNull() ?: byArity.first()
        // most-specific：返回严格比其他所有 applicable 候选都更具体的那个
        for (a in applicable) {
            if (applicable.all { b -> a === b || isMoreSpecific(a, b) }) return a
        }
        return applicable.first()
    }

    private fun isVararg(fn: FunctionSymbol) = fn.declaration.params.lastOrNull()?.vararg == true

    /** 粗略 arity 检查：只排除明显不可能的情况，详细检查交给 [applicable]。 */
    private fun matchesArity(fn: FunctionSymbol, arguments: List<LFArgument>): Boolean {
        val params = fn.declaration.params
        val named = arguments.mapNotNull { it.name }
        // 每个命名实参必须对应一个形参
        if (named.any { n -> params.none { it.name == n } }) return false
        val positional = arguments.takeWhile { it.name == null }.size
        val mandatory = params.count { it.default == null && !it.vararg }
        return if (isVararg(fn)) positional >= mandatory else positional in mandatory..params.count { !it.vararg }
    }

    private fun applicable(fn: FunctionSymbol, arguments: List<LFArgument>): Boolean {
        val params = fn.declaration.params
        val argTypes = arguments.map { it.value.inferredType ?: LType.Error.infer }

        if (isVararg(fn)) {
            val fixed = params.size - 1
            if (arguments.size < fixed) return false
            val used = MutableList(arguments.size) { false }
            // 位置实参：前 fixed 个匹配前 fixed 个非 vararg 形参
            for (i in 0 until minOf(fixed, arguments.size)) {
                if (arguments[i].name != null) continue
                used[i] = true
                if (!argTypes[i].isAssignableTo(params[i].type.lower())) return false
            }
            // vararg 元素类型
            val elem = params.last().type.lower()
            // 剩余位置实参匹配 vararg
            for (i in fixed until arguments.size) {
                if (arguments[i].name != null) continue
                used[i] = true
                if (!argTypes[i].isAssignableTo(elem)) return false
            }
            // 命名实参
            for ((i, arg) in arguments.withIndex()) {
                val name = arg.name ?: continue
                if (used[i]) return false
                val idx = params.indexOfFirst { it.name == name }
                if (idx < 0) return false
                val pType = if (idx == params.size - 1) elem else params[idx].type.lower()
                if (!(arg.value.inferredType ?: LType.Error.infer).isAssignableTo(pType)) return false
            }
            // 未填的非 vararg 形参必须有默认值
            for (i in 0 until fixed) {
                if (arguments.withIndex().any { it.index < arguments.size && it.value.name == params[i].name }) continue
                if (i < arguments.size && arguments[i].name == null) continue
                if (params[i].default == null) return false
            }
            return true
        }

        // 非 vararg：先位置后命名
        val positionalCount = arguments.indexOfFirst { it.name != null }.let { if (it < 0) arguments.size else it }
        val used = MutableList(params.size) { false }
        for (i in 0 until positionalCount) {
            if (i >= params.size) return false
            used[i] = true
            if (!argTypes[i].isAssignableTo(params[i].type.lower())) return false
        }
        for (i in positionalCount until arguments.size) {
            val name = arguments[i].name ?: return false
            val idx = params.indexOfFirst { it.name == name }
            if (idx < 0 || used[idx]) return false
            used[idx] = true
            if (!argTypes[i].isAssignableTo(params[idx].type.lower())) return false
        }
        // 未填形参必须有默认值
        for (i in params.indices) {
            if (!used[i] && params[i].default == null) return false
        }
        return true
    }

    /** a 是否比 b 更具体：对应位置参数类型都是 b 参数类型的子类型。 */
    private fun isMoreSpecific(a: FunctionSymbol, b: FunctionSymbol): Boolean {
        val aParams = a.declaration.params
        val bParams = b.declaration.params
        if (aParams.size != bParams.size) return false
        for (i in aParams.indices) {
            if (aParams[i].vararg != bParams[i].vararg) return false
            if (!aParams[i].type.lower().isAssignableTo(bParams[i].type.lower())) return false
        }
        return true
    }

    private fun varargArrayType(elem: LType): LType =
        provider.findClass("ling.std.Array")?.let { LType.Class(it, listOf(elem)) } ?: elem

    private fun substituteTypeArgs(type: LType, owner: LType.Class): LType =
        substitute(type, inheritanceSubst(owner))

    private fun inheritanceSubst(owner: LType.Class): Map<TypeParameterSymbol, LType> {
        val acc = mutableMapOf<TypeParameterSymbol, LType>()
        fun walk(cls: ClassSymbol, args: List<LType>, seen: MutableSet<ClassSymbol>) {
            if (!seen.add(cls)) return
            cls.declaration.tp.zip(args).forEach { (p, a) ->
                p.symbol?.let { acc.putIfAbsent(it, a) }
            }
            for (superRef in cls.declaration.interfaces) {
                val superSym = superRef.symbol as? ClassSymbol ?: continue
                walk(superSym, superRef.tp.map { substitute(it.lower(), acc) }, seen)
            }
        }
        walk(owner.symbol, owner.args, mutableSetOf())
        return acc
    }

    private fun substitute(type: LType, map: Map<TypeParameterSymbol, LType>): LType = when (type) {
        is LType.TypeParam -> map[type.symbol]?.withNullability(type.nullable) ?: type
        is LType.Class -> type.copy(args = type.args.map { substitute(it, map) })
        else -> type
    }

    private fun extensionCandidates(recv: LType, name: String): List<FunctionSymbol> =
        provider.findExtensions(name).filter { fn ->
            fn.declaration.receiver?.let { receiverMatches(recv, it.lower()) } ?: false
        }

    private fun receiverMatches(callRecv: LType, extRecv: LType): Boolean {
        val a = receiverClass(callRecv)?.symbol ?: return false
        val b = receiverClass(extRecv)?.symbol ?: return false
        return LType.Class(a).isAssignableTo(LType.Class(b))
    }

    private fun extReceiverSubst(extRecv: LType, callRecv: LType?): Map<TypeParameterSymbol, LType> {
        if (extRecv is LType.Class && callRecv is LType.Class && extRecv.symbol == callRecv.symbol)
            return extRecv.args.zip(callRecv.args).mapNotNull { (p, a) ->
                (p as? LType.TypeParam)?.let { it.symbol to a }
            }.toMap()
        return emptyMap()
    }

    private fun infixCall(left: LType, name: String, rightExpr: LFExpression): LType {
        val recv = receiverClass(left)
        val members = recv?.symbol?.resolveMember(name)?.filterIsInstance<FunctionSymbol>().orEmpty()
        val chosen = selectOverload(members + extensionCandidates(left, name), listOf(LFArgument(null, rightExpr)))
            ?: return LType.Error.errInfix
        ensureReturnTypeInferred(chosen.declaration)
        val ret = chosen.declaration.ret.lower()
        return if (chosen.declaration.receiver != null)
            substitute(ret, extReceiverSubst(chosen.declaration.receiver.lower(), left))
        else recv?.let { substituteTypeArgs(ret, it) } ?: ret
    }

    private fun elementTypeOf(iter: LType, seen: MutableSet<ClassSymbol> = mutableSetOf()): LType {
        val recv = receiverClass(iter) ?: return LType.Error.errFor
        if (!seen.add(recv.symbol)) return LType.Error.errFor
        recv.symbol.resolveMember("next").filterIsInstance<FunctionSymbol>().firstOrNull()?.let {
            ensureReturnTypeInferred(it.declaration)
            return substituteTypeArgs(it.declaration.ret.lower(), recv)
        }
        recv.symbol.resolveMember("iterator").filterIsInstance<FunctionSymbol>().firstOrNull()?.let {
            ensureReturnTypeInferred(it.declaration)
            return elementTypeOf(substituteTypeArgs(it.declaration.ret.lower(), recv), seen)
        }
        return LType.Error.errFor
    }

    private fun inferBinary(expr: LFBinaryExpr): LType {
        val left = inferExpr(expr.left, null)
        val right = inferExpr(expr.right, null)
        return when (expr.op.op) {
            Operator.Eq, Operator.Ne,
            Operator.Lt, Operator.Gt, Operator.Le, Operator.Ge,
            Operator.And, Operator.Or,
            Operator.Is, Operator.IsNot, Operator.In, Operator.NotIn -> LType.Boolean
            Operator.Plus -> operatorResult(left, expr.right, "plus") ?: numericPromotion(left, right) ?: left
            Operator.Minus -> operatorResult(left, expr.right, "minus") ?: numericPromotion(left, right) ?: left
            Operator.Mul -> operatorResult(left, expr.right, "times") ?: numericPromotion(left, right) ?: left
            Operator.Div -> operatorResult(left, expr.right, "div") ?: numericPromotion(left, right) ?: left
            Operator.Rem -> operatorResult(left, expr.right, "rem") ?: numericPromotion(left, right) ?: left
            Operator.RangeTo -> operatorResult(left, expr.right, "rangeTo") ?: numericPromotion(left, right) ?: LType.Error.errBinOp
            Operator.RangeUntil -> operatorResult(left, expr.right, "rangeUntil") ?: numericPromotion(left, right) ?: LType.Error.errBinOp
            Operator.Elvis -> left.withNullability(false) // a ?: b → a 去空
            Operator.As -> typeOfTypeExpr(expr.right, right)
            Operator.SafeAs -> typeOfTypeExpr(expr.right, right).withNullability(true)
            Operator.Infix -> expr.op.infix?.let { infixCall(left, it, expr.right) } ?: LType.Error.errInfix
            else -> LType.Error.errBinOp
        }
    }

    private fun operatorResult(left: LType, rightExpr: LFExpression, name: String): LType? {
        val recv = receiverClass(left) ?: return null
        val fn = selectOverload(recv.symbol.resolveMember(name).filterIsInstance<FunctionSymbol>(), listOf(LFArgument(null, rightExpr)))
            ?: return null
        println("[operatorResult] left=$left name=$name fn=$fn fn.ret(before)=${fn.declaration.ret}")
        ensureReturnTypeInferred(fn.declaration)
        val result = substituteTypeArgs(fn.declaration.ret.lower(), recv)
        println("[operatorResult] fn.ret(after)=${fn.declaration.ret} result=$result")
        return result
    }

    /** 原始数值类型的算术提升：Byte < Short < Int < Long < Float < Double。 */
    private fun numericPromotion(a: LType, b: LType): LType? {
        if (a !is LType.Primitive || b !is LType.Primitive) return null
        val order = listOf(
            LType.Primitive.Kind.Byte,
            LType.Primitive.Kind.Short,
            LType.Primitive.Kind.Int,
            LType.Primitive.Kind.Long,
            LType.Primitive.Kind.Float,
            LType.Primitive.Kind.Double,
        )
        val ai = order.indexOf(a.kind)
        val bi = order.indexOf(b.kind)
        if (ai < 0 || bi < 0) return null
        return LType.Primitive(order[maxOf(ai, bi)])
    }

    private fun typeOfTypeExpr(expr: LFExpression, inferred: LType): LType {
        if (expr is LFFieldAccessExpr && expr.receiver == null)
            LType.lowerPrimitive(expr.field)?.let { return LType.Primitive(it) }
        return inferred
    }

    private fun inferIf(expr: LFIf, expected: LType?): LType {
        inferExpr(expr.condition, LType.Boolean)
        pushSmartCasts(extractSmartCasts(expr.condition, positive = true))
        val thenT = inferModuleValue(expr.then, expected)
        popSmartCasts()
        val elseT = expr.elseBranch?.let {
            pushSmartCasts(extractSmartCasts(expr.condition, positive = false))
            inferModuleValue(it, expected).also { popSmartCasts() }
        }
        return if (elseT == null) LType.Unit else lub(thenT, elseT)
    }

    /** 从条件表达式提取 smart cast。positive=true 表示该分支 condition 为真时成立的缩窄。 */
    private fun extractSmartCasts(expr: LFExpression, positive: Boolean): Map<Symbol, LType> = when (expr) {
        is LFBinaryExpr -> when (expr.op.op) {
            Operator.Is -> if (positive) castOf(expr.left, expr.right) else emptyMap()
            Operator.IsNot -> if (!positive) castOf(expr.left, expr.right) else emptyMap()
            Operator.Eq -> when {
                positive && isNull(expr.right) -> nullCastOf(expr.left, makeNullable = true)
                positive && isNull(expr.left) -> nullCastOf(expr.right, makeNullable = true)
                else -> emptyMap()
            }
            Operator.Ne -> when {
                positive && isNull(expr.right) -> nullCastOf(expr.left, makeNullable = false)
                positive && isNull(expr.left) -> nullCastOf(expr.right, makeNullable = false)
                else -> emptyMap()
            }
            Operator.And -> if (positive) {
                extractSmartCasts(expr.left, true) + extractSmartCasts(expr.right, true)
            } else emptyMap()
            Operator.Or -> if (!positive) {
                extractSmartCasts(expr.left, false) + extractSmartCasts(expr.right, false)
            } else emptyMap()
            else -> emptyMap()
        }
        is LFUnaryExpr -> if (expr.op == Operator.Not) extractSmartCasts(expr.expr, !positive) else emptyMap()
        else -> emptyMap()
    }

    private fun castOf(subject: LFExpression, typeExpr: LFExpression): Map<Symbol, LType> {
        val sym = (subject as? LFFieldAccessExpr)?.takeIf { it.receiver == null }?.symbol ?: return emptyMap()
        val t = typeExpr.inferredType ?: inferExpr(typeExpr, null)
        return mapOf(sym to t)
    }

    private fun nullCastOf(subject: LFExpression, makeNullable: Boolean): Map<Symbol, LType> {
        val sym = (subject as? LFFieldAccessExpr)?.takeIf { it.receiver == null }?.symbol ?: return emptyMap()
        val base = typeOfSymbolRef(sym)
        return mapOf(sym to base.withNullability(makeNullable))
    }

    private fun isNull(expr: LFExpression): Boolean =
        expr is LFLiteral<*> && expr is LFLiteral.LFLNull

    private fun inferTry(expr: LFTry, expected: LType?): LType {
        val bodyT = inferModuleValue(expr.body, expected)
        val catchTs = expr.catches.map { c ->
            val sym = BoundVariableSymbol(c.name)
            boundVarTypes[sym] = c.type.lower() // catch 变量类型语法上写明（`catch (e: T)`）
            boundScopes.addLast(mapOf(c.name to sym))
            inferModuleValue(c.body, expected).also { boundScopes.removeLast() }
        }
        expr.finallyBlock?.let { inferModule(it) }
        return catchTs.fold(bodyT) { acc, t -> lub(acc, t) }
    }

    private fun inferLambda(expr: LFLambda, expected: LType?): LType {
        val expectedFn = expected as? LType.Function
        // 隐式 it：无显式参数且期望为单参函数时注入 `it`
        val effectiveParams = if (expr.params.isEmpty() && expectedFn != null && expectedFn.params.size == 1) {
            val p = LFParameter("it", LTypeRef.infer)
            val sym = ParameterSymbol("it", p)
            p.symbol = sym
            inferredParams[sym] = expectedFn.params[0]
            implicitItStack.addLast(sym)
            listOf(p)
        } else expr.params

        val paramTypes = effectiveParams.mapIndexed { i, p ->
            val t = when {
                p.type !== LTypeRef.infer -> p.type.lower()
                expectedFn != null && i < expectedFn.params.size -> expectedFn.params[i]
                else -> {
                    diag.add("Error", "Cannot infer the type of lambda parameter '${p.name}'", expr.position, expr.position)
                    LType.Error.errInfix
                }
            }
            p.symbol?.let { inferredParams[it] = t }
            t
        }
        val bodyT = inferModuleValue(expr.body, expectedFn?.ret)
        if (effectiveParams !== expr.params) implicitItStack.removeLast()
        return LType.Function(paramTypes, bodyT)
    }

    private fun inferModuleValue(module: LFModule, expected: LType?): LType {
        if (module.statements.isEmpty()) return LType.Unit
        for (stmt in module.statements.dropLast(1)) inferStmt(stmt)
        val last = module.statements.last()
        return if (last is LFExprStatement) inferExpr(last.expr, expected)
        else inferStmt(last).let { last.inferredType ?: LType.Unit }
    }

    private fun lub(a: LType, b: LType): LType = when {
        a == b -> a
        a is LType.Error -> b
        b is LType.Error -> a
        a.isAssignableTo(b) -> b
        b.isAssignableTo(a) -> a
        else -> LType.Any
    }
}