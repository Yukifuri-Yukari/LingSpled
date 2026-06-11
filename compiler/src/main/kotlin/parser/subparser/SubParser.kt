package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.exception.EofException
import yukifuri.lang.lingspled.compiler.exception.ParsingException
import yukifuri.lang.lingspled.compiler.general.LTypeDecl
import yukifuri.lang.lingspled.compiler.general.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.general.LTypeReference
import yukifuri.lang.lingspled.compiler.general.Variance
import yukifuri.lang.lingspled.compiler.lexer.token.Token
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Modifiers

open class SubParser(val parent: Parser) {

    protected val expr by lazy { parent.expr }
    protected val module by lazy { parent.module }
    protected val toplevel by lazy { parent.toplevel }
    protected val cls by lazy { parent.cls }
    protected val diag by lazy { parent.diag }

    // 不会抛 EofException：流耗尽时退回最后一个 token 的位置
    fun safePos(): Pair<Int, Int> =
        if (hasNext()) peek().position
        else parent.ts.lastOrNull()?.position ?: (0 to 0)

    fun diagnostic(
        detail: String,
        info: String = "Error",
        start: Pair<Int, Int> = safePos(),
        sidenote: String = "",
        end: Pair<Int, Int> = if (hasNext()) peek().position else start,
    ): Nothing {
        diag.add(info, detail, start, end, sidenote)
        throw ParsingException(detail)
    }

    // region Stream methods
    fun hasNext() = parent.ts.hasNext()
    fun next() = parent.ts.next()
    fun peek() = parent.ts.peek()

    fun peek(text: String, type: TokenType? = null) =
        peek().text == text && (type == null || peek().type == type)
    fun peek(n: Int): Token {
        require(n >= 1)
        if (!hasNext()) throw EofException()
        val s = parent.ts.snapshot()
        for (i in 1 until n) {
            if (!hasNext()) throw EofException()
            next()
        }
        val t = peek()
        parent.ts.restore(s)
        return t
    }
    fun peek(type: TokenType) =
        hasNext() && peek().type == type
    fun tryConsume(type: TokenType?, text: String = ""): Boolean {
        if (!hasNext()) return false

        val p = peek()
        if ((type == null || p.type == type) &&
            (text.isEmpty() || p.text == text)) {
            next()
            return true
        }
        return false
    }
    fun keyword(text: String): Token {
        if (!hasNext())
            throw EofException()

        if (!(peek().type == TokenType.Keyword && peek().text == text))
            throw ParsingException("Expected keyword $text, actually \"${peek().text}\"")

        return next()
    }
    fun expect(type: TokenType, text: String = ""): Token {
        if (!hasNext()) throw EofException()
        val t = peek()
        if (t.type != type || (text.isNotEmpty() && t.text != text)) throw ParsingException("Expected $type, got $t")
        return next()
    }
    fun expectId() =
        expect(TokenType.Identifier)
    // endregion

    fun skipStmtEnd() {
        if (!hasNext()) throw ParsingException("Unexpected EOF")

        if (peek().type == TokenType.Semicolon) next()
        if (peek().type == TokenType.NewLine) next()
        if (peek().type == TokenType.RBrace) return
    }
    fun skipWs() {
        while (hasNext() && peek().type == TokenType.NewLine) next()
    }

    fun parseTypeDecl(): LTypeDecl {
        val name = expectId().text
        return LTypeDecl(name)
    }
    fun parseTypeRef(): LTypeRef {
        val name = expectId().text
        val tp = if (peek("<", TokenType.Operator)) {
            next()
            val list = mutableListOf<LTypeReference>()
            do {
                skipWs()
                list.add(parseTypeRef())
                skipWs()
            } while (tryConsume(TokenType.Comma))
            expect(TokenType.Operator, ">")
            list
        } else emptyList()
        val nullable = tryConsume(TokenType.Question)
        return LTypeRef(name, tp, nullable = nullable)
    }

    // <A, B : A, C, D : Array<A>>
    fun parseTypeParams(): List<LTypeParamDecl> {
        if (!peek("<", TokenType.Operator)) return emptyList()
        next()
        val list = mutableListOf<LTypeParamDecl>()
        do {
            skipWs()

            val variance = when (peek().text) {
                "in" -> Variance.In.also { next() }
                "out" -> Variance.Out.also { next() }
                else -> Variance.Invariant
            }
            val id = expectId().text
            val upbounds = if (tryConsume(TokenType.Colon)) listOf(parseTypeRef())
            else listOf(LTypeRef.any)

            list.add(LTypeParamDecl(id, variance, upbounds))
            skipWs()
        } while (tryConsume(TokenType.Comma))
        expect(TokenType.Operator, ">")
        return list
    }

    // where C : Comparator<C>, C : Comparable<C>
    // 把额外的上界合并进已声明的类型参数中 (替换掉默认的 Any 占位上界)
    fun parseWhereClause(typeParams: List<LTypeParamDecl>): List<LTypeParamDecl> {
        if (!tryConsume(TokenType.Identifier, "where")) return typeParams

        val extra = mutableListOf<Pair<String, LTypeRef>>()
        do {
            skipWs()
            val id = expectId().text
            expect(TokenType.Colon)
            extra.add(id to parseTypeRef())
            skipWs()
        } while (tryConsume(TokenType.Comma))

        val byId = extra.groupBy({ it.first }, { it.second })
        return typeParams.map { tp ->
            val additional = byId[tp.id] ?: return@map tp
            val base = if (tp.upbounds == listOf(LTypeRef.any)) emptyList() else tp.upbounds
            tp.copy(upbounds = base + additional)
        }
    }

    fun parseModifiers(): Pair<Modifiers.Access, List<Modifiers.ModifierType>> {
        val collected = mutableListOf<Modifiers.ModifierType>()
        val set = Modifiers.all.keys + Modifiers.Access.map.keys
        val access = mutableListOf<Modifiers.Access>()
        while (hasNext() && peek().text in set) {
            val p = peek().text
            if (p in Modifiers.Access.map.keys) access.add(Modifiers.Access.map[p]!!)
            else if (p in Modifiers.all.keys) collected.add(Modifiers.all[p]!!)
            next()
        }

        if (access.size > 1)
            throw ParsingException("Expected one access modifier, got ${access.size} modifier(s)")

        val duplicates = collected.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw ParsingException("Duplicate modifiers: ${duplicates.joinToString { it.keyword }}")

        return (access.firstOrNull() ?: Modifiers.Access.Public) to collected
    }
    fun parseAnnotations(): List<LAAnnotation> {
        val annotations = mutableListOf<LAAnnotation>()
        while (hasNext() && peek(TokenType.At)) {
            expect(TokenType.At)
            val name = expectId().text
            val arguments = if (peek(TokenType.LParen)) parseList(
                TokenType.LParen, TokenType.RParen, TokenType.Comma
            ) { module.parseArgument() } else listOf()

            annotations.add(LAAnnotation(name, arguments))
        }

        return annotations
    }
    fun <T> parseList(
        start: TokenType? = null,
        terminator: TokenType,
        separator: TokenType = TokenType.Comma,
        function: () -> T
    ): List<T> {
        start?.let { expect(it) }
        val list = mutableListOf<T>()
        while (hasNext() && !peek(terminator)) {
            skipWs()
            list.add(function())
            if (!tryConsume(separator)) break
        }
        skipWs()
        expect(terminator)
        return list
    }

    companion object {
        val safepoints = setOf(
            TokenType.NewLine,
            TokenType.Semicolon,
            TokenType.RBrace
        )
    }
}