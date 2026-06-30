package yukifuri.lang.lingspled.compiler.symbol

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.util.LTypeReference
import yukifuri.lang.lingspled.compiler.ir.fst.*
import yukifuri.lang.lingspled.compiler.util.LTypeParamNone
import yukifuri.lang.lingspled.compiler.util.LTypeParamRef

/**
 * Resolution 遍：SymbolCollection 之后的名字解析,把 **use → [Symbol]** 绑定填回 use 节点
 * （[LFFieldAccessExpr.symbol] / [LTypeRef.symbol]）。走 K2 的 phase 划分,但本遍只吃下不依赖类型的部分：
 *
 * - **SUPER_TYPES**（[linkSupertypes]）：解析每个类的超类名 → 填 [ClassSymbol.superclass]，接通继承链。
 * - **TYPES**（[resolve] 内）：解析声明签名里的类型引用（参数/返回/属性/上界…）→ 回填 [LTypeRef.symbol]。
 * - **BODY**（[resolve] 内）：函数体里 **不依赖类型** 的值引用（局部/参数/this 成员/顶层名）。
 *   成员访问 `expr.x`、调用重载决议 `f(x)` 依赖类型 → 本遍留空,交后续 TypeInference。
 *
 * scope-tower 临时构建（K2 路线,不存 node→scope 侧表）。多文件：先全 [linkSupertypes] 再全 [resolve]，
 * 故继承链、跨文件、前向引用在 BODY 解析时已全部可见。
 */
