package yukifuri.lang.lingspled.compiler.front.lexeme.token

import yukifuri.lang.lingspled.compiler.bridge.Position
import yukifuri.lang.lingspled.compiler.front.lexeme.Lexer
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType.Companion.whitespaces

class ProcessedTokenStream(list: List<Token>) : TokenStream(list) {

    /**
     * 判断当前位置之后是否还存在有效 token，尾随 trivia 不算在内。
     */
    override fun hasNext(): Boolean {
        var index = ptr
        while (index < size && list[index].type in whitespaces) index++
        return index < size
    }

    override fun peek(): Token {
        val p = ptr
        while (super.hasNext() && super.peek().type in whitespaces) ptr++
        val t = if (super.hasNext()) super.peek() else Lexer.eof()
        ptr = p
        return t
    }

    /** WARNING: `n` is 0-based. Which means peek(0) == LL(1), peek(1) == LL(2) */
    override fun peek(n: Int): Token {
        var ptr = 0
        val s = snapshot()
        while (ptr < n) { ptr++; next() }
        val t = peek()
        restore(s)
        return t
    }

    override fun next(): Token {
        while (super.hasNext() && super.peek().type in whitespaces) ptr++
        return super.next()
    }

    fun peekRaw() = super.peek()

    fun nextRaw() = super.next()

    fun peekRaw(n: Int): Token {
        val s = snapshot()
        repeat(n) { super.next() }
        val t = super.peek()
        restore(s)
        return t
    }

    /** Returns all the trivia tokens before `pos` after last effective token. */
    fun getTriviaTokens(pos: Position): List<TriviaToken> {
        var position = list.indexOfFirst { it.position == pos }
        val ls = mutableListOf<TriviaToken>()
        while (position > 0) {
            val t = list[--position]
            if (t.type == TokenType.Comment)
                ls.add(TriviaToken.Comment(t.position, t.text))
            else if (t.type == TokenType.Whitespace)
                ls.add(TriviaToken.Whitespace(t.position))
            else
                // Normal token,
                break // -it
        }
        ls.reverse()
        return ls
    }

    sealed class TriviaToken {
        data class Whitespace(val position: Position) : TriviaToken()
        data class Comment(val position: Position, val content: String) : TriviaToken()
    }
}
