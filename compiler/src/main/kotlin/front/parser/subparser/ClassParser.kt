package yukifuri.lang.lingspled.compiler.front.parser.subparser

import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.Modifier
import yukifuri.lang.lingspled.compiler.front.parser.Parser
import yukifuri.lang.lingspled.compiler.front.struct.*

class ClassParser(parent: Parser) : SubParser(parent) {

    fun parse(modifiers: List<Modifier>): LCStatement {
        return when (val kw = next().text) {
            "class" -> parseClass(modifiers)
            "interface" -> parseInterface(modifiers)
            else -> throwCE("Expected 'class' or 'interface', found '$kw'")
        }
    }

    fun parseClass(modifiers: List<Modifier>): LCStatement {
        val pos = pos()
        // 注意：parse() 已经消费了 "class"，这里直接读类名
        val name = ident()
        val typeParams = parseTypeParameters()
        val primaryCtor = parsePrimaryConstructor()
        val (supers, superConstructorArgs) = parseSupertypes()
        val body = parseClassBody()
        return LCClass(
            name,
            modifiers,
            supers,
            body.join(primaryCtor),
            pos,
            typeParams,
            superConstructorArgs,
        )
    }

    fun parseInterface(modifiers: List<Modifier>): LCStatement {
        val pos = pos()
        val name = ident()
        val typeParams = parseTypeParameters()
        val (supers, superConstructorArgs) = parseSupertypes()
        val body = parseClassBody()
        return LCClass(
            name,
            modifiers,
            supers,
            body,
            pos,
            typeParams,
            superConstructorArgs,
        )
    }

    private fun parseSupertypes(): Pair<List<LTypeRef>, List<List<LCExpression>>> {
        if (!peek(TokenType.Colon)) return emptyList<LTypeRef>() to emptyList()
        next()

        val supers = mutableListOf<LTypeRef>()
        val constructorArgs = mutableListOf<List<LCExpression>>()
        while (true) {
            val type = when (val parsed = typeRef()) {
                is LTypeRef -> parsed
                else -> {
                    diag("A class supertype must be a named type", pos())
                    throw IllegalStateException()
                }
            }
            supers.add(type)
            constructorArgs.add(
                if (peek(TokenType.LParen)) {
                    parseList(
                        TokenType.LParen,
                        TokenType.RParen,
                        TokenType.Comma,
                        ::parseExpressionArgument,
                    )
                } else {
                    emptyList()
                }
            )

            if (!peek(TokenType.Comma)) break
            next()
        }
        return supers to constructorArgs
    }

    private fun parseExpressionArgument(): LCExpression = expr.parse()

    private fun parseClassBody(): LCClass.ClassBody {
        if (!peek(TokenType.LBrace)) {
            return LCClass.ClassBody(mutableListOf(), mutableListOf(), null)
        }

        expect(TokenType.LBrace)

        var initBlock: LCClass.InitBlock? = null
        val constructors = mutableListOf<LCClass.Constructor>()
        val members = mutableListOf<LCStatement>()

        while (hasNext() && !peek(TokenType.RBrace)) {
            val modifiers = parseModifiers()

            when {
                peek(null, "constructor") -> {
                    constructors.add(parseSecondaryConstructor(modifiers))
                }

                peek(null, "init") -> {
                    if (initBlock != null) {
                        throwCE("Multiple init block definitions found")
                    }
                    initBlock = parseInitBlock()
                }

                peekKeyword("fun") -> {
                    members.add(module.functionDecl(modifiers))
                }

                peek().text in Parser.varDeclStart -> {
                    members.add(module.variableDecl(modifiers))
                }

                peekKeyword("class") || peekKeyword("interface") -> {
                    members.add(parse(modifiers))
                }

                else -> {
                    diag("Unexpected token '${peek().text}' in class body", pos())
                    throw IllegalStateException()
                }
            }
        }

        expect(TokenType.RBrace)
        return LCClass.ClassBody(constructors, members, initBlock)
    }

    private fun parsePrimaryConstructor(): LCClass.Constructor {
        val start = pos()

        val modifiers = if (!peek(TokenType.LParen)) {
            val parsed = parseModifiers()
            if (peek(null, "constructor")) {
                next()
                parsed
            } else if (parsed.isNotEmpty()) {
                throwCE("Expected primary constructor, found '${peek().text}'")
            } else {
                parsed
            }
        } else {
            emptyList()
        }

        val params = if (peek(TokenType.LParen)) {
            parseList(
                TokenType.LParen,
                TokenType.RParen,
                TokenType.Comma,
                ::parameterDecl
            )
        } else {
            emptyList()
        }

        return LCClass.Constructor(
            modifiers = modifiers,
            params = params,
            delegationCall = null,
            body = null,
            pos = start,
        )
    }

    private fun parseSecondaryConstructor(modifiers: List<Modifier>): LCClass.Constructor {
        val pos = expect("constructor").position
        val params = parseList(
            TokenType.LParen, TokenType.RParen,
            TokenType.Comma, ::parameterDecl
        )

        val delegationCall = if (peek(TokenType.Colon)) {
            next()
            expr.parse()
        } else null

        val body = if (peek(TokenType.LBrace)) parseBlock() else null

        return LCClass.Constructor(modifiers, params, delegationCall, body, pos)
    }

    private fun parseInitBlock(): LCClass.InitBlock {
        val pos = expect("init").position
        val body = parseBlock()
        return LCClass.InitBlock(body, pos)
    }
}
