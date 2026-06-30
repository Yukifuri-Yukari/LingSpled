package yukifuri.lang.lingspled.compiler.lexer.token

import yukifuri.lang.lingspled.compiler.lexer.Position

data class Token(
    val text: String,
    var type: TokenType,
    private val row: Int, private val col: Int,
    val position: Position = row to col,
    /** 仅插值字符串非空：字面/插值分段（见 [StrSeg]）。普通串为 null，行为不变。 */
    val template: List<StrSeg>? = null,
) {

    override fun toString() = "Token(text='$text', type=$type, position=$position)"
}