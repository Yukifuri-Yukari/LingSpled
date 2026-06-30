package yukifuri.lang.lingspled.compiler.type

import yukifuri.lang.lingspled.compiler.util.LTypeParamNone
import yukifuri.lang.lingspled.compiler.util.LTypeParamRef
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.util.LTypeReference
import yukifuri.lang.lingspled.compiler.util.Variance
import yukifuri.lang.lingspled.compiler.symbol.ClassSymbol
import yukifuri.lang.lingspled.compiler.symbol.TypeParameterSymbol

sealed class LType {

    abstract val nullable: Boolean

    abstract fun withNullability(value: Boolean): LType

    data class Class(
        val symbol: ClassSymbol,
        val args: List<LType> = emptyList(),
        override val nullable: Boolean = false,
    ) : LType() {
        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "${symbol.name}${argsStr(args)}${nullStr(nullable)}"
    }

    data class Primitive(
        val kind: Kind,
        override val nullable: Boolean = false,
    ) : LType() {
        enum class Kind { Int, Long, Short, Byte, Float, Double, Char, Boolean }

        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "$kind${nullStr(nullable)}"
    }

    data class TypeParam(
        val symbol: TypeParameterSymbol,
        override val nullable: Boolean = false,
    ) : LType() {
        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "${symbol.name}${nullStr(nullable)}"
    }

    data class Function(
        val params: List<LType>,
        val ret: LType,
        override val nullable: Boolean = false,
    ) : LType() {
        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "(${params.joinToString(", ")}) -> $ret${nullStr(nullable)}"
    }

    data class Builtin(
        val name: String,
        override val nullable: Boolean = false,
    ) : LType() {
        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "$name${nullStr(nullable)}"
    }

    object Unit : LType() {
        override val nullable get() = false
        override fun withNullability(value: Boolean) = this // Unit? 罕见,v1 折叠为 Unit
        override fun toString() = "Unit"
    }

    data class Nothing(override val nullable: Boolean = false) : LType() {
        override fun withNullability(value: Boolean) = copy(nullable = value)
        override fun toString() = "Nothing${nullStr(nullable)}"
    }

    open class Error private constructor(open val reason: String? = null) : LType() {
        override val nullable get() = false
        override fun withNullability(value: Boolean) = this
        override fun toString() = "Error(${reason ?: ""})"

        override fun equals(other: Any?) =
            if (this === other) true else if (other !is Error) false else reason == other.reason

        override fun hashCode() = reason?.hashCode() ?: 0

        data class UnHandled(override val reason: String? = null) : Error(reason) {
            override fun toString() = "Error(Unhandled-Error: ${reason ?: ""})"
        }

        data class NoOverloads(override val reason: String? = null) : Error(reason) {
            override fun toString() = "Error(No-Overloads: ${reason ?: ""})"
        }

        companion object {
            val infer = Error("/Infer")

            val untypeUninitProp = Error("/Untyped-Uninitialized-Property")
            val propCycle = Error("/Property-Cycle")
            val primitiveNotFound = Error("/Primitive-Not-Found")
            val noThisContext = Error("/No-This-Context")
            val boundVar = Error("/Bound-Var")
            val memberAccess = Error("/Member-Access")
            val localUntyped = Error("/Local-Untyped")
            val unbound = Error("/Unbound")

            val errFor = Error("for")
            val errBinOp = Error("BinaryOperator")
            val errInfix = Error("InfixOperator")

            val resUnresolved = Error("Resolution/Unresolved")
            val resTpRef = Error("Resolution/Type-Parameter-Ref")
            val resTpNone = Error("Resolution/Type-Parameter-None")
        }
    }

