package yukifuri.lang.lingspled.compiler.front.lexeme.token

import yukifuri.lang.lingspled.compiler.bridge.Position

open class Token(
    val type: TokenType,
    val text: String,
    val position: Position,
) {

    override fun toString() = "Token($type, text='$text', $position)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Token

        if (type != other.type) return false
        if (text != other.text) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }
}
