package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.expr.BinaryExpr
import yukifuri.lang.lingspled.compiler.ast.expr.FieldAccess
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAccess
import yukifuri.lang.lingspled.compiler.ast.expr.InvokeExpr
import yukifuri.lang.lingspled.compiler.ast.expr.MethodCall
import yukifuri.lang.lingspled.compiler.ast.expr.ThisExpr
import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.conditional.If
import yukifuri.lang.lingspled.compiler.ast.conditional.When
import yukifuri.lang.lingspled.compiler.ast.expr.AsExpr
import yukifuri.lang.lingspled.compiler.ast.expr.UnaryExpr
import yukifuri.lang.lingspled.compiler.ast.literal.BooleanLiteral
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet
import yukifuri.lang.lingspled.compiler.ast.literal.DecimalLiteral
import yukifuri.lang.lingspled.compiler.ast.literal.IntegerLiteral
import yukifuri.lang.lingspled.compiler.ast.literal.Literal
import yukifuri.lang.lingspled.compiler.ast.literal.NullLiteral
import yukifuri.lang.lingspled.compiler.ast.literal.StringLiteral
import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.function.LambdaExpr
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.lexer.token.Token
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Utils
import yukifuri.libs.compilation.stream.CharStream

class ExpressionParser(parent: Parser) : SubParser(parent) {
    companion object {
        val literals = setOf(
            TokenType.String,
            TokenType.Integer,
            TokenType.Decimal,
            TokenType.BooleanLiteral
        )
    }

    fun parse(minPriority: Int = Int.MAX_VALUE): Expression {
        var left = primary()

        while (hasNext()) {
            val opToken = peek()

            // !is / !in: two-token compound operators (Operator '!' + Keyword 'is'/'in')
            if (opToken.type == TokenType.Operator && opToken.text == "!") {
                val snap = parent.ts.snapshot()
                next() // tentatively consume '!'
                val compoundOp = if (hasNext() && peek().type == TokenType.Keyword) {
                    when (peek().text) {
                        "is" -> Operator.NotIs
                        "in" -> Operator.NotIn
                        else -> null
                    }
                } else null
                if (compoundOp != null && compoundOp.priority < minPriority) {
                    next() // consume 'is' or 'in'
                    val right = if (compoundOp == Operator.NotIs) {
                        val typeTok = next(TokenType.Identifier)
                        VariableGet(typeTok.text).also { it.at(typeTok.row, typeTok.col) }
                    } else {
                        parse(compoundOp.priority)
                    }
                    left = BinaryExpr(left, compoundOp, right).also { it.at(opToken.row, opToken.col) }
                    continue
                }
                parent.ts.restore(snap)
                break
            }

            // ?: Elvis operator (two-token: Question + Colon)
            if (opToken.type == TokenType.Question) {
                val snap = parent.ts.snapshot()
                next() // tentatively consume '?'
                if (hasNext() && peek().type == TokenType.Colon && Operator.Elvis.priority < minPriority) {
                    next() // consume ':'
                    val right = parse(Operator.Elvis.priority)
                    left = BinaryExpr(left, Operator.Elvis, right).also { it.at(opToken.row, opToken.col) }
                    continue
                }
                parent.ts.restore(snap)
                break
            }

            // Standard single-token binary operators
            val operator = Operator.fromSymbol(opToken.text) ?: break
            val priority = operator.priority
            if (opToken.type != TokenType.Operator && opToken.type != TokenType.Keyword) break
            if (priority >= minPriority) break

            next() // consume operator
            val right = if (operator == Operator.Is) {
                // right-hand side of 'is' is a type name, not a value expression
                val typeTok = next(TokenType.Identifier)
                VariableGet(typeTok.text).also { it.at(typeTok.row, typeTok.col) }
            } else {
                parse(priority)
            }
            left = BinaryExpr(left, operator, right).also { it.at(opToken.row, opToken.col) }
        }

        return left
    }

