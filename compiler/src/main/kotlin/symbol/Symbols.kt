package yukifuri.lang.lingspled.compiler.symbol

import yukifuri.lang.lingspled.compiler.util.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.ir.fst.LFClass
import yukifuri.lang.lingspled.compiler.ir.fst.LFFunction
import yukifuri.lang.lingspled.compiler.ir.fst.LFParameter
import yukifuri.lang.lingspled.compiler.ir.fst.LFVariableDecl

sealed class Symbol {
    abstract val name: String
}

class PackageSymbol(
    override val name: String,
    val fqn: String,
) : Symbol()

class ClassSymbol(
    override val name: String,
    val fqn: String,
    val declaration: LFClass,
) : Symbol() {

    private val memberTable = mutableMapOf<String, MutableList<Symbol>>()
    var superclass: ClassSymbol? = null
    var interfaces: List<ClassSymbol> = emptyList()
    val members: Map<String, List<Symbol>> get() = memberTable

    fun declareMember(symbol: Symbol) {
        memberTable.getOrPut(symbol.name) { mutableListOf() }.add(symbol)
    }

    fun resolveMemberLocal(name: String): List<Symbol> = memberTable[name].orEmpty()

    fun resolveMember(name: String): List<Symbol> {
        val result = mutableListOf<Symbol>()
        result += resolveMemberLocal(name)
        superclass?.let { result += it.resolveMember(name) }
        for (itf in interfaces) result += itf.resolveMember(name)
        return result.distinct()
    }

    override fun toString() = "ClassSymbol($fqn)"
}

class FunctionSymbol(
    override val name: String,
    val declaration: LFFunction,
) : Symbol() {
    override fun toString() = "FunctionSymbol($name)"
}

class PropertySymbol(
    override val name: String,
    val mutable: Boolean,
    val declaration: LFVariableDecl,
) : Symbol() {
    override fun toString() = "PropertySymbol($name)"
}

class ParameterSymbol(
    override val name: String,
    val declaration: LFParameter,
) : Symbol() {
    override fun toString() = "ParameterSymbol($name)"
}

class TypeParameterSymbol(
    override val name: String,
    val declaration: LTypeParamDecl? = null,
) : Symbol() {
    override fun toString() = "TypeParameterSymbol($name)"
}

class EnumEntrySymbol(
    override val name: String,
    val enclosing: ClassSymbol,
) : Symbol() {
    override fun toString() = "EnumEntrySymbol($name)"
}

class LocalVariableSymbol(
    override val name: String,
    val mutable: Boolean,
    val declaration: LFVariableDecl,
) : Symbol() {
    override fun toString() = "LocalVariableSymbol($name)"
}

class BoundVariableSymbol(
    override val name: String,
) : Symbol() {
    override fun toString() = "BoundVariableSymbol($name)"
}

enum class ScopeKind { Function, Block }

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

class SymbolProvider {

    private val table = mutableMapOf<String, MutableList<Symbol>>()
    private val extensions = mutableMapOf<String, MutableList<FunctionSymbol>>()

    val symbols: Map<String, List<Symbol>> get() = table

    fun register(fqn: String, symbol: Symbol) {
        table.getOrPut(fqn) { mutableListOf() }.add(symbol)
    }

    fun registerExtension(name: String, fn: FunctionSymbol) {
        extensions.getOrPut(name) { mutableListOf() }.add(fn)
    }

    fun findExtensions(name: String): List<FunctionSymbol> = extensions[name].orEmpty()

    fun resolve(fqn: String): List<Symbol> = table[fqn].orEmpty()

    fun findClass(fqn: String): ClassSymbol? =
        table[fqn]?.firstNotNullOfOrNull { it as? ClassSymbol }

    fun findFunction(fqn: String): List<FunctionSymbol> =
        table[fqn]?.filterIsInstance<FunctionSymbol>().orEmpty()

    fun findVar(fqn: String): PropertySymbol? =
        table[fqn]?.firstNotNullOfOrNull { it as? PropertySymbol }
}
