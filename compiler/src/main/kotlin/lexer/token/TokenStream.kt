package yukifuri.lang.lingspled.compiler.lexer.token

class TokenStream(
    private val tokens: List<Token>
) : Collection<Token> {
    override val size = tokens.size
    private var ptr = 0

    fun snapshot() = ptr

    fun restore(snapshot: Int) { ptr = snapshot }

    fun hasNext() = ptr < tokens.size

    fun next() = tokens[ptr++]

    fun peek() = tokens[ptr]

    override fun isEmpty() = tokens.isEmpty()

    override fun contains(element: Token) = tokens.contains(element)

    override fun iterator() = tokens.iterator()

    override fun containsAll(elements: Collection<Token>) = tokens.containsAll(elements)

    override fun toString(): String {
        return "TokenStream$tokens"
    }
}