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

    fun fqn(): LCConfirmedName {
        val fqn = mutableListOf(ident())
        while (hasNext() && peek(TokenType.Dot)) {
            if (peek(1).type != TokenType.Identifier) break
            next()
            fqn.add(ident())
        }
        return LCConfirmedName(fqn)
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
                null to "val",
                null to "var",
                null to "vararg",
            )
        ) LParameterModifier.from(next().text) else null
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

    /**
     * Parses a type reference, including nested generic and function types.
     *
     * Examples:
     * `Type<A, Box<out B>>`, `*`, `(T1, T2) -> T3`, and `() -> T1`.
     */
    fun typeRef(): LTypeReference {
        if (peek(TokenType.LParen)) {
            next()
            val params = mutableListOf<LTypeReference>()
            if (!peek(TokenType.RParen)) {
                while (true) {
                    params.add(typeRef())
                    if (peek(TokenType.Comma)) {
                        next()
                        continue
                    }
                    break
                }
            }
            expect(TokenType.RParen)

            if (hasNext() && peek(TokenType.Arrow)) {
                next()
                return LFunctionTypeRef(params, typeRef())
            }

            if (params.size == 1) return params.single()
            diag("Expected '->' after a parenthesized type list", pos())
            throw IllegalStateException()
        }

        if (peek(TokenType.Operator, "*")) {
            next()
            return LTypeParameterNone
        }

        val name = ident()
        val typeArgs = if (hasNext() && peek(TokenType.Operator, "<")) {
            parseTypeArguments()
        } else {
            emptyList()
        }
        return LTypeRef(name, typeArgs, LVariance.None)
    }

    fun parseTypeArguments(): List<LTypeReference> {
        expect(TokenType.Operator, "<")
        val args = mutableListOf<LTypeReference>()

        if (peek(TokenType.Operator, ">")) {
            next()
            return args
        }

        while (true) {
            val variance = parseTypeVariance()
            val type = typeRef()
            args.add(applyTypeVariance(type, variance))

            when {
                peek(TokenType.Comma) -> {
                    next()
                    if (peek(TokenType.Operator, ">")) {
                        diag("Trailing comma is not allowed in a type argument list", pos())
                    }
                }

                peek(TokenType.Operator, ">") -> {
                    next()
                    break
                }

                else -> diag("Expected ',' or '>' in a type argument list", pos())
            }
        }

        return args
    }

    private fun parseTypeVariance(): LVariance = when {
        peek(null, "in") -> {
            next()
            LVariance.In
        }

        peek(null, "out") -> {
            next()
            LVariance.Out
        }

        else -> LVariance.None
    }

    private fun applyTypeVariance(type: LTypeReference, variance: LVariance): LTypeReference {
        if (variance == LVariance.None) return type
        return when (type) {
            is LTypeRef -> type.copy(variance = variance)
            is LTypeParameterRef -> type.copy(variance = variance)
            LTypeParameterNone -> {
                diag("A type variance cannot be applied to '*'", pos())
                type
            }
            is LFunctionTypeRef -> {
                diag("A type variance cannot be applied to a function type", pos())
                type
            }
        }
    }

    /** Parses a declaration such as `T : A` or `Type : A`. */
    fun typeDef(): LTypeDecl {
        val variance = parseTypeVariance()
        val name = ident()
        val bound = if (peek(TokenType.Colon)) {
            next()
            typeRef()
        } else null
        return LTypeDecl(name, bound, variance)
    }

    /** Parses generic declaration parameters such as `<T, U : A>`. */
    fun typeParameterDecl(): LTypeParameterDecl {
        val variance = parseTypeVariance()
        val name = ident()
        val bound = if (peek(TokenType.Colon)) {
            next()
            typeRef()
        } else null
        return LTypeParameterDecl(name, bound, variance)
    }

    fun parseTypeParameters(): List<LTypeParameterDecl> {
        if (!peek(TokenType.Operator, "<")) return emptyList()
        expect(TokenType.Operator, "<")
        val params = mutableListOf<LTypeParameterDecl>()
        if (peek(TokenType.Operator, ">")) {
            next()
            return params
        }

        while (true) {
            params.add(typeParameterDecl())
            when {
                peek(TokenType.Comma) -> {
                    next()
                    if (peek(TokenType.Operator, ">")) {
                        diag("Trailing comma is not allowed in type parameter list", pos())
                    }
                }

                peek(TokenType.Operator, ">") -> {
                    next()
                    break
                }

                else -> diag("Expected ',' or '>' in a type parameter list", pos())
            }
        }
        return params
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
        val params = if (isLambdaParameterStart()) {
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
        val modifier = if (
            peek(TokenType.Identifier) &&
            peek().text in setOf("crossinline", "noinline")
        ) {
            LParameterModifier.from(next().text)
        } else null
        val name = ident()
        val type = if (peek(TokenType.Colon)) {
            next()
            typeRef()
        } else null
        return LCLambdaParameter(modifier, name, type, null, pos)
    }

    private fun isLambdaParameterStart(): Boolean {
        if (!peek(TokenType.Identifier)) return false

        val modifierOffset = if (peek().text in setOf("crossinline", "noinline")) 1 else 0
        return peek(modifierOffset + 1).type in Parser.lambdaParamStart
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