    fun primary(): Expression {
        var expr = primaryAtom()
        while (hasNext()) {
            expr = when (peek().type) {
                TokenType.Dot -> {
                    next() // consume '.'
                    val memberTok = next(TokenType.Identifier)
                    val typeArgs = parent.parseTypeArgs()
                    if (hasNext() && peek().type == TokenType.LParen) {
                        next() // consume '('
                        MethodCall(expr, memberTok.text, parent.parseList { parent.expr.parse() }, typeArgs).also { it.at(memberTok.row, memberTok.col) }
                    } else
                        FieldAccess(expr, memberTok.text).also { it.at(memberTok.row, memberTok.col) }
                }
                TokenType.Question -> {
                    val snap = parent.ts.snapshot()
                    next() // tentatively consume '?'
                    if (hasNext() && peek().type == TokenType.Dot) {
                        next() // consume '.'
                        val memberTok = next(TokenType.Identifier)
                        val typeArgs = parent.parseTypeArgs()
                        if (hasNext() && peek().type == TokenType.LParen) {
                            next() // consume '('
                            MethodCall(expr, memberTok.text, parent.parseList { parent.expr.parse() }, typeArgs, safe = true).also { it.at(memberTok.row, memberTok.col) }
                        } else
                            FieldAccess(expr, memberTok.text, safe = true).also { it.at(memberTok.row, memberTok.col) }
                    } else {
                        parent.ts.restore(snap)
                        break
                    }
                }
                TokenType.Operator if peek().text == "!!" -> {
                    val bangTok = next()
                    UnaryExpr(Operator.NotNull, expr).also { it.at(bangTok.row, bangTok.col) }
                }
                TokenType.LParen -> {
                    val lparenTok = next() // consume '('
                    InvokeExpr(expr, parent.parseList { parent.expr.parse() }).also { it.at(lparenTok.row, lparenTok.col) }
                }
                TokenType.LBracket -> {
                    val lbracketTok = next() // consume '['
                    val index = parse()
                    next(TokenType.RBracket)
                    IndexAccess(expr, index).also { it.at(lbracketTok.row, lbracketTok.col) }
                }
                TokenType.Keyword if peek().text == "as" -> {
                    val asTok = next()
                    val targetType = parent.parseType()
                    AsExpr(expr, targetType).also { it.at(asTok.row, asTok.col) }
                }
                else -> break
            }
        }
        return expr
    }

    private fun primaryAtom(): Expression {
        skipWs()
        val t = peek()
        return when (t.type) {
            in literals -> parseLiteral()
            // UnaryExpr(Front)
            TokenType.Operator if Operator.fromSymbol(t.text) in Operator.unaryOps -> {
                val op = Operator.fromSymbol(next().text)
                UnaryExpr(op!!, primary()).also { it.at(t.row, t.col) }
            }
            TokenType.Keyword if t.text == "null" -> { next(); NullLiteral().also { it.at(t.row, t.col) } }
            TokenType.Keyword if t.text == "this" -> { next(); ThisExpr().also { it.at(t.row, t.col) } }
            TokenType.Keyword if t.text in setOf("if", "when") -> parseConditional()
            // Function Calls, VariableGet, UnaryExpr(Back)
            TokenType.Identifier -> {
                val s = parent.ts.snapshot()
                val name = next()
                if (hasNext() && peek().type == TokenType.LParen) {
                    // Unambiguous function call / 无歧义函数调用
                    parent.ts.restore(s)
                    return inModule.parseFunctionCall()
                }
                if (hasNext() && peek().type == TokenType.Operator && peek().text == "<") {
                    // Speculatively check for generic call: name<TypeArgs>(...).
                    // parseTypeArgs() uses its own snapshot/restore and only returns
                    // non-empty when '<...>' is followed by '(', so 'v < lo' falls through.
                    // 投机性检查泛型调用：parseTypeArgs() 内部自带 snapshot/restore，
                    // 只有 '<...>' 后紧跟 '(' 才返回非空，'v < lo' 会原样回退。
                    val typeArgs = parent.parseTypeArgs()
                    if (typeArgs.isNotEmpty()) {
                        parent.ts.restore(s)
                        return inModule.parseFunctionCall()
                    }
                    // '<' is a comparison operator — fall through to VariableGet.
                    // '<' 是比较运算符，继续走 VariableGet 路径。
                }
                if (hasNext() &&
                    peek().type == TokenType.Operator &&
                    Operator.fromSymbol(peek().text) in setOf(Operator.Increment, Operator.Decrement)
                ) {
                    val op = Operator.fromSymbol(next().text)
                    return UnaryExpr(op!!, VariableGet(name.text).also { it.at(name.row, name.col) }).also { it.at(name.row, name.col) }
                }
                VariableGet(name.text).also { it.at(name.row, name.col) }
            }
            // Extract Parens
            TokenType.LParen -> {
                next()
                val expr = parse()
                next(TokenType.RParen)
                expr
            }

            TokenType.LBrace -> {
                next()
                val arguments = tryParseLambdaArguments()
                val builder = Module.Builder()
                inModule.parse(builder, enableExpressionParsing = true)
                next(TokenType.RBrace)
                LambdaExpr(arguments, builder.build()).also { it.at(t.row, t.col) }
            }

            else -> {
                diagnostic("Unexpected token: '${t.text}'", throws = true)
                throw IllegalStateException()
            }
        }
    }

