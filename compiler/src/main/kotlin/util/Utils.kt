package yukifuri.lang.lingspled.compiler.util


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

fun <A, B> Collection<B>.cast(): List<A> {
    return this.cast { c, e ->
        throw ClassCastException("Cannot cast elements of collection $c into given type. $e")
    }
}

@Suppress("UNCHECKED_CAST")
fun <A, B> Collection<B>.cast(failureAction: (Collection<B>, Throwable) -> Unit): List<A> {
    return runCatching { map { it as A } }.onFailure { failureAction(this, it) }.getOrThrow()
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
        Interface("interface"),
        Annotation("annotation"),
        Enum("enum"),
        Data("data"),
        Sealed("sealed"),
        Open("open"),
        Final("final"),
        Inner("inner"),
        Value("value");

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
        Const("const");

        companion object {
            val map = entries.associateBy { it.keyword }
        }
    }

    val all: Map<String, ModifierType> = Class.map + Function.map + Property.map
}