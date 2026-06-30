package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAArgument
import yukifuri.lang.lingspled.compiler.ast.LABinaryExpr
import yukifuri.lang.lingspled.compiler.ast.LABinaryExpr.BinaryOperator
import yukifuri.lang.lingspled.compiler.ast.LAErrorStatement
import yukifuri.lang.lingspled.compiler.ast.LAExpression
import yukifuri.lang.lingspled.compiler.ast.LAFieldAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAIndexAccessExpr
import yukifuri.lang.lingspled.compiler.ast.LAInvokeExpr
import yukifuri.lang.lingspled.compiler.ast.LALiteral
import yukifuri.lang.lingspled.compiler.ast.LAParameter
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAStringTemplate
import yukifuri.lang.lingspled.compiler.ast.LAUnaryExpr
import yukifuri.lang.lingspled.compiler.ast.control.LACatch
import yukifuri.lang.lingspled.compiler.ast.control.LAIf
import yukifuri.lang.lingspled.compiler.ast.control.LALambda
import yukifuri.lang.lingspled.compiler.ast.control.LAThrow
import yukifuri.lang.lingspled.compiler.ast.control.LATry
import yukifuri.lang.lingspled.compiler.ast.module.LAModule
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.exception.ParsingException
import yukifuri.lang.lingspled.compiler.lexer.Lexer
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.lexer.token.StrSeg
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser
import yukifuri.lang.lingspled.compiler.util.Operator
import yukifuri.libs.compilation.stream.CharStream

class ExpressionParser(parent: Parser) : SubParser(parent) {

    fun parse(minLbp: Int = 0, inModule: Boolean = false): LAExpression {
        val errPos = peek().position
        try {
            skipWs()
            var left = nud(inModule)
            while (true) {
                if (!hasNext() || (inModule && peek(TokenType.NewLine))) break
                val newline = peek(TokenType.NewLine) // skipWs 前左操作数后是否有换行
                skipWs()
                val op = peekBinaryOp() ?: break
                // 中缀 identifier 调用不跨换行：`a\nb` 不是 `a infix b`（符号算符 isInfix=false，仍可跨行）
                if (newline && op.isInfix) break
                if (op.op.lbp <= minLbp) break
                consumeOp(op)
                left = led(left, op, inModule)
            }
            return left
        } catch (_: Throwable) {
            while (hasNext() && peek().type !in safepoints) next()
            next()
        }

        return LAErrorStatement(errPos)
    }

    /** 插值 String token 分段 → [LAStringTemplate]：字面段→字符串字面量，插值段→重 lex + parseExpression。 */
    private fun buildTemplate(segs: List<StrSeg>, pos: Position): LAExpression =
        LAStringTemplate(segs.map { seg ->
            when (seg) {
                is StrSeg.Lit -> LALiteral.LALString(seg.text, pos)
                is StrSeg.Expr -> reparseInterp(seg.source)
            }
        }, pos)

    /** 在插值表达式源码上临时跑一遍 Lexer，换上子 TokenStream 调 [parse]，再还原（Lexer 可随时复用）。
     *  源码补尾换行：裸表达式 lex 后无尾部 token，否则 `primary` 链式访问 peek 会越界（NewLine 作前看缓冲）。 */
    private fun reparseInterp(source: String): LAExpression {
        val sub = Lexer(parent.diag).reset(CharStream("$source\n")).lex().ts
        val saved = parent.ts
        parent.ts = sub
        return try { parse() } finally { parent.ts = saved }
    }

