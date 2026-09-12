package yukifuri.lang.lingspled.compiler.front.parser.subparser

import yukifuri.lang.lingspled.compiler.front.Operator
import yukifuri.lang.lingspled.compiler.front.struct.*
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.Parser

class ExpressionParser(parent: Parser) : SubParser(parent) {

    fun parseNullable(minLbp: Int = 0): LCExpression? {
        if (peekKeyword("if")) {
            return module.ifStmt()
        }
        if (peek(TokenType.LParen)) {
            next()
            return postfix(
                LCParenGroup(
                    parse().also { expect(TokenType.RParen) }
                )
            )
        }

        var left = primary() ?: return null

        while (hasNext()) {
            if (peekRaw().type == TokenType.NewLine) break

            val s = parent.ts.snapshot()
            val op = peekOperator()
            if (op == null || op.operator.lbp < minLbp) {
                parent.ts.restore(s)
                break
            }
            next()

            left = led(left, op)
        }

        return left
    }

    fun parse(minLbp: Int = 0): LCExpression {
        return parseNullable(minLbp)!!
    }

    private fun postfix(recv: LCExpression): LCExpression {
        var node = recv
        while (hasNext()) {
            val s = parent.ts.snapshot()
            val crossedNewline = run {
                var crossed = false
                while (peekRaw().type in TokenType.whitespaces) {
                    if (peekRaw().type == TokenType.NewLine)
                        crossed = true
                    nextRaw()
                }
                parent.ts.restore(s)
                crossed
            }

            node = when {
                !crossedNewline && (peek(TokenType.LParen) || peek(TokenType.LBrace)) -> {
                    if (peek(TokenType.LBrace))
                        LCExprInvoke(node, listOf(), parseLambda())
                    else
                        LCExprInvoke(
                            node,
                            if (peek(TokenType.LParen)) parseList(
                                TokenType.LParen, TokenType.RParen,
                                TokenType.Comma, ::argument
                            ) else listOf(),
                            if (peek(TokenType.LBrace))
                                parseLambda()
                            else null
                        )
                }

                peek(TokenType.LBracket) -> {
                    next()
                    LCExprIndex(node, expr.parse().also { expect(TokenType.RBracket) })
                }

                peek(TokenType.Dot) -> {
                    next()
                    val pos = pos()
                    LCExprMemberAccess(node, ident(), pos)
                }

                peek().text in setOf("++", "--", "!") -> {
                    val op = when (next().text) {
                        "++" -> Operator.Inc
                        "--" -> Operator.Dec
                        "!" -> Operator.Factorial
                        else -> throw IllegalStateException("-")
                    }
                    LCExprUnary(
                        node,
                        op,
                        false
                    )
                }

                else -> {
                    parent.ts.restore(s)
                    break
                }
            }
        }
        return node
    }

    private fun primary(): LCExpression? {
        val possiblePrefixOp = peekOperator(atStart = true)
        if (possiblePrefixOp != null && possiblePrefixOp.operator in setOf(
                Operator.Add, Operator.Sub, Operator.Not,
                Operator.Inc, Operator.Dec
            )
        ) {
            next()
            val expr = parse(possiblePrefixOp.operator.nud)
            return LCExprUnary(expr, possiblePrefixOp.operator, true)
        }

        val n = nud()

        if (n == null) {
            return n
        }

        return postfix(n)
    }

    private fun nud(): LCExpression? {
        return when {
            peek(TokenType.LBrace) -> parseLambda()
            peek(TokenType.Integer) -> {
                val pos = pos()
                if (peek().text.endsWith("L"))
                    LCLiteral.LongLit(next().text.dropLast(1).toLong(), pos)
                else
                    LCLiteral.IntLit(next().text.toInt(), pos)
            }

            peek(TokenType.Decimal) -> {
                val pos = pos()
                if (peek().text.endsWith("F"))
                    LCLiteral.FloatLit(next().text.dropLast(1).toFloat(), pos)
                else
                    LCLiteral.DoubleLit(next().text.toDouble(), pos)
            }

            peek(TokenType.String) -> {
                val pos = pos()
                val s = next().text
                if (s.startsWith("\"\"\"") && s.endsWith("\"\"\""))
                    LCLiteral.StringLit(s.drop(3).dropLast(3), pos)
                else
                    LCLiteral.StringLit(s.drop(1).dropLast(1), pos)
            }

            peek(TokenType.Character) -> {
                val pos = pos()
                LCLiteral.CharLit(next().text.drop(1).dropLast(1)[0], pos)
            }

            peek(TokenType.Keyword) -> {
                val pos = pos()
                when (val t = next().text) {
                    "true" -> LCLiteral.BoolLit(true, pos)
                    "false" -> LCLiteral.BoolLit(false, pos)
                    "null" -> LCLiteral.NullLit(pos)
                    else -> {
                        diag("Stray keyword \"$t\" in expression", pos)
                        throw IllegalStateException()
                    }
                }
            }

            peek(TokenType.Identifier) -> {
                val pos = pos()
                LCExprVariableGet(ident(), pos)
            }

            else -> null
        }
    }


    private fun led(left: LCExpression, op: LCOperator) = when (op.operator) {
        Operator.As, Operator.SafeAs -> {
            // After these come TypeRefs
            LCExprAs(left, typeRef(), op.operator == Operator.SafeAs)
        }

        Operator.Elvis -> {
            val right = when (peek().text) {
                "continue" -> LCCtrlContinue(next().position)
                "break" -> LCCtrlBreak(next().position)
                "return" -> module.retStmt()
                else -> parse(op.operator.rbp)
            }
            LCExprBinary(left, op, right)
        }

        else -> {
            val right = parse(op.operator.rbp)
            LCExprBinary(left, op, right)
        }
    }

    private fun peekOperator(atStart: Boolean = false): LCOperator? {
        if (peek(TokenType.Identifier) && !atStart)
            return LCOperator(peek().text, Operator.Infix)
        if (peek(TokenType.Operator))
            return LCOperator(null, Operator.bySymbol(peek().text)!!)
        return null
    }
}