class ResolutionPass(
    private val provider: SymbolProvider,
    private val diag: Diagnostics,
) {

    /** 当前文件包前缀（形如 `a.b.` 或 `""`）。每进一个 module 由 [setupContext] 重置。 */
    private var packagePrefix = ""

    /** 当前文件的显式单类型 import：simpleName/alias → FQN。 */
    private val imports = mutableMapOf<String, String>()

    /** 当前文件的显式通配 import（`import a.b.*`）的包前缀，裸名按 `前缀.name` 回查。 */
    private val wildcardImports = mutableListOf<String>()

    /**
     * 默认隐式导入的包（裸名兜底回查，像 Kotlin 的 `kotlin.*`）。
     * **仅 `ling.std` 这一层**——不含 `ling.*` 兄弟包，也不含 `ling.std.x.*` 子包。
     */
    private val defaultImports = listOf("ling.std")

    private fun setupContext(module: LFModule) {
        packagePrefix = module.statements
            .filterIsInstance<LFFile.LFPackageDeclaration>()
            .firstOrNull()?.packageFqn.orEmpty()
        imports.clear()
        wildcardImports.clear()
        for (imp in module.statements.filterIsInstance<LFFile.LFImportDeclaration>()) {
            if (imp.wildcard) {
                wildcardImports += imp.part.joinToString(".")
            } else {
                val fqn = imp.part.joinToString(".")
                val simple = imp.alias ?: imp.part.last()
                imports[simple] = fqn
            }
        }
    }

    fun linkSupertypes(module: LFModule) {
        setupContext(module)
        for (stmt in module.statements) if (stmt is LFClass) linkClass(stmt, emptyList())
    }

    private fun linkClass(cls: LFClass, enclosing: List<ClassSymbol>) {
        val sym = cls.symbol ?: return
        val scope = Scope(ScopeKind.Function)
        for (tp in cls.tp) {
            val tps = TypeParameterSymbol(tp.id, tp)
            tp.symbol = tps
            scope.declare(tps)
        }

        typeNameOf(cls.superclass)?.let { superName ->
            val resolved = resolveTypeName(superName, scope, enclosing) as? ClassSymbol
            // 跳过自引用：根类 Any 的合成超类 `Any()` 会解析回自身，留 null 终止继承链
            // TODO 传递性环（A:B, B:A）仍会让 resolveMember 栈溢出，待补超类型环检测
            if (resolved != null && resolved != sym) sym.superclass = resolved
        }

        // cls.interfaces[0] 恒为超类型（真超类或合成 Any，见 ClassParser.parseSuperDecl），
        // 故 drop(1) 即被实现/扩展的接口。class 实现接口、interface 扩展接口共用此路径。
        sym.interfaces = cls.interfaces.drop(1)
            .mapNotNull { resolveTypeName(it.name, scope, enclosing) as? ClassSymbol }
            .filter { it != sym }

        val chain = listOf(sym) + enclosing
        for (nested in cls.nested) linkClass(nested, chain)
    }

    fun resolve(module: LFModule) {
        setupContext(module)
        for (stmt in module.statements) when (stmt) {
            is LFClass -> resolveClass(stmt, null, emptyList())
            is LFFunction -> resolveFunction(stmt, null, emptyList())
            is LFVariableDecl -> resolveProperty(stmt, Scope(ScopeKind.Block), emptyList())
            else -> {}
        }
    }

    private fun resolveClass(cls: LFClass, parentScope: Scope?, enclosing: List<ClassSymbol>) {
        val sym = cls.symbol ?: return
        val scope = parentScope?.child(ScopeKind.Function) ?: Scope(ScopeKind.Function)
        for (tp in cls.tp) {
            val sym = tp.symbol ?: TypeParameterSymbol(tp.id, tp).also { tp.symbol = it }
            scope.declare(sym)
        }

        val chain = listOf(sym) + enclosing
        for (tp in cls.tp) for (b in tp.upbounds) resolveType(b, scope, chain)
        for (itf in cls.interfaces) resolveType(itf, scope, chain)
        cls.primaryCtor?.params?.forEach { p ->
            resolveType(p.type, scope, chain)
            p.default?.let { resolveExpr(it, scope, chain) }
        }

        for (a in cls.attr) resolveAttribute(a, scope, chain)
        for (f in cls.functions) resolveFunction(f, scope, chain)
        for (c in cls.ctors) resolveFunction(c, scope, chain)
        for (i in cls.inits) resolveFunction(i, scope, chain)
        cls.deinit?.let { resolveFunction(it, scope, chain) }
        for (n in cls.nested) resolveClass(n, scope, chain)
    }

    private fun resolveFunction(fn: LFFunction, parentScope: Scope?, enclosing: List<ClassSymbol>) {
        val scope = parentScope?.child(ScopeKind.Function) ?: Scope(ScopeKind.Function)
        for (tp in fn.tp) {
            val tps = TypeParameterSymbol(tp.id, tp)
            tp.symbol = tps
            scope.declare(tps)
        }
        for (tp in fn.tp) for (b in tp.upbounds) resolveType(b, scope, enclosing)

        fn.receiver?.let { resolveType(it, scope, enclosing) }
        for (p in fn.params) {
            resolveType(p.type, scope, enclosing)
            val ps = ParameterSymbol(p.name, p)
            p.symbol = ps
            scope.declare(ps)
        }
        resolveType(fn.ret, scope, enclosing)

        if (fn is LFClassConstructor) when (val d = fn.delegation) {
            is LFConstructorDelegation.This -> for (a in d.args) resolveExpr(a.value, scope, enclosing)
            is LFConstructorDelegation.Super -> for (a in d.args) resolveExpr(a.value, scope, enclosing)
            null -> {}
        }

        fn.body?.let { resolveModule(it, scope, enclosing) }
    }

    private fun resolveAttribute(a: LFClassAttribute, scope: Scope, enclosing: List<ClassSymbol>) {
        a.type?.let { resolveType(it, scope, enclosing) }
        a.init?.let { resolveExpr(it, scope, enclosing) }
        a.delegator?.let { resolveExpr(it, scope, enclosing) }
        a.getter?.let { resolveFunction(it, scope, enclosing) }
        a.setter?.let { resolveFunction(it, scope, enclosing) }
    }

    /** 顶层属性（无 enclosing）。 */
    private fun resolveProperty(v: LFVariableDecl, scope: Scope, enclosing: List<ClassSymbol>) {
        v.type?.let { resolveType(it, scope, enclosing) }
        v.init?.let { resolveExpr(it, scope, enclosing) }
        v.delegator?.let { resolveExpr(it, scope, enclosing) }
    }

    private fun resolveModule(module: LFModule, parentScope: Scope, enclosing: List<ClassSymbol>) {
        val scope = parentScope.child(ScopeKind.Block)
        for (stmt in module.statements) resolveStmt(stmt, scope, enclosing)
    }

    private fun resolveStmt(stmt: LFStatement, scope: Scope, enclosing: List<ClassSymbol>) {
        when (stmt) {
            is LFModule -> resolveModule(stmt, scope, enclosing)
            is LFExprStatement -> resolveExpr(stmt.expr, scope, enclosing)
            is LFAssign -> {
                resolveExpr(stmt.target, scope, enclosing)
                resolveExpr(stmt.value, scope, enclosing)
            }
            is LFWhile -> {
                resolveExpr(stmt.condition, scope, enclosing)
                resolveModule(stmt.body, scope, enclosing)
            }
            is LFDoWhile -> {
                resolveModule(stmt.body, scope, enclosing)
                resolveExpr(stmt.condition, scope, enclosing)
            }
            is LFFor -> {
                resolveExpr(stmt.iterable, scope, enclosing)
                stmt.type?.let { resolveType(it, scope, enclosing) }
                resolveModule(stmt.body, scope, enclosing) // TODO 循环变量无声明节点,body 内引用暂留空
            }
            is LFFunction.LFReturnStmt -> resolveExpr(stmt.expr, scope, enclosing)
            is LFVariableDecl -> resolveLocalVar(stmt, scope, enclosing)
            is LFFunction -> resolveFunction(stmt, scope, enclosing) // 局部函数
            is LFClass -> resolveClass(stmt, scope, enclosing) // 局部类
            is LFBreak, is LFContinue -> {}
            is LFFile.LFPackageDeclaration, is LFFile.LFImportDeclaration -> {}
            else -> resolveExpr(stmt, scope, enclosing) // LFInvokeExpr 等表达式语句
        }
    }

    private fun resolveLocalVar(v: LFVariableDecl, scope: Scope, enclosing: List<ClassSymbol>) {
        v.type?.let { resolveType(it, scope, enclosing) }
        v.init?.let { resolveExpr(it, scope, enclosing) }
        v.delegator?.let { resolveExpr(it, scope, enclosing) }
        val sym = LocalVariableSymbol(v.name, v.mutable, v)
        v.symbol = sym
        scope.declare(sym) // 后续语句可见
    }

    private fun resolveExpr(expr: LFExpression, scope: Scope, enclosing: List<ClassSymbol>) {
        when (expr) {
            is LFFieldAccessExpr -> {
                if (expr.receiver == null) {
                    expr.symbol = resolveBareName(expr.field, scope, enclosing)
                } else {
                    resolveExpr(expr.receiver, scope, enclosing)
                    // 限定名 a.b：仅当 a 解析成类/包时查其成员/嵌套；a 是值（类型未知）→ 留空,交 TypeInference
                    expr.symbol = when (val rsym = (expr.receiver as? LFFieldAccessExpr)?.symbol) {
                        is ClassSymbol -> rsym.resolveMember(expr.field).firstOrNull()
                        is PackageSymbol -> provider.resolve("${rsym.fqn}.${expr.field}").firstOrNull()
                        else -> null
                    }
                }
            }
            is LFIndexAccessExpr -> {
                resolveExpr(expr.receiver, scope, enclosing)
                resolveExpr(expr.index, scope, enclosing)
            }
            is LFInvokeExpr -> {
                resolveExpr(expr.receiver, scope, enclosing) // 重载决议归 TypeInference,此处仅递归
                for (a in expr.arg) resolveExpr(a.value, scope, enclosing)
            }
            is LFUnaryExpr -> resolveExpr(expr.expr, scope, enclosing)
            is LFIncDec -> resolveExpr(expr.target, scope, enclosing)
            is LFBinaryExpr -> {
                resolveExpr(expr.left, scope, enclosing)
                resolveExpr(expr.right, scope, enclosing)
            }
            is LFIf -> {
                resolveExpr(expr.condition, scope, enclosing)
                resolveModule(expr.then, scope, enclosing)
                expr.elseBranch?.let { resolveModule(it, scope, enclosing) }
            }
            is LFTry -> {
                resolveModule(expr.body, scope, enclosing)
                for (c in expr.catches) {
                    resolveType(c.type, scope, enclosing)
                    resolveModule(c.body, scope, enclosing) // TODO catch 变量无声明节点,暂留空
                }
                expr.finallyBlock?.let { resolveModule(it, scope, enclosing) }
            }
            is LFThrow -> resolveExpr(expr.expr, scope, enclosing)
            is LFLambda -> {
                val s = scope.child(ScopeKind.Function)
                for (p in expr.params) {
                    resolveType(p.type, s, enclosing)
                    val ps = ParameterSymbol(p.name, p)
                    p.symbol = ps
                    s.declare(ps)
                }
                resolveModule(expr.body, s, enclosing) // TODO 隐式 it / lambda 提升归 HIR
            }
            is LFLiteral<*> -> {} // 含 LFThis：暂不绑 this-type
            else -> {}
        }
    }

    /**
     * 值引用 tower（裸名）：① 局部作用域 → ② 隐式 receiver(类成员,含继承) → ③ 显式单 import →
     * ④ 同包顶层 → ⑤ 通配 import + 默认导入(`ling.std.*`) → ⑥ 裸 FQN。
     */
    private fun resolveBareName(name: String, scope: Scope, enclosing: List<ClassSymbol>): Symbol? {
        scope.resolve(name).firstOrNull()?.let { return it }
        for (c in enclosing) c.resolveMember(name).firstOrNull()?.let { return it }
        imports[name]?.let { fqn -> provider.resolve(fqn).firstOrNull()?.let { return it } }
        provider.resolve(packagePrefix + name).firstOrNull()?.let { return it }
        for (pkg in wildcardImports + defaultImports)
            provider.resolve("$pkg.$name").firstOrNull()?.let { return it }
        provider.resolve(name).firstOrNull()?.let { return it }
        return null
    }

    private fun resolveType(ref: LTypeReference, scope: Scope, enclosing: List<ClassSymbol>) {
        when (ref) {
            is LTypeRef -> {
                // 占位符单例（any/unit/infer）不解析,避免污染共享实例
                if (ref === LTypeRef.any || ref === LTypeRef.unit || ref === LTypeRef.infer) return
                ref.symbol = resolveTypeName(ref.name, scope, enclosing)
                // TODO 解析失败暂不报 error（stdlib/import 未完善前 Int/String/Any 等查不到）
                for (arg in ref.tp) resolveType(arg, scope, enclosing)
            }
            is LTypeParamRef -> {
                // 类型参数引用（如 `fun <T> foo(x: T)` 中的 `T`）按名解析为 TypeParameterSymbol
                ref.symbol = (scope.resolve(ref.name).firstOrNull { it is TypeParameterSymbol } as? TypeParameterSymbol)
                    ?: enclosing.firstNotNullOfOrNull { c ->
                        c.resolveMember(ref.name).firstOrNull { it is TypeParameterSymbol } as? TypeParameterSymbol
                    }
            }
            is LTypeParamNone -> {}
        }
    }

    /**
     * 类型引用 tower：① 局部类型参数 → ② 外层类(类型参数/嵌套类,含继承) → ③ 显式单 import →
     * ④ 同包顶层 → ⑤ 通配 import + 默认导入(`ling.std.*`) → ⑥ 裸 FQN。
     */
    private fun resolveTypeName(name: String, scope: Scope, enclosing: List<ClassSymbol>): Symbol? {
        scope.resolve(name).firstOrNull { it is TypeParameterSymbol }?.let { return it }
        for (c in enclosing) c.resolveMember(name)
            .firstOrNull { it is TypeParameterSymbol || it is ClassSymbol }?.let { return it }
        imports[name]?.let { fqn -> provider.findClass(fqn)?.let { return it } }
        provider.findClass(packagePrefix + name)?.let { return it }
        for (pkg in wildcardImports + defaultImports)
            provider.findClass("$pkg.$name")?.let { return it }
        provider.findClass(name)?.let { return it }
        return null
    }

    /** 从超类构造调用 / 限定字段链提取类型名（`A.B.C`）。 */
    private fun typeNameOf(expr: LFExpression): String? = when (expr) {
        is LFInvokeExpr -> typeNameOf(expr.receiver)
        is LFFieldAccessExpr ->
            if (expr.receiver == null) expr.field
            else typeNameOf(expr.receiver)?.let { "$it.${expr.field}" }
        else -> null
    }
}