    private fun nud(inModule: Boolean = false): LAExpression {
        val p = peek()
        val node: LAExpression = when (p.type) {
            TokenType.BooleanLiteral ->
                LALiteral.LABoolean(next().text.toBooleanStrict(), p.position)

            TokenType.String -> {
                val t = next()
                t.template?.let { buildTemplate(it, p.position) }
                    ?: LALiteral.LALString(t.text.substring(1, t.text.length - 1), p.position)
            }

            TokenType.Integer -> {
                val t = next()
                t.text.toIntOrNull()?.let { LALiteral.LALInteger(it, p.position) }
                    ?: LALiteral.LALLong(t.text.toLong(), p.position)
            }

            TokenType.Decimal -> {
                val t = next()
                if (t.text.endsWith("F"))
                    LALiteral.LAFloat(t.text.dropLast(1).toFloat(), p.position)
                else
                    LALiteral.LADouble(t.text.toDouble(), p.position)
            }

            TokenType.Keyword -> when (p.text) {
                "null" -> {
                    next(); LALiteral.LALNull(p.position)
                }

                "this" -> {
                    next(); LALiteral.LAThis(p.position)
                }

                "if" -> parseIf()
                "try" -> parseTry()
                "throw" -> {
                    next(); LAThrow(parse(0, inModule), p.position)
                }

                else -> TODO("keyword '${p.text}' in expression")
            }

            TokenType.LParen -> {
                next()
                val inner = parse(0)
                skipWs()
                expect(TokenType.RParen)
                inner
            }

            TokenType.LBrace -> parseLambda()

            TokenType.Identifier -> {
                if (peek(2).type == TokenType.LParen)
                    parseInvoke(LAFieldAccessExpr(null, next().text, p.position))
                else
                    LAFieldAccessExpr(null, next().text, p.position)
            }

            TokenType.Operator -> {
                val op = Operator.from(p.text)
                if (op.nud == 0) throw ParsingException("'${p.text}' not a prefix operator")
                next()
                // 透传 inModule：前缀算符的操作数（`val x = -a` / `!flag` / `throw e`）同样在换行处停住，
                // 否则操作数 parse 吃掉语句边界换行，下一行裸名被误当中缀（中缀 identifier 隐患）。括号内 parse(0) 仍跨行。
                val right = parse(op.nud, inModule)
                LAUnaryExpr(op, right, prefix = true, p.position)
            }

            else -> TODO("unexpected token '$p'")
        }
        return primary(node)
    }
    private fun led(left: LAExpression, op: BinaryOperator, inModule: Boolean = false): LAExpression {
        if (op.op.rbp == 0)
            return LAUnaryExpr(op.op, left, prefix = false, left.position)
        // 透传 inModule：语句级初始化器（如 `val t = n + 1`）的右操作数也要在换行处停住，
        // 否则右操作数 parse 会 skipWs 吃掉作为语句边界的换行，下一行裸名被误当中缀算符（中缀 identifier 隐患）。
        val right = parse(op.op.rbp, inModule)
        return LABinaryExpr(left, op, right, left.position)
    }
    /**
     * There are several cases when need a binary operator.
     *
     * Case 0 - `Not Keyword (!in, !is)`
     * Case 1 - `Operator.? (Simple Operators)`
     * Case 2 - `Keyword (in, is)`
     * Case 3 - `Keyword.As (as)`
     * Case 3 - `Keyword.As Mark.Ques (as?)`
     * Case 4 - `Identifier (Infix)`
     */
    private fun peekBinaryOp(): BinaryOperator? {
        if (!hasNext()) return null

        if (peek("!") && peek(2).text == "in") return BinaryOperator.op(Operator.NotIn)
        if (peek("!") && peek(2).text == "is") return BinaryOperator.op(Operator.IsNot)
        if (peek("in"))                        return BinaryOperator.op(Operator.In)
        if (peek("is"))                        return BinaryOperator.op(Operator.Is)

        if (peek("as") && peek(2).text == "?") return BinaryOperator.op(Operator.SafeAs)
        if (peek("as"))                        return BinaryOperator.op(Operator.As)

        if (peek(TokenType.Identifier))        return BinaryOperator.infix(peek().text)

        if (peek(TokenType.Operator)) {
            val op = runCatching { Operator.from(peek().text) }.getOrNull() ?: return null
            if (op.lbp == 0) return null
            return BinaryOperator.op(op)
        }

        return null
    }
    private fun consumeOp(op: BinaryOperator) {
        if (op.op in setOf(Operator.SafeAs, Operator.NotIn, Operator.IsNot)) {
            next(); next()
        } else next()
    }

