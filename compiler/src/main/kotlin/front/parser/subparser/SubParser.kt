package yukifuri.lang.lingspled.compiler.front.parser.subparser

import yukifuri.lang.lingspled.compiler.exception.Diagnostics.Diagnostic
import yukifuri.lang.lingspled.compiler.exception.Diagnostics.Level
import yukifuri.lang.lingspled.compiler.exception.ParserException
import yukifuri.lang.lingspled.compiler.bridge.Position
import yukifuri.lang.lingspled.compiler.front.struct.*
import yukifuri.lang.lingspled.compiler.front.lexeme.token.Token
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.Modifier
import yukifuri.lang.lingspled.compiler.front.parser.Parser

sealed class SubParser(val parent: Parser) {

    val expr by lazy { parent.expr }
    val module by lazy { parent.module }
    val cls by lazy { parent.cls }

    fun peekRaw() = parent.ts.peekRaw()
    fun nextRaw() = parent.ts.nextRaw()
    fun peekRaw(n: Int) = parent.ts.peekRaw(n)

    fun hasNext() = parent.ts.hasNext()
    fun next() = parent.ts.next()
    fun next(n: Int) {
        for (`unused var` in 1..n) next()
    }

    fun peek() = parent.ts.peek()

    /** WARNING: `n` is 0-based. Which means peek(0) == LL(1), peek(1) == LL(2) */
    fun peek(n: Int) = parent.ts.peek(n)

    /**
     * Returns `true` if next token matches any of the rules.
     * Doesn't consume tokens.
     */
    fun peek(vararg pairs: Pair<TokenType?, String?>): Boolean {
        for ((first, second) in pairs) {
            if (peek(first, second)) return true
        }
        return false
    }

    fun peek(type: TokenType?, value: String? = null): Boolean {
        val t = peek()
        if (type != null && (type != t.type))
            return false
        if (value != null && (value != t.text))
            return false
        return true
    }

    fun skipWs() {
        while (peek(TokenType.NewLine)) next()
    }

    fun stmtEnd(optional: Boolean = false) {
        while (hasNext() && peekRaw().type in setOf(TokenType.Whitespace, TokenType.Comment)) {
            nextRaw()
        }

        when {
            !hasNext() || peek(TokenType.RBrace) -> return
            peekRaw().type == TokenType.NewLine -> nextRaw()
            peek().type == TokenType.Semicolon -> next()
            optional -> return
            else -> diag("Expected statement end", pos())
        }
    }

    fun expect(type: TokenType, value: String? = null): Token {
        if (!hasNext()) diag("EOF reached", parent.ts.list().last().position)
        val t = next()
        if (t.type == type && (value == null || t.text == value))
            return t
        else
            diag("Expected $type, actually ${t.type}", pos())
        throw IllegalStateException()
    }

    fun expect(value: String): Token {
        if (!hasNext()) diag("EOF reached", parent.ts.list().last().position)
        val t = next()
        if (t.text == value)
            return t
        else
            diag("Expected $value, actually ${t.text}", pos())
        throw IllegalStateException()
    }

    fun fqn(): LQualifiedName {
        val fqn = mutableListOf(ident())
        while (hasNext() && peek(TokenType.Dot)) {
            if (peek(1).type != TokenType.Identifier) break
            next()
            fqn.add(ident())
        }
        return LQualifiedName(fqn)
    }

    fun keyword(value: String) = expect(TokenType.Keyword, value)

    fun peekKeyword(value: String) = peek(TokenType.Keyword, value)

    fun ident(): String {
        val t = expect(TokenType.Identifier).text
        if (t.startsWith("`") && t.endsWith("`"))
            return t.dropLast(1).drop(1)
        return t
    }

    fun pos() = if (hasNext()) peek().position else -1 to -1

    fun <T> throwCE(message: String = ""): T {
        throw ParserException(message)
    }

    fun throwCE(message: String = ""): Nothing {
        throwCE<Nothing>(message)
    }

    fun parameterDecl(): LCParameter {
        val pos = pos()
        val modifier = if (peek(
                null to "crossinline",
                null to "noinline",
                TokenType.Keyword to "val",
                TokenType.Keyword to "var",
                TokenType.Keyword to "vararg",
            )
        ) LCParameter.Modifier.from(next().text) else null
        val name = ident()
        expect(TokenType.Colon)
        val type = typeRef()
        val default = if ( peek(null, "=")) {
            next()
            expr.parse()
        } else null
        return LCParameter(
            modifier = modifier,
            name = name,
            type = type,
            default = default,
            position = pos,
        )
    }

    fun argument(): LCArgument {
        val pos = pos()
        val name = if (peek(TokenType.Identifier) && peek(1).text == "=") {
            ident().also { expect("=") }
        } else null

        val expr = expr.parse()
        return LCArgument(name, expr, pos)
    }

    fun typeRef(): LCTypeRef {
        // lets dont consider too much, also mark it as todo: complex type ref (& def)
        val name = ident()
        return LCTypeRef(name)
    }

    fun parseBlock(optionalBlock: Boolean = false): LCModule {
        if (optionalBlock && !peek(TokenType.LBrace)) {
            val pos = pos()
            val stmt = module.parse()
            return LCModule(listOf(stmt), pos)
        }

        val pos = expect(TokenType.LBrace).position
        val builder = LCModule.Builder()
        while (hasNext() && !peek(TokenType.RBrace)) {
            val stmt = module.parse()
            builder.add(stmt)
        }
        expect(TokenType.RBrace)
        return builder.build(pos)
    }

    fun parseModifiers(): List<Modifier> {
        val list = mutableListOf<Modifier>()
        while (hasNext() && peek().text in Modifier.map.keys) {
            list.add(Modifier.map[next().text]!!)
        }
        return list
    }

    fun parseLambda(): LCLambda {
        val pos = expect(TokenType.LBrace).position
        if (peek(TokenType.RBrace))
            return LCLambda(
                listOf(),
                LCModule(listOf(), pos),
                pos).also { next() }
        val params = if (
            peek(TokenType.Identifier) &&
            peek(1).type in Parser.lambdaParamStart
            ) {
            parseList(
                null, TokenType.Arrow, TokenType.Comma,
                ::lambdaParameterDecl
            )
        } else listOf<LCLambdaParameter>().also { if (peek(TokenType.Arrow)) next() }

        val builder = LCModule.Builder()
        while (hasNext() && !peek(TokenType.RBrace)) {
            val stmt = module.parse()
            builder.add(stmt)
        }
        expect(TokenType.RBrace)
        return LCLambda(params, builder.build(pos), pos)
    }

    fun lambdaParameterDecl(): LCLambdaParameter {
        val pos = pos()
        val name = ident()
        val type = if (peek(TokenType.Colon)) {
            next()
            typeRef()
        } else null
        return LCLambdaParameter(null, name, type, null, pos)
    }

    fun <T> parseList(
        start: TokenType?,
        end: TokenType,
        separator: TokenType,
        action: () -> T,
    ): List<T> {
        val list = mutableListOf<T>()
        start?.let { expect(it) }
        while (true) {
            if (peek(end)) break
            list.add(action())
            if (peek(separator)) next()
            else break
        }
        expect(end)
        return list
    }

    fun diag(
        detail: String,
        start: Position,
        end: Position = run { val p = pos(); if (p != -1 to -1) p else start },
        info: String = "",
        level: Level = Level.Error,
        sidenote: String = ""
    ) {
        parent.diagnostics.add(Diagnostic(detail, start to end, info, level, sidenote))

        if (level == Level.Error) throw ParserException("Error when parsing: $detail $start:$end")
    }
}