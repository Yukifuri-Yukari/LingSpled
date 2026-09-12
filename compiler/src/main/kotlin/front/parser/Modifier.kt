package yukifuri.lang.lingspled.compiler.front.parser

enum class Modifier(val symbol: String) {
    // ===== Access =====
    Private("private"),
    Protected("protected"),
    Internal("internal"),
    Public("public"),

    // Class, Instance, Inheritance
    Enum("enum"),
    Abstract("abstract"),
    Final("final"),
    Open("open"),
    Sealed("sealed"),
    Inner("inner"),
    Annotation("annotation"),
    Data("data"),
    Value("value"),

    // Fields
    Suspend("suspend"),
    Override("override"),
    Static("static"),
    Const("const"),
    Volatile("volatile"),
    Lateinit("lateinit"),
    Inline("inline"),
    Operator("operator"),
    Infix("infix"),
    Native("native"),
    Synchronized("synchronized"),

    ;
    companion object {
        val map = Modifier.entries.associateBy {
            it.symbol
        }
    }
}