    companion object {
        private fun nullStr(n: Boolean) = if (n) "?" else ""
        private fun argsStr(args: List<LType>) =
            if (args.isEmpty()) "" else "<${args.joinToString(", ")}>"

        private val PRIMITIVES: Map<String, Primitive.Kind> = mapOf(
            "Byte" to Primitive.Kind.Byte,
            "Short" to Primitive.Kind.Short,
            "Int" to Primitive.Kind.Int,
            "Long" to Primitive.Kind.Long,
            "Float" to Primitive.Kind.Float,
            "Double" to Primitive.Kind.Double,
            "Char" to Primitive.Kind.Char,
            "Boolean" to Primitive.Kind.Boolean,
        )

        fun lowerPrimitive(name: String): Primitive.Kind? = PRIMITIVES[name]

        val Int = Primitive(Primitive.Kind.Int)
        val Long = Primitive(Primitive.Kind.Long)
        val Float = Primitive(Primitive.Kind.Float)
        val Double = Primitive(Primitive.Kind.Double)
        val Boolean = Primitive(Primitive.Kind.Boolean)
        val Any = Builtin("Any")
        val NullLit = Nothing(nullable = true)
    }
}

fun LTypeReference.lower(): LType = when (this) {
    is LTypeRef -> when {
        this === LTypeRef.infer -> LType.Error.infer
        this === LTypeRef.unit || name == "Unit" || name == "ling.std.Unit" -> LType.Unit
        this === LTypeRef.any || name == "Any" || name == "ling.std.Any" ->
            LType.Builtin("Any", nullable)
        // String 不是 primitive/占位类型——是有 String.ling 幕后的真类，走 symbol → Class（见下 else 分支）。
        else -> {
            val kind = LType.lowerPrimitive(name)
            when {
                kind != null -> LType.Primitive(kind, nullable)
                else -> when (val s = symbol) {
                    is ClassSymbol -> LType.Class(s, tp.map { it.lower() }, nullable)
                    is TypeParameterSymbol -> LType.TypeParam(s, nullable)
                    else -> LType.Error.resUnresolved // TODO std/import 完善后收紧成诊断
                }
            }
        }
    }
    is LTypeParamRef -> when (val s = symbol) {
        is TypeParameterSymbol -> LType.TypeParam(s, nullable)
        else -> LType.Error.resTpRef
    }
    is LTypeParamNone -> LType.Error.resTpNone
}

fun LType.isAssignableTo(target: LType): Boolean {
    if (this is LType.Error || target is LType.Error) return true
    if (this is LType.Nothing) return true // 底类型可赋给任意
    if (this.nullable && !target.nullable) return false // 可空不可赋给非空
    if (this.withNullability(false) == target.withNullability(false)) return true
    if (target is LType.Builtin && target.name == "Any") return true // 万物 <: Any（nullability 已把关）
    // 类型参数：按上界判定（`T : Bound` ⟹ `T <: Bound`，再递归比 Bound 与 target）
    if (this is LType.TypeParam) return boundClass()?.isAssignableTo(target) ?: false
    if (this is LType.Class && target is LType.Class) {
        if (this.symbol == target.symbol) return argsAssignableTo(this.args, target.args, target.symbol)
        return isSubclassOf(this.symbol, target.symbol, mutableSetOf())
    }
    return false
}

private fun argsAssignableTo(subArgs: List<LType>, superArgs: List<LType>, symbol: ClassSymbol): Boolean {
    if (subArgs.isEmpty() || superArgs.isEmpty()) return true
    if (subArgs.size != superArgs.size) return false
    val params = symbol.declaration.tp
    return subArgs.indices.all { i ->
        val sub = subArgs[i]
        val sup = superArgs[i]
        when (params.getOrNull(i)?.variance ?: Variance.Invariant) {
            Variance.Out -> sub.isAssignableTo(sup)
            Variance.In -> sup.isAssignableTo(sub)
            Variance.Invariant -> sub.isAssignableTo(sup) && sup.isAssignableTo(sub)
        }
    }
}

fun LType.boundClass(seen: MutableSet<TypeParameterSymbol> = mutableSetOf()): LType.Class? = when (this) {
    is LType.Class -> this
    is LType.TypeParam ->
        if (!seen.add(symbol)) null
        else symbol.declaration?.upbounds?.firstOrNull()?.lower()?.boundClass(seen)
    else -> null
}

private fun isSubclassOf(sub: ClassSymbol, target: ClassSymbol, seen: MutableSet<ClassSymbol>): Boolean {
    if (sub == target) return true
    if (!seen.add(sub)) return false
    sub.superclass?.let { if (isSubclassOf(it, target, seen)) return true }
    for (itf in sub.interfaces) if (isSubclassOf(itf, target, seen)) return true
    return false
}
