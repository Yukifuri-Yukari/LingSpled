package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAArgument
import yukifuri.lang.lingspled.compiler.ast.LAExprStatement
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Modifiers
import yukifuri.lang.lingspled.compiler.util.cast

class ModuleParser(parent: Parser) : SubParser(parent) {

    fun parse(): LAStatement {
        skipWs()
        val annotations = parseAnnotations()
        skipWs()
        return when {
            peek("fun") -> functionDecl(annotations)
            peek("val") || peek("var") -> variableDecl(annotations)
            peek("return") -> {
                val pos = keyword("return").position
                val expr = expr.parse(inModule = true)
                LAFunction.LAReturnStmt(expr, pos)
            } /*
            peek("if") -> ifStmt()
            peek("while") -> whileStmt()
            peek("for") -> forStmt()
            peek("when") -> whenStmt() */
            else -> LAExprStatement(expr.parse(inModule = true))
        }.also { skipStmtEnd() }
    }

    fun functionDecl(
        annotations: List<LAAnnotation> = listOf(),
        modifiers: Pair<Modifiers.Access, List<Modifiers.ModifierType>> =
            Modifiers.Access.Local to listOf()
    ): LAFunction {
        val pos = keyword("fun").position
        val tp = parseTypeParams()

        val receiver = run {
            val s = parent.ts.snapshot()
            try {
                val t = parseTypeRef()
                if (peek(TokenType.Dot)) {
                    next()
                    t
                } else {
                    parent.ts.restore(s)
                    null
                }
            } catch (_: Exception) {
                parent.ts.restore(s)
                null
            }
        }

        val name = expectId().text
        val params = parseList(
            TokenType.LParen, TokenType.RParen
        ) { parseParameter() }

        val ret = if (peek(TokenType.Colon)) {
            next()
            parseTypeRef()
        } else LTypeRef.unit

        val resolvedTp = parseWhereClause(tp)

        val body = if (peek(TokenType.LBrace)) parseBody() else null

        return LAFunction(
            annotations, modifiers.first, modifiers.second.cast(), 
            resolvedTp, receiver, name, params, ret, body, pos)
    }

    fun parseParameter(): LAParameter {
        val name = expectId().text
        expect(TokenType.Colon)
        val type = parseTypeRef()
        return LAParameter(name, type)
    }

    fun parseArgument(): LAArgument {
        val name = run {
            skipWs()
            val s = parent.ts.snapshot()
            if (hasNext()) next()
            skipWs()
            if (hasNext() && peek("=", TokenType.Operator)) {
                parent.ts.restore(s)
                return@run expectId().text.also { expect(TokenType.Operator, "=") }
            }
            parent.ts.restore(s)
            null
        }

        val value = expr.parse()

        return LAArgument(name, value)
    }

    fun variableDecl(
        annotations: List<LAAnnotation> = listOf(),
        modifiers: Pair<Modifiers.Access, List<Modifiers.ModifierType>> =
            Modifiers.Access.Local to listOf()
    ): LAStatement {
        val pos = peek().position
        val mutable = next().text == "var"
        val name = expectId().text
        val type = if (peek(TokenType.Colon)) {
            next()
            parseTypeRef()
        } else null
        val init = if (peek("=", TokenType.Operator)) {
            next()
            expr.parse(inModule = true)
        } else null
        return LAVariableDecl(annotations, modifiers.first, modifiers.second.cast(), mutable, name, type, init, pos)
    }

    fun parseBody(fallback: Boolean = false): LAModule {
        if (peek(TokenType.LBrace)) {
            val pos = next().position
            val statements = mutableListOf<LAStatement>()

            while (hasNext() && !peek(TokenType.RBrace))
                statements.add(parse())

            next()

            return LAModule(statements, pos)
        } else {
            if (!fallback)
                diagnostic("Required a block with {} surrounding")
            val pos = peek().position
            return LAModule(listOf(parse()), pos)
        }
    }
}