    fun parseConditional(): Statement {
        if (peek().text == "if") return parseIf()
        return parseWhen()
    }

    fun parseWhen(): When {
        val whenTok = next(TokenType.Keyword, "when")

        val subject = if (peek().type == TokenType.LParen) {
            next()
            val e = parse()
            next(TokenType.RParen)
            e
        } else null

        next(TokenType.LBrace)

        val branches = mutableListOf<When.Branch>()
        var elseBranch: Module? = null

        while (hasNext() && peek().type != TokenType.RBrace) {
            skipWs()
            if (peek().type == TokenType.RBrace) break
            val t = peek()
            when {
                t.type == TokenType.Keyword && t.text == "else" -> {
                    next()
                    next(TokenType.Operator) // '->'
                    elseBranch = parseBranchBody()
                }
                t.type == TokenType.Keyword && t.text == "is" -> {
                    next()
                    val typeName = next(TokenType.Identifier).text
                    val destructured = if (peek().type == TokenType.LParen) {
                        next()
                        val names = mutableListOf<String>()
                        while (peek().type != TokenType.RParen) {
                            names.add(next(TokenType.Identifier).text)
                            if (peek().type == TokenType.Comma) next()
                        }
                        next(TokenType.RParen)
                        names
                    } else emptyList()
                    val guard = parseWhenGuard()
                    next(TokenType.Operator) // '->'
                    branches.add(When.TypeBranch(typeName, destructured, guard, parseBranchBody()))
                }
                else -> {
                    val expr = parse(Operator.Arrow.priority)
                    val guard = parseWhenGuard()
                    next(TokenType.Operator) // '->'
                    branches.add(When.ExprBranch(expr, guard, parseBranchBody()))
                }
            }
            skipWs()
        }

        next(TokenType.RBrace)
        return When(subject, branches, elseBranch).also { it.at(whenTok.row, whenTok.col) }
    }

    private fun parseWhenGuard(): Expression? =
        if (peek().type == TokenType.Keyword && peek().text == "if") {
            next()
            parse(Operator.Arrow.priority)
        } else null

    private fun parseBranchBody(): Module =
        if (peek().type == TokenType.LBrace) parent.parseBlock()
        else Module(listOf(inModule.once(enableExpressionParsing = true)))

    fun parseIf(): If {
        val ifTok = next(TokenType.Keyword, "if")
        next(TokenType.LParen)
        val expr = parse()
        next(TokenType.RParen)

        val then = parent.parseBlock()

        val els = run {
            if (peek().type != TokenType.Keyword || peek().text != "else")
                return@run null
            next()
            parent.parseBlock()
        }

        return If(expr, then, els).also { it.at(ifTok.row, ifTok.col) }
    }

