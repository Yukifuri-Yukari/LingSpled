package yukifuri.lang.lingspled.compiler.symbol

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.fst.LFClass
import yukifuri.lang.lingspled.compiler.ir.fst.LFFile
import yukifuri.lang.lingspled.compiler.ir.fst.LFFunction
import yukifuri.lang.lingspled.compiler.ir.fst.LFModule
import yukifuri.lang.lingspled.compiler.ir.fst.LFStatement
import yukifuri.lang.lingspled.compiler.ir.fst.LFVariableDecl
import yukifuri.lang.lingspled.compiler.lexer.Position

/**
 * SymbolCollection 遍：走 FST 收集声明，建符号表，回填声明节点的 `symbol` 字段。
 * **本遍只建表、不解析名字**（use→decl 的绑定是后续 Resolution 遍）。多文件共享一个 [provider]，
 * 逐文件 [collect]，跨文件/前向引用天然可见。
 *
 * 分三档（见 [SymbolProvider]/[ClassSymbol]/[Scope]）：顶层进全局 provider、成员进所属 ClassSymbol、
 * 局部进作用域树。**本块（3a）只做顶层与成员**；参数/类型参数/局部 + 作用域树是 3b。
 */
class SymbolCollector(
    private val provider: SymbolProvider,
    private val diag: Diagnostics,
) {

    fun collect(module: LFModule) {
        val pkg = module.statements
            .filterIsInstance<LFFile.LFPackageDeclaration>()
            .firstOrNull()?.part?.joinToString(".").orEmpty()
        val prefix = if (pkg.isEmpty()) "" else "$pkg."
        for (stmt in module.statements) collectTopLevel(stmt, prefix)
    }

    private fun collectTopLevel(stmt: LFStatement, prefix: String) {
        when (stmt) {
            is LFClass -> collectClass(stmt, prefix, enclosing = null)
            is LFFunction -> collectTopFunction(stmt, prefix)
            is LFVariableDecl -> collectTopProperty(stmt, prefix)
            else -> {}
        }
    }

    private fun collectTopFunction(fn: LFFunction, prefix: String) {
        val sym = FunctionSymbol(fn.name, fn)
        fn.symbol = sym
        registerTop(prefix + fn.name, sym, fn.position)
        // 扩展函数（带 receiver 的顶层 fun）另入副索引，供成员/中缀调用按 receiver 类型解析。
        if (fn.receiver != null) provider.registerExtension(fn.name, sym)
    }

    private fun collectTopProperty(v: LFVariableDecl, prefix: String) {
        val sym = PropertySymbol(v.name, v.mutable, v)
        v.symbol = sym
        registerTop(prefix + v.name, sym, v.position)
    }

    private fun collectClass(cls: LFClass, prefix: String, enclosing: ClassSymbol?) {
        val fqn = (enclosing?.let { "${it.fqn}." } ?: prefix) + cls.name
        val sym = ClassSymbol(cls.name, fqn, cls)
        cls.symbol = sym

        if (enclosing == null) registerTop(fqn, sym, cls.position)
        else declareMember(enclosing, sym, cls.position)

        for (tp in cls.tp) {
            val tps = TypeParameterSymbol(tp.id, tp)
            tp.symbol = tps
            sym.declareMember(tps)
        }

        for (attr in cls.attr) {
            val p = PropertySymbol(attr.name, attr.mutable, attr)
            attr.symbol = p
            declareMember(sym, p, attr.position)
            collectAccessor(sym, attr.getter)
            collectAccessor(sym, attr.setter)
        }
        for (fn in cls.functions) {
            val f = FunctionSymbol(fn.name, fn)
            fn.symbol = f
            declareMember(sym, f, fn.position)
        }
        for (ctor in cls.ctors) {
            val f = FunctionSymbol(ctor.name, ctor)
            ctor.symbol = f
            sym.declareMember(f)
        }
        for (nested in cls.nested) collectClass(nested, prefix, enclosing = sym)
        // enum 条目作为该 enum 类的成员（`Color.RED`）。匿名体成员暂不收集（留后续）。
        for (entry in cls.entries) declareMember(sym, EnumEntrySymbol(entry.name, sym), entry.position)
    }

    private fun collectAccessor(owner: ClassSymbol, accessor: LFFunction?) {
        accessor ?: return
        val f = FunctionSymbol(accessor.name, accessor)
        accessor.symbol = f
        declareMember(owner, f, accessor.position)
    }

    private fun registerTop(fqn: String, symbol: Symbol, pos: Position) {
        val clash = when (symbol) {
            is ClassSymbol -> provider.findClass(fqn) != null
            is PropertySymbol -> provider.findVar(fqn) != null || provider.findClass(fqn) != null
            else -> false
        }
        if (clash) conflict("顶层符号 '$fqn' 重复声明", pos)
        provider.register(fqn, symbol)
    }

    private fun declareMember(owner: ClassSymbol, symbol: Symbol, pos: Position) {
        if (symbol !is FunctionSymbol) {
            val clash = owner.resolveMemberLocal(symbol.name).any { it !is FunctionSymbol }
            if (clash) conflict("成员 '${symbol.name}' 在类 '${owner.fqn}' 中重复声明", pos)
        }
        owner.declareMember(symbol)
    }

    private fun conflict(detail: String, pos: Position) =
        diag.add("Error", detail, pos, pos)
}