package yukifuri.lang.lingspled.compiler.util

import yukifuri.lang.lingspled.compiler.exception.ParsingException

fun toInt(s: String): Int {
    return toLong(s).toInt()
}

fun toLong(s: String): Long {
    return when {
        s.startsWith("0b") -> s.substring(2).toLong(2)
        s.startsWith("0x") -> s.substring(2).toLong(16)
        else -> s.toLong()
    }
}

object Modifiers {

    interface ModifierType {
        val keyword: String
    }

    enum class Access(override val keyword: String) : ModifierType {
        Public("public"),
        Private("private"),
        Protected("protected"),
        Internal("internal"),
        Local("local");

        companion object {
            val map = entries.associateBy { it.keyword } - "local"
        }
    }

    enum class Class(override val keyword: String) : ModifierType {
        Abstract("abstract"),
        // interface 是带专属分发的类描述符, 无需 modifiers
        Enum("enum"),
        Data("data"),
        Annotation("annotation"),
        Sealed("sealed"),
        Open("open"),
        Final("final"),
        Inner("inner"),
        Value("value"),
        Native("native");

        companion object {
            val map = entries.associateBy { it.keyword }
        }
    }

    enum class Function(override val keyword: String) : ModifierType {
        Inline("inline"),
        Infix("infix"),
        Operator("operator"),
        Tailrec("tailrec"),
        Suspend("suspend"),
        Override("override"),
        Native("native"),
        Abstract("abstract"),
        Open("open"),
        Final("final"),
        Static("static");

        companion object {
            val map = entries.associateBy { it.keyword }
        }
    }

    enum class Property(override val keyword: String) : ModifierType {
        Lateinit("lateinit"),
        Volatile("volatile"),
        Static("static"),
        Const("const"),
        Abstract("abstract"),
        Override("override");

        companion object {
            val map = entries.associateBy { it.keyword }
        }
    }

    val all: Map<String, ModifierType> = Class.map + Function.map + Property.map
}

fun <T : Modifiers.ModifierType> Collection<String>.resolve(
    table: Map<String, T>, target: String
): List<T> = map {
    table[it] ?: throw ParsingException("Modifier '$it' is not applicable to $target")
}