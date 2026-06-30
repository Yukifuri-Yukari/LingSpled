package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAArgument
import yukifuri.lang.lingspled.compiler.ast.LAErrorStatement
import yukifuri.lang.lingspled.compiler.ast.LAExprStatement
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.cls.LAAnnotation
import yukifuri.lang.lingspled.compiler.ast.control.LABreak
import yukifuri.lang.lingspled.compiler.ast.control.LAContinue
import yukifuri.lang.lingspled.compiler.ast.control.LADoWhile
import yukifuri.lang.lingspled.compiler.ast.control.LAFor
import yukifuri.lang.lingspled.compiler.ast.control.LAWhile
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Modifiers
import yukifuri.lang.lingspled.compiler.util.resolve

class ModuleParser(parent: Parser) : SubParser(parent) {

    fun parse(consumeEnd: Boolean = true): LAStatement {
        val errPos = peek().position
        try {
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
                }

                peek("while") -> whileStmt()
                peek("do") && peek(2).type == TokenType.LBrace -> doWhileStmt()
                peek("for") -> forStmt()
                peek("break") -> LABreak(keyword("break").position)
                peek("continue") -> LAContinue(keyword("continue").position)
                else -> LAExprStatement(expr.parse(inModule = true))
            }.also { if (consumeEnd) skipStmtEnd() }
        } catch (_: Throwable) {
            while (hasNext() && peek().type !in safepoints) next()
            next()
        }

        return LAErrorStatement(errPos)
    }

    fun functionDecl(
        annotations: List<LAAnnotation> = listOf(),
        modifiers: Pair<Modifiers.Access, List<String>> =
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

        val explicitRet = if (peek(TokenType.Colon)) {
            next()
            parseTypeRef()
        } else null

        val resolvedTp = parseWhereClause(tp)

        val isExprBody = peek("=", TokenType.Operator)

        val body = when {
            peek(TokenType.LBrace) -> parseBody()
            // 表达式体：fun f() = expr 脱糖为单条 return；无显式返回类型时用 infer 占位
            isExprBody -> {
                val eq = next().position
                LAModule(listOf(LAFunction.LAReturnStmt(expr.parse(inModule = true), eq)), eq)
            }
            else -> null
        }

        val ret = explicitRet ?: if (isExprBody) LTypeRef.infer else LTypeRef.unit

        return LAFunction(
            annotations, modifiers.first, modifiers.second.resolve(Modifiers.Function.map, "function"),
            resolvedTp, receiver, name, params, ret, body, pos)
    }

    fun parseParameter(): LAParameter {
        skipWs()
        val vararg = tryConsume(TokenType.Identifier, "vararg") // 软关键字，lex 成 Identifier
        val name = expectId().text
        expect(TokenType.Colon)
        val type = parseTypeRef()
        val default = if (peek("=", TokenType.Operator)) {
            next()
            expr.parse(inModule = true)
        } else null
        return LAParameter(name, type, vararg, default)
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
        modifiers: Pair<Modifiers.Access, List<String>> =
            Modifiers.Access.Local to listOf()
    ): LAStatement {
        val pos = peek().position
        val mutable = next().text == "var"
        val name = expectId().text
        val type = if (peek(TokenType.Colon)) {
            next()
            parseTypeRef()
        } else null

        val delegator = if (peek("by")) {
            next()
            expr.parse(inModule = true)
        } else null

        val init = if (peek("=", TokenType.Operator)) {
            next()
            expr.parse(inModule = true)
        } else null
        return LAVariableDecl(
            annotations, modifiers.first, modifiers.second.resolve(Modifiers.Property.map, "property"),
            mutable, name, type, init, delegator, pos)
    }

    fun whileStmt(): LAWhile {
        val pos = keyword("while").position
        skipWs()
        expect(TokenType.LParen)
        val cond = expr.parse()
        skipWs()
        expect(TokenType.RParen)
        return LAWhile(cond, parseBody(fallback = true), pos)
    }

    fun doWhileStmt(): LADoWhile {
        val pos = next().position
        val body = parseBody()
        skipWs()
        keyword("while")
        skipWs()
        expect(TokenType.LParen)
        val cond = expr.parse()
        skipWs()
        expect(TokenType.RParen)
        return LADoWhile(body, cond, pos)
    }

    fun forStmt(): LAFor {
        val pos = keyword("for").position
        skipWs()
        expect(TokenType.LParen)
        skipWs()
        val variable = expectId().text
        val type = if (peek(TokenType.Colon)) {
            next()
            parseTypeRef()
        } else null
        skipWs()
        keyword("in")
        val iterable = expr.parse()
        skipWs()
        expect(TokenType.RParen)
        return LAFor(variable, type, iterable, parseBody(fallback = true), pos)
    }

    fun parseBody(fallback: Boolean = false): LAModule {
        if (peek(TokenType.LBrace)) {
            val pos = next().position
            val statements = mutableListOf<LAStatement>()

            // skipWs 必须在 RBrace 检查之前：语句末 skipStmtEnd 只吞一个换行，块内空行/多换行会让
            // `!peek(RBrace)` 误为真 → parse() 落到 `}` 上 → nud 抛 TODO("unexpected token '}'")。
            while (hasNext()) {
                skipWs()
                if (peek(TokenType.RBrace)) break
                statements.add(parse())
            }

            next()

            return LAModule(statements, pos)
        } else {
            if (!fallback)
                diagnostic("Required a block with {} surrounding")
            val pos = peek().position
            // 单语句体不吞尾部换行，留给外层 expr.parse 作语句边界
            return LAModule(listOf(parse(consumeEnd = false)), pos)
        }
    }
}