    private fun tryParseLambdaArguments(): List<Argument> {
        val snap = parent.ts.snapshot()
        val arguments = mutableListOf<Argument>()
        try {
            // 显式零参 { -> 函数体 }
            if (peek().type == TokenType.Operator && peek().text == "->") {
                next()
                return emptyList()
            }
            // 尝试解析 name (, name)* ->
            while (true) {
                if (peek().type != TokenType.Identifier) {
                    parent.ts.restore(snap); return emptyList()
                }
                val name = next().text
                val type = if (peek().type == TokenType.Colon) {
                    next()
                    parent.parseType()
                } else {
                    LType.INFER
                }
                arguments.add(Argument(name, type))
                when (peek().type) {
                    TokenType.Comma -> next()
                    TokenType.Operator if peek().text == "->" -> {
                        next(); return arguments
                    }
                    else -> { parent.ts.restore(snap); return emptyList() }
                }
            }
        } catch (_: Exception) {
            parent.ts.restore(snap)
            return emptyList()
        }
    }

    fun parseLiteral(): Expression {
        val t = next()
        return when (t.type) {
            TokenType.String -> parseStringWithInterpolation(t)
            TokenType.Integer -> IntegerLiteral(Utils.toInt(t.text)).also { it.at(t.row, t.col) }
            TokenType.Decimal -> DecimalLiteral(t.text.toDouble()).also { it.at(t.row, t.col) }
            TokenType.BooleanLiteral -> BooleanLiteral(t.text.toBooleanStrict()).also { it.at(t.row, t.col) }
            else -> throw IllegalStateException("Unexpected token: ${t.type} '${t.text}'")
        }
    }

    private fun parseStringWithInterpolation(t: Token): Expression {
        val content = t.text
        if ('$' !in content) return StringLiteral(content).also { it.at(t.row, t.col) }

        val parts = mutableListOf<Expression>()
        var i = 0
        val current = StringBuilder()

        while (i < content.length) {
            val ch = content[i]
            if (ch == '$' && i + 1 < content.length) {
                // flush pending literal
                if (current.isNotEmpty()) {
                    parts.add(StringLiteral(current.toString()).also { it.at(t.row, t.col) })
                    current.clear()
                }
                i++
                when {
                    content[i] == '{' -> {
                        // ${expr}: 收集到匹配的 }
                        i++ // skip '{'
                        var depth = 1
                        val exprText = StringBuilder()
                        while (i < content.length && depth > 0) {
                            val c = content[i]
                            if (c == '{') depth++
                            else if (c == '}') { depth--; if (depth == 0) { i++; break } }
                            exprText.append(c)
                            i++
                        }
                        val subExpr = parseEmbeddedExpr(exprText.toString())
                        parts.add(MethodCall(subExpr, "toString", emptyList()).also { it.at(t.row, t.col) })
                    }
                    content[i].isLetter() || content[i] == '_' -> {
                        // $ident
                        val sb = StringBuilder()
                        while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '_'))
                            sb.append(content[i++])
                        parts.add(MethodCall(
                            VariableGet(sb.toString()).also { it.at(t.row, t.col) },
                            "toString", emptyList()
                        ).also { it.at(t.row, t.col) })
                    }
                    else -> current.append('$') // 孤立 $，保留原样
                }
            } else {
                current.append(ch)
                i++
            }
        }

        if (current.isNotEmpty())
            parts.add(StringLiteral(current.toString()).also { it.at(t.row, t.col) })

        if (parts.isEmpty()) return StringLiteral("").also { it.at(t.row, t.col) }
        if (parts.size == 1) return parts[0]

        return parts.reduce { acc, expr ->
            BinaryExpr(acc, Operator.Add, expr).also { it.at(t.row, t.col) }
        }
    }

    private fun parseEmbeddedExpr(source: String): Expression {
        val lexer = Lexer(CharStream(source), parent.diagnostics)
        lexer.lex()
        val savedTs = parent.ts
        parent.ts = lexer.ts
        val result = parse()
        parent.ts = savedTs
        return result
    }
}