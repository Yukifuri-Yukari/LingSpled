package yukifuri.lang.lingspled.compiler.symbol

import yukifuri.lang.lingspled.compiler.ir.fst.LFClass
import yukifuri.lang.lingspled.compiler.ir.fst.LFFunction
import yukifuri.lang.lingspled.compiler.ir.fst.LFParameter
import yukifuri.lang.lingspled.compiler.ir.fst.LFVariableDecl

/**
 * 符号：声明的稳定句柄。约定 **use → [Symbol] → 声明**（学 K2 的 FirSymbol 间接层）——
 * 引用绑定到 Symbol 而非声明节点本身，声明节点重建/替换时引用不悬空。
 *
 * SymbolCollection 阶段为每个声明建 Symbol，并挂到声明节点的可变 `symbol` 字段上；
 * use 处的绑定（填 use 节点的 symbol）是后续独立 Resolution 遍的职责，本遍不做。
 */
sealed class Symbol {
    abstract val name: String
}

/** 包符号。[fqn] 形如 `language.test`。 */
class PackageSymbol(
    override val name: String,
    val fqn: String,
) : Symbol()

/**
 * 类符号。[fqn] = 包名 + 外层类 + 类名。
 * **成员单独一档**：属性/方法/嵌套类挂在本类的成员表上（不进全局 [SymbolProvider]，那只装顶层）；
 * 继承成员沿 [superclass] 链上溯查找。
 */
class ClassSymbol(
    override val name: String,
    val fqn: String,
    val declaration: LFClass,
) : Symbol() {
    private val memberTable = mutableMapOf<String, MutableList<Symbol>>()

    /** 超类符号，名字解析阶段填（继承成员查找沿此链上溯）。 */
    var superclass: ClassSymbol? = null

    val members: Map<String, List<Symbol>> get() = memberTable

    fun declareMember(symbol: Symbol) {
        memberTable.getOrPut(symbol.name) { mutableListOf() }.add(symbol)
    }

    /** 仅本类成员（含重载/同名，供冲突诊断）。 */
    fun resolveMemberLocal(name: String): List<Symbol> = memberTable[name].orEmpty()

    /** 本类 + 超类链查成员。 */
    fun resolveMember(name: String): List<Symbol> =
        resolveMemberLocal(name).ifEmpty { superclass?.resolveMember(name) ?: emptyList() }

    override fun toString() = "ClassSymbol($fqn)"
}

/** 函数符号。重载由所在 [Scope]/[SymbolProvider] 的多重映射承载，不在符号自身区分。 */
class FunctionSymbol(
    override val name: String,
    val declaration: LFFunction,
) : Symbol() {
    override fun toString() = "FunctionSymbol($name)"
}

/** 属性符号（类成员或顶层）。 */
class PropertySymbol(
    override val name: String,
    val mutable: Boolean,
    val declaration: LFVariableDecl,
) : Symbol() {
    override fun toString() = "PropertySymbol($name)"
}

/** 函数/构造器参数符号。 */
class ParameterSymbol(
    override val name: String,
    val declaration: LFParameter,
) : Symbol() {
    override fun toString() = "ParameterSymbol($name)"
}

/** 类型参数符号（`<T>` 等）。 */
class TypeParameterSymbol(
    override val name: String,
) : Symbol() {
    override fun toString() = "TypeParameterSymbol($name)"
}

/** 局部变量符号（函数/块体内的 `val`/`var`）。 */
class LocalVariableSymbol(
    override val name: String,
    val mutable: Boolean,
    val declaration: LFVariableDecl,
) : Symbol() {
    override fun toString() = "LocalVariableSymbol($name)"
}

/** 局部作用域类别。全局/成员符号不进作用域树（见 [SymbolProvider]），故只有函数体与块。 */
enum class ScopeKind { Function, Block }

/**
 * 局部符号表——作用域树：每个作用域有 [parent]，内部 name → 符号列表（多重映射，承载遮蔽）。
 * 只装参数 / 类型参数 / 块内局部；查找沿 parent 向上。全局/成员符号走 [SymbolProvider]。
 *
 * **本类供未来 Resolution 遍的 scope-tower 使用，SymbolCollection 阶段不构建它**（走 K2 路线：
 * 局部作用域在解析时重走 FST 边建临时 tower，不在收集期建存）。
 */
class Scope(
    val kind: ScopeKind,
    val parent: Scope? = null,
) {
    private val table = mutableMapOf<String, MutableList<Symbol>>()

    val symbols: Map<String, List<Symbol>> get() = table

    fun declare(symbol: Symbol) {
        table.getOrPut(symbol.name) { mutableListOf() }.add(symbol)
    }

    /** 仅本作用域。 */
    fun resolveLocal(name: String): List<Symbol> = table[name].orEmpty()

    /** 本作用域 + 沿 parent 向上。 */
    fun resolve(name: String): List<Symbol> =
        resolveLocal(name).ifEmpty { parent?.resolve(name) ?: emptyList() }

    fun child(kind: ScopeKind) = Scope(kind, this)

    override fun toString() = "Scope($kind, ${table.keys})"
}

/**
 * 全局符号表，横跨一个 module 的所有文件（学 K2 的 FirSymbolProvider）。
 * 一张扁平 **FQN → 符号列表** 多重映射，**只装顶层符号**：顶层类、顶层函数/属性（重载/同名靠列表承载）。
 * 类成员不在此处——挂在各自的 [ClassSymbol] 上（限定访问 `Outer.Inner`/`C.m` 由 resolver 先查类再查成员）。
 * 跨文件引用与前向引用天然可见——不需要谁定义在谁前面。冲突诊断留给调用方（SymbolCollector）。
 */
class SymbolProvider {
    private val table = mutableMapOf<String, MutableList<Symbol>>()

    val symbols: Map<String, List<Symbol>> get() = table

    fun register(fqn: String, symbol: Symbol) {
        table.getOrPut(fqn) { mutableListOf() }.add(symbol)
    }

    /** 该 FQN 下的全部符号（含重载/同名冲突，供冲突诊断）。 */
    fun resolve(fqn: String): List<Symbol> = table[fqn].orEmpty()

    fun findClass(fqn: String): ClassSymbol? =
        table[fqn]?.firstNotNullOfOrNull { it as? ClassSymbol }

    fun findFunction(fqn: String): List<FunctionSymbol> =
        table[fqn]?.filterIsInstance<FunctionSymbol>().orEmpty()

    fun findVar(fqn: String): PropertySymbol? =
        table[fqn]?.firstNotNullOfOrNull { it as? PropertySymbol }
}
