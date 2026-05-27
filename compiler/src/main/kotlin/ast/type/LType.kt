package yukifuri.lang.lingspled.compiler.ast.type

import yukifuri.lang.lingspled.compiler.ast.module.Annotation

data class LType(
    val name: String,
    val typeArgs: List<LType> = emptyList(),
    val nullable: Boolean = false,
    val annotations: List<Annotation> = emptyList()
) {
    val isGeneric: Boolean get() = typeArgs.isNotEmpty()
    val isFunction: Boolean get() = name == "->"
    val isWide: Boolean
        get() = name == "Long" || name == "Double"

    val slotSize: Int
        get() = if (isWide) 2 else 1

    val descriptorChar
        get() = when (name) {
            "Byte" -> "B"
            "Short" -> "S"
            "Int" -> "I"
            "Long" -> "L"
            "Float" -> "F"
            "Double" -> "D"
            "Boolean" -> "O"
            "String" -> "N"
            "Unit" -> "V"
            else -> "R$name;"
        }

    fun typename(): String {
        val annPrefix = if (annotations.isEmpty()) "" else annotations.joinToString(" ") { "@${it.name}" } + " "
        if (name == "->") {
            val params = typeArgs.dropLast(1).joinToString(", ") { it.typename() }
            val ret = typeArgs.last().typename()
            return "$annPrefix($params) -> $ret${if (nullable) "?" else ""}"
        }
        val typeArgsStr = if (typeArgs.isEmpty()) "" else "<${typeArgs.joinToString(", ") { it.typename() }}>"
        return "$annPrefix$name$typeArgsStr${if (nullable) "?" else ""}"
    }

    fun isSuperType(type: LType): Boolean {
        // todo
        return type == this || this.name == "Any"
    }

    override fun toString() = buildString {
        if (name == "->") {
            val params = typeArgs.dropLast(1).joinToString(", ") { it.typename() }
            val ret = typeArgs.last().typename()
            append("($params) -> $ret${if (nullable) "?" else ""}")
            return@buildString
        }
        append("LType(name=$name")
        if (typeArgs.isNotEmpty()) {
            append(", typeArgs=[")
            for (typeArg in typeArgs) {
                append(typeArg)
                append(", ")
            }
            append("]")
        }
        if (nullable)
            append(", nullable")
        if (annotations.isNotEmpty()) {
            append(", annotations=[")
            for (annotation in annotations) {
                append(annotation)
                append(", ")
            }
            append("]")
        }
        append(")")
    }

    companion object {
        val BYTE = LType("Byte")
        val SHORT = LType("Short")
        val INT = LType("Int")
        val LONG = LType("Long")
        val FLOAT = LType("Float")
        val DOUBLE = LType("Double")
        val BOOLEAN = LType("Boolean")
        val STRING = LType("String")
        val UNIT = LType("Unit")

        val ANY = LType("Any")
        val AUTO = LType("Instance")
        val INFER = LType("000TYPE_INFERENCE_REQUIRED")
    }
}