    private fun primary(recv: LAExpression): LAExpression {
        var node = recv
        while (true) {
            node = when {
                peek(TokenType.Dot) -> {
                    next()
                    val name = expect(TokenType.Identifier)
                    if (peek(TokenType.LParen)) {
                        val args = parseList(TokenType.LParen, TokenType.RParen) { module.parseArgument() }
                        LAInvokeExpr(LAFieldAccessExpr(node, name.text, name.position), args, node.position)
                    } else {
                        LAFieldAccessExpr(node, name.text, name.position)
                    }
                }
                peek(TokenType.LBracket) -> {
                    next()
                    val idx = parse(0)
                    skipWs()
                    expect(TokenType.RBracket)
                    LAIndexAccessExpr(node, idx, idx.position)
                }
                peek(TokenType.LParen) -> parseInvoke(node)
                // trailing lambda：同行紧跟 `{` 才吸收为末位实参；换行时当前是 NewLine，
                // 不匹配 → break，`{` 留给下一条语句成为独立 lambda（a(...)\n{} = invoke + lambda）
                peek(TokenType.LBrace) -> {
                    val lambda = parseLambda()
                    when (node) {
                        is LAInvokeExpr -> LAInvokeExpr(node.receiver, node.arg + LAArgument(null, lambda), node.position)
                        else -> LAInvokeExpr(node, listOf(LAArgument(null, lambda)), node.position)
                    }
                }
                else -> break
            }
        }
        return node
    }

    /** `if (cond) then [else elseBranch]`；else-if 经 [ModuleParser.parseBody] 的 fallback 自然嵌套。 */
    private fun parseIf(): LAIf {
        val pos = next().position // if
        skipWs()
        expect(TokenType.LParen)
        val cond = parse(0)
        skipWs()
        expect(TokenType.RParen)
        val then = module.parseBody(fallback = true)

        val elseBranch = run {
            val s = parent.ts.snapshot()
            skipWs()
            if (hasNext() && peek("else")) {
                next()
                module.parseBody(fallback = true)
            } else {
                parent.ts.restore(s)
                null
            }
        }

        return LAIf(cond, then, elseBranch, pos)
    }

    /** `try body (catch (name: Type) body)* (finally body)?`。catch/finally lex 成 Identifier。 */
    private fun parseTry(): LATry {
        val pos = next().position // try
        val body = module.parseBody()

        val catches = mutableListOf<LACatch>()
        while (true) {
            val s = parent.ts.snapshot()
            skipWs()
            if (!hasNext() || !peek("catch")) {
                parent.ts.restore(s)
                break
            }
            val cpos = next().position
            expect(TokenType.LParen)
            val name = expectId().text
            expect(TokenType.Colon)
            val type = parseTypeRef()
            skipWs()
            expect(TokenType.RParen)
            catches.add(LACatch(name, type, module.parseBody(), cpos))
        }

        val finallyBlock = run {
            val s = parent.ts.snapshot()
            skipWs()
            if (hasNext() && peek("finally")) {
                next()
                module.parseBody()
            } else {
                parent.ts.restore(s)
                null
            }
        }

        return LATry(body, catches, finallyBlock, pos)
    }

    fun parseInvoke(receiver: LAExpression): LAStatement {
        val args = parseList(
            TokenType.LParen, TokenType.RParen, TokenType.Comma
        ) { module.parseArgument() }

        return LAInvokeExpr(receiver, args, receiver.position)
    }

    /** `{ [params ->] statements }`；`{` 由调用方（nud / trailing）前看确认。 */
    private fun parseLambda(): LALambda {
        val pos = expect(TokenType.LBrace).position
        val params = parseLambdaParams()

        val statements = mutableListOf<LAStatement>()
        skipWs()
        while (hasNext() && !peek(TokenType.RBrace)) {
            statements.add(module.parse())
            skipWs()
        }
        expect(TokenType.RBrace)
        return LALambda(params, LAModule(statements, pos), pos)
    }

    /**
     * 探测参数头 `id (: Type)? (, id (: Type)?)* ->`。
     * 不是参数头（如 `{ println(x) }`、`{ it + 1 }`）则 snapshot/restore 回退为无参（隐式 it）。
     */
    private fun parseLambdaParams(): List<LAParameter> {
        val s = parent.ts.snapshot()
        skipWs()

        // { -> body }：显式空参数头
        if (peek("->", TokenType.Operator)) {
            next()
            return emptyList()
        }

        val params = mutableListOf<LAParameter>()
        while (peek(TokenType.Identifier)) {
            val name = next().text
            skipWs()
            val type =
                if (tryConsume(TokenType.Colon)) {
                    skipWs()
                    runCatching { parseTypeRef() }.getOrElse {
                        parent.ts.restore(s)
                        return emptyList()
                    }
                } else LTypeRef.infer
            params.add(LAParameter(name, type))
            skipWs()
            if (!tryConsume(TokenType.Comma)) break
            skipWs()
        }

        if (params.isNotEmpty() && peek("->", TokenType.Operator)) {
            next()
            return params
        }
        parent.ts.restore(s)
        return emptyList()
    }
}