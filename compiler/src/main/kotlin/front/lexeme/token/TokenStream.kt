package yukifuri.lang.lingspled.compiler.front.lexeme.token

import yukifuri.lang.lingspled.compiler.front.lexeme.Lexer

open class TokenStream(
    protected val list: List<Token>
) {

    protected var ptr = 0
    protected val size = list.size

    open fun hasNext() = ptr < size

    open fun next(): Token {
        return if (hasNext()) list[ptr++] else Lexer.eof()
    }

    open fun peek(): Token {
        return if (hasNext()) list[ptr] else Lexer.eof()
    }

    open fun peek(n: Int): Token {
        return if (hasNext()) list[ptr + n] else Lexer.eof()
    }

    open fun snapshot() = ptr

    open fun restore(snapshot: Int) {
        ptr = snapshot
    }

    override fun toString() = list.toString()

    open fun list(): List<Token> = list.toList()
}