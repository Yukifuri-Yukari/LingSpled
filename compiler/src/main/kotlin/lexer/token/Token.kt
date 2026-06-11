package yukifuri.lang.lingspled.compiler.lexer.token

import yukifuri.lang.lingspled.compiler.lexer.Position

data class Token(
    val text: String,
    var type: TokenType,
    private val row: Int, private val col: Int,
    val position: Position = row to col
) {

    override fun toString() = "Token(text='$text', type=$type, position=$position)"
}