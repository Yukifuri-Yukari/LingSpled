package yukifuri.lang.lingspled.compiler.ast.literal

class StringLiteral(value: String) : Literal<String>(value) {
    override fun toString(): String {
        return "StringLiteral(value=\"$value\")"
    }
}