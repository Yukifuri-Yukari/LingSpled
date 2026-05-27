package yukifuri.lang.lingspled.compiler.lexer.token

data class Token(
    val text: String,
    val type: TokenType,
    val row: Int,
    val col: Int,
) {
    override fun toString(): String {
        return "Token(text=\"$text\", type=$type, pos=($row, $col))"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false

        if (text != other.text) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
