package yukifuri.lang.lingspled.compiler.front.parser.subparser

import yukifuri.lang.lingspled.compiler.front.struct.*
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.Modifier
import yukifuri.lang.lingspled.compiler.front.parser.Parser

class ModuleParser(parent: Parser) : SubParser(parent) {

    fun parse(): LCStatement {
        val modifiers = parseModifiers()
        return when {
            peekKeyword("fun") -> functionDecl(modifiers)
            modifiers.isNotEmpty() -> throwCE("Modifier exists before illegal target")
            peek().text in Parser.varDeclStart -> variableDecl(listOf())
            peekKeyword("for") -> forLoop()
            peekKeyword("while") -> whileLoop()
            peekKeyword("if") -> ifStmt()
            peekKeyword("return") -> retStmt()
            peekKeyword("break") || peekKeyword("continue") -> breakAndContinue()
            else -> LCExprStatement(expr.parse()).also { stmtEnd(true) }
        }
    }

    fun functionDecl(modifiers: List<Modifier>): LCStatement {
        val pos = keyword("fun").position
        // TODO: Extension Type
        val name = ident()
        val params = parseList(
            TokenType.LParen, TokenType.RParen,
            TokenType.Comma, ::parameterDecl
        )
        val returnType = if (peek(TokenType.Colon)) {
            next()
            typeRef()
        } else LCTypeRef.unit

        val body = parseBlock()

        return LCFunction(name, params, returnType, body, modifiers, pos)
    }

    fun variableDecl(modifiers: List<Modifier>): LCStatement {
        if (peek().text !in Parser.varDeclStart)
            throw IllegalStateException("-")

        val position = pos()
        val mutable = next().text == "var"
        val id = ident()
        val type = if (peek(null, ":")) {
            next()
            typeRef()
        } else null
        expect("=")
        val init = expr.parse()
        stmtEnd()
        return LCVariableDecl(id, type, init, mutable, modifiers, position)
    }

    fun forLoop(): LCStatement {
        val pos = keyword("for").position
        expect(TokenType.LParen)
        val name = ident()
        expect("in")
        val expr = expr.parse()
        expect(TokenType.RParen)
        val module = parseBlock(true)
        return LCCtrlFor(name, expr, module, pos)
    }

    fun whileLoop(): LCStatement {
        val pos = keyword("while").position
        expect(TokenType.LParen)
        val expr = expr.parse()
        expect(TokenType.RParen)
        val module = parseBlock(true)
        return LCCtrlWhile(expr, module, pos)
    }

    fun ifStmt(): LCStatement {
        val pos = keyword("if").position
        expect(TokenType.LParen)
        val expr = expr.parse()
        expect(TokenType.RParen)
        val block = parseBlock(true)
        val elseBlock = if (peekKeyword("else")) {
            next()
            parseBlock(true)
        } else null
        return LCCtrlIf(expr, block, elseBlock, pos)
    }

    fun retStmt(): LCStatement {
        val pos = keyword("return").position
        return LCCtrlReturn(expr.parseNullable(), pos)
    }

    fun breakAndContinue(): LCStatement {
        val pos = pos()
        return if (next().text == "break") LCCtrlBreak(pos) else LCCtrlContinue(pos)
    }
}