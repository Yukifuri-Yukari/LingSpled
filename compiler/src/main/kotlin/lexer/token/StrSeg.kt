package yukifuri.lang.lingspled.compiler.lexer.token

import yukifuri.lang.lingspled.compiler.lexer.Position

/**
 * 字符串字面量分段（带插值时由 Lexer 产出，挂在 [Token.template] 上）。
 * [Lit] 是字面文本，[Expr] 是 `${...}` / `$id` 里的**原始表达式源码**——Parser 拿它重新 lex + parseExpression。
 */
sealed class StrSeg {
    data class Lit(val text: String) : StrSeg()
    data class Expr(val source: String, val position: Position) : StrSeg()
}
