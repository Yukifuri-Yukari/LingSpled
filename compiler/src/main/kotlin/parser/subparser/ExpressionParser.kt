package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.conditional.If
import yukifuri.lang.lingspled.compiler.ast.conditional.When
import yukifuri.lang.lingspled.compiler.ast.expr.*
import yukifuri.lang.lingspled.compiler.ast.function.LFunctionCall
import yukifuri.lang.lingspled.compiler.ast.function.LambdaExpr
import yukifuri.lang.lingspled.compiler.ast.literal.*
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet
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

        fun binaryLbp(op: Operator) = 20 - op.priority

        // 后缀/导航操作符（.  ?.  !!  ()  []  as  ++后缀  --后缀）的左绑定力，
        // 高于所有二元操作符（最高 lbp 为 18），保证后缀链先于二元操作被消费。
        private const val POSTFIX_LBP = 100

        // 一元前缀的右绑定力。
        //   > 乘法 lbp(18)：-a * b  →  (-a) * b
        //   < 后缀 lbp(100)：-a.b  →  -(a.b)
        private const val UNARY_RBP = 19
    }

    fun parse(rbp: Int = 0): Expression {
        skipWs()
        var left = nud(next())
        while (hasNext() && lbp() > rbp) {
            left = led(next(), left)
        }
        return left
    }

    private fun lbp(): Int {
        if (!hasNext()) return 0
        val tok = peek()
        return when (tok.type) {
            TokenType.Dot -> POSTFIX_LBP
            TokenType.LParen -> POSTFIX_LBP   // 调用 expr(...)
            TokenType.LBracket -> POSTFIX_LBP   // 索引 expr[i]
            TokenType.Keyword -> when (tok.text) {
                "as" -> POSTFIX_LBP                  // 类型转换（后缀）
                "is" -> binaryLbp(Operator.Is)       // priority 6 → lbp 14
                "in" -> binaryLbp(Operator.In)       // priority 6 → lbp 14
                else -> 0
            }

            TokenType.Question -> questionLbp()
            TokenType.Operator -> when (tok.text) {
                "!!" -> POSTFIX_LBP                  // 非空断言（后缀）
                "++" -> POSTFIX_LBP                  // 后缀自增
                "--" -> POSTFIX_LBP                  // 后缀自减
                // '!' 在中缀位置仅当紧跟 'is'/'in' 时有效（!is / !in）
                "!" -> bangLbp()
                else -> {
                    val op = Operator.fromSymbol(tok.text) ?: return 0
                    // Not / BitNot 为纯前缀，在中缀位置无绑定力
                    if (op == Operator.Not || op == Operator.BitNot) 0
                    else binaryLbp(op)
                }
            }

            else -> 0
        }
    }

    /** '?' 的 lbp：向前看一个 token 决定是 '?.'（POSTFIX_LBP）还是 '?:'（Elvis lbp）还是 0 */
    private fun questionLbp(): Int {
        val snap = parent.ts.snapshot()
        next() // 消费 '?'
        val result = when {
            hasNext() && peek().type == TokenType.Dot -> POSTFIX_LBP
            hasNext() && peek().type == TokenType.Colon -> binaryLbp(Operator.Elvis)
            else -> 0
        }
        parent.ts.restore(snap)
        return result
    }

    /** '!' 的 lbp：向前看，若紧跟 'is'/'in' 则返回比较级 lbp，否则为 0（纯前缀）*/
    private fun bangLbp(): Int {
        val snap = parent.ts.snapshot()
        next() // 消费 '!'
        val result = if (hasNext() && peek().type == TokenType.Keyword &&
            peek().text in setOf("is", "in")
        ) binaryLbp(Operator.NotIs) else 0
        parent.ts.restore(snap)
        return result
    }

    private fun nud(t: Token): Expression = when (t.type) {
        in literals -> nudLiteral(t)

        TokenType.Keyword -> when (t.text) {
            "null" -> NullLiteral().also { it.at(t.row, t.col) }
            "this" -> ThisExpr().also { it.at(t.row, t.col) }
            "if" -> parseIf()
            "when" -> parseWhen()
            else -> {
                diagnostic("Unexpected keyword: '${t.text}'", throws = true); throw IllegalStateException()
            }
        }

        // 前缀运算符：!, ~, ++, --, +, -
        TokenType.Operator -> {
            val op = Operator.fromSymbol(t.text)
            if (op != null && op in Operator.unaryOps) {
                // UNARY_RBP 使后缀（lbp=100）先于一元被消费：-a.b → -(a.b)
                UnaryExpr(op, parse(UNARY_RBP)).also { it.at(t.row, t.col) }
            } else {
                diagnostic("Unexpected operator in prefix position: '${t.text}'", throws = true)
                throw IllegalStateException()
            }
        }

        TokenType.Identifier -> nudIdentifier(t)

        // 括号表达式
        TokenType.LParen -> {
            val expr = parse()
            next(TokenType.RParen)
            expr
        }

        // Lambda / 代码块
        TokenType.LBrace -> {
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

    /**
     * 标识符的 nud：变量读取、普通函数调用、泛型函数调用。
     * t 已被 parse() 消费，此处直接根据后续 token 决定分支，无需 restore。
     */
    private fun nudIdentifier(t: Token): Expression {
        // 普通函数调用 ident(...)
        if (hasNext() && peek().type == TokenType.LParen) {
            next() // 消费 '('
            val arguments = parent.parseList { parse() }
            return LFunctionCall(t.text, arguments).also { it.at(t.row, t.col) }
        }

        // 泛型函数调用 ident<T>(...)
        // parseTypeArgs() 内部有 snapshot/restore：仅当 '<...>' 后紧跟 '(' 时返回非空列表
        if (hasNext() && peek().type == TokenType.Operator && peek().text == "<") {
            val typeArgs = parent.parseTypeArgs()
            if (typeArgs.isNotEmpty()) {
                next(TokenType.LParen) // parseTypeArgs 已验证 '(' 跟在后面
                val arguments = parent.parseList { parse() }
                return LFunctionCall(t.text, arguments, typeArgs).also { it.at(t.row, t.col) }
            }
            // '<' 是比较运算符，fall through 到 VariableGet
        }

        return VariableGet(t.text).also { it.at(t.row, t.col) }
    }

    private fun led(tok: Token, left: Expression): Expression = when (tok.type) {
        TokenType.Dot -> {
            val memberTok = next(TokenType.Identifier)
            val typeArgs = parent.parseTypeArgs()
            if (hasNext() && peek().type == TokenType.LParen) {
                next() // 消费 '('
                MethodCall(left, memberTok.text, parent.parseList { parse() }, typeArgs)
                    .also { it.at(memberTok.row, memberTok.col) }
            } else {
                FieldAccess(left, memberTok.text)
                    .also { it.at(memberTok.row, memberTok.col) }
            }
        }

        TokenType.Question -> {
            if (hasNext() && peek().type == TokenType.Dot) {
                // ?. 安全导航
                next() // 消费 '.'
                val memberTok = next(TokenType.Identifier)
                val typeArgs = parent.parseTypeArgs()
                if (hasNext() && peek().type == TokenType.LParen) {
                    next() // 消费 '('
                    MethodCall(left, memberTok.text, parent.parseList { parse() }, typeArgs, safe = true)
                        .also { it.at(memberTok.row, memberTok.col) }
                } else {
                    FieldAccess(left, memberTok.text, safe = true)
                        .also { it.at(memberTok.row, memberTok.col) }
                }
            } else {
                next() // 消费 ':'
                BinaryExpr(left, Operator.Elvis, parse(binaryLbp(Operator.Elvis)))
                    .also { it.at(tok.row, tok.col) }
            }
        }

        TokenType.LParen ->
            InvokeExpr(left, parent.parseList { parse() })
                .also { it.at(tok.row, tok.col) }

        TokenType.LBracket -> {
            val index = parse()
            next(TokenType.RBracket)
            IndexAccess(left, index).also { it.at(tok.row, tok.col) }
        }

        TokenType.Keyword -> when (tok.text) {
            "as" ->
                AsExpr(left, parent.parseType()).also { it.at(tok.row, tok.col) }

            "is" -> {
                val typeTok = next(TokenType.Identifier)
                BinaryExpr(
                    left, Operator.Is,
                    VariableGet(typeTok.text).also { it.at(typeTok.row, typeTok.col) })
                    .also { it.at(tok.row, tok.col) }
            }

            "in" ->
                BinaryExpr(left, Operator.In, parse(binaryLbp(Operator.In)))
                    .also { it.at(tok.row, tok.col) }

            else -> {
                diagnostic("Unexpected keyword in infix position: '${tok.text}'", throws = true)
                throw IllegalStateException()
            }
        }

        TokenType.Operator -> when (tok.text) {
            // 后缀一元
            "!!" -> UnaryExpr(Operator.NotNull, left).also { it.at(tok.row, tok.col) }
            "++" -> UnaryExpr(Operator.Increment, left).also { it.at(tok.row, tok.col) }
            "--" -> UnaryExpr(Operator.Decrement, left).also { it.at(tok.row, tok.col) }

            // !is / !in 复合操作符（bangLbp() 已确认后跟 'is'/'in'）
            "!" -> {
                val keyword = next(TokenType.Keyword)
                when (keyword.text) {
                    "is" -> {
                        val typeTok = next(TokenType.Identifier)
                        BinaryExpr(
                            left, Operator.NotIs,
                            VariableGet(typeTok.text).also { it.at(typeTok.row, typeTok.col) })
                            .also { it.at(tok.row, tok.col) }
                    }

                    "in" ->
                        BinaryExpr(left, Operator.NotIn, parse(binaryLbp(Operator.NotIn)))
                            .also { it.at(tok.row, tok.col) }

                    else -> {
                        diagnostic("Expected 'is' or 'in' after '!'", throws = true)
                        throw IllegalStateException()
                    }
                }
            }

            // 普通二元操作符（左结合：右侧用相同 lbp，同优先级不再被右侧消费）
            else -> {
                val op = Operator.fromSymbol(tok.text)
                    ?: run {
                        diagnostic(
                            "Unknown operator: '${tok.text}'",
                            throws = true
                        ); throw IllegalStateException()
                    }
                BinaryExpr(left, op, parse(binaryLbp(op))).also { it.at(tok.row, tok.col) }
            }
        }

        else -> {
            diagnostic("Unexpected token in infix position: '${tok.text}'", throws = true)
            throw IllegalStateException()
        }
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
            when (t.type) {
                TokenType.Keyword if t.text == "else" -> {
                    next()
                    next(TokenType.Operator) // '->'
                    elseBranch = parseBranchBody()
                }

                TokenType.Keyword if t.text == "is" -> {
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
                    // 解析分支条件，停在 '->' 前
                    val expr = parse(binaryLbp(Operator.Arrow))
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
            parse(binaryLbp(Operator.Arrow)) // 停在 '->' 前
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
                } else LType.INFER
                arguments.add(Argument(name, type))
                when (peek().type) {
                    TokenType.Comma -> next()
                    TokenType.Operator if peek().text == "->" -> {
                        next(); return arguments
                    }

                    else -> {
                        parent.ts.restore(snap); return emptyList()
                    }
                }
            }
        } catch (_: Exception) {
            parent.ts.restore(snap)
            return emptyList()
        }
    }

    private fun nudLiteral(t: Token): Expression = when (t.type) {
        TokenType.String -> parseStringWithInterpolation(t)
        TokenType.Integer -> IntegerLiteral(Utils.toInt(t.text)).also { it.at(t.row, t.col) }
        TokenType.Decimal -> DecimalLiteral(t.text.toDouble()).also { it.at(t.row, t.col) }
        TokenType.BooleanLiteral -> BooleanLiteral(t.text.toBooleanStrict()).also { it.at(t.row, t.col) }
        else -> throw IllegalStateException("Unexpected literal token: ${t.type} '${t.text}'")
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
                            else if (c == '}') {
                                depth--; if (depth == 0) {
                                    i++; break
                                }
                            }
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
                        parts.add(
                            MethodCall(
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