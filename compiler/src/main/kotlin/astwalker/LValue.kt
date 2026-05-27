package yukifuri.lang.lingspled.compiler.astwalker

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.clazz.LClass
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.module.Module

/**
 * Runtime values for the stack-based interpreter.
 */
open class LValue {
    data class LInt(val value: Int) : LValue() {
        override fun toString() = value.toString()
    }

    data class LDecimal(val value: Double) : LValue() {
        override fun toString() = value.toString()
    }

    data class LString(val value: String) : LValue() {
        override fun toString() = value
    }

    data class LBool(val value: Boolean) : LValue() {
        override fun toString() = value.toString()
    }

    data object LUnit : LValue() {
        override fun toString() = "Unit"
    }

    data object LNull : LValue() {
        override fun toString() = "null"
    }

    class LLambda(
        val arguments: List<Argument>,
        val body: Module,
        val closure: Environment   // 定义时捕获的词法环境
    ) : LValue()

    class LArray(val elements: MutableList<LValue>) : LValue() {
        override fun toString() = "[${elements.joinToString(", ")}]"
    }

    data class LRange(val start: Int, val end: Int) : LValue() {
        override fun toString() = "$start..$end"
    }

    class LObject(
        val cls: LClass,
        val fields: MutableMap<String, LValue> = mutableMapOf(),
    ) : LValue() {
        override fun toString(): String {
            return "${cls.name}@${System.identityHashCode(this).toHexString()}{${fields.entries.joinToString { "${it.key}=${it.value}" }}}"
        }
    }

    /**
     * Promote to a common numeric type for binary operations.
     * Int + Int -> Int, anything with Decimal -> Decimal.
     */
    fun asInt(): Int = when (this) {
        is LInt -> value
        is LDecimal -> value.toInt()
        is LBool -> if (value) 1 else 0
        else -> throw WalkerException("Cannot convert $this to Int")
    }

    fun asDecimal(): Double = when (this) {
        is LInt -> value.toDouble()
        is LDecimal -> value
        else -> throw WalkerException("Cannot convert $this to Double")
    }

    fun asBool(): Boolean = when (this) {
        is LBool -> value
        is LInt -> value != 0
        else -> throw WalkerException("Cannot convert $this to Bool")
    }

    fun isNumeric(): Boolean = this is LInt || this is LDecimal
}