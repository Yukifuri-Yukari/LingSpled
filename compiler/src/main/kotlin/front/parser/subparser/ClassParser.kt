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
        // TODO: 泛型参数
        // TODO: 主构造函数（如果类名后紧跟 '(' ）
        val body = parseClassBody()
        return LCClass(name, modifiers, listOf(), body, pos)
    }

    fun parseInterface(modifiers: List<Modifier>): LCStatement {
        val pos = pos()
        val name = ident()
        val body = parseClassBody()
        return LCClass(name, modifiers, listOf(), body, pos)
    }

    private fun parseClassBody(): LCClass.ClassBody {
        if (!peek(TokenType.LBrace)) {
            return LCClass.ClassBody(listOf(), listOf(), null)
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