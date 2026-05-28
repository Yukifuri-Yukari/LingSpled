package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.base.StatementWrapper
import yukifuri.lang.lingspled.compiler.ast.conditional.*
import yukifuri.lang.lingspled.compiler.ast.expr.FieldAccess
import yukifuri.lang.lingspled.compiler.ast.expr.FieldAssign
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAccess
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAssign
import yukifuri.lang.lingspled.compiler.ast.expr.UnaryExpr
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.function.LFunctionCall
import yukifuri.lang.lingspled.compiler.ast.function.Return
import yukifuri.lang.lingspled.compiler.ast.module.Annotation
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.ast.util.Operator
import yukifuri.lang.lingspled.compiler.ast.variable.VariableAssign
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser

class InModuleParser(parent: Parser) : SubParser(parent) {
    fun parseWithBraces(ast: Module.Builder): Module.Builder {
        next(TokenType.LBrace)
        val res = parse(ast)
        next(TokenType.RBrace)
        return res
    }

    fun parse(ast: Module.Builder, enableExpressionParsing: Boolean = false): Module.Builder {
        while (hasNext() && peek().type != TokenType.RBrace) {
            if (peek().type == TokenType.NewLine) {
                next()
                continue
            }
            if (peek().type == TokenType.Semicolon)
                diagnostic("Unexpected statement terminator")
            ast.add(once(enableExpressionParsing))
            skipStmtEnd()
        }
        return ast
    }

    fun once(enableExpressionParsing: Boolean = false): Statement {
        skipWs()
        val t = peek()
        return when (t.type) {
            TokenType.Identifier -> {
                val s = parent.ts.snapshot()

                val t = next()
                if (enableExpressionParsing && peek().type == TokenType.Operator &&
                    Operator.fromSymbol(peek().text) in setOf(Operator.Increment, Operator.Decrement)
                ) {
                    val op = Operator.fromSymbol(next(TokenType.Operator).text)!!
                    return UnaryExpr(op, VariableGet(t.text).also { it.at(t.row, t.col) }).also { it.at(t.row, t.col) }
                }

                if (peek().type == TokenType.LParen) {
                    parent.ts.restore(s)
                    return parseFunctionCall()
                }

                // ident.field = value  or  ident.method()
                if (peek().type == TokenType.Dot) {
                    parent.ts.restore(s)
                    return parseDotStatement()
                }

                // ident[index] = value  or  ident[index] (expression)
                if (peek().type == TokenType.LBracket) {
                    parent.ts.restore(s)
                    return parseDotStatement()
                }

                // If a binary (non-assignment) operator follows and expression statements
                // are allowed, parse the full expression rather than just the variable.
                // 若后跟二元（非赋值）运算符且允许表达式语句，则解析完整表达式而非仅变量。
                // TokenType.Question covers the '?:' (Elvis) two-token operator.
                if (enableExpressionParsing && (
                    peek().type == TokenType.Question ||
                    (peek().type == TokenType.Operator &&
                        Operator.fromSymbol(peek().text)?.isAssignment() != true))) {
                    parent.ts.restore(s)
                    return StatementWrapper(expr.parse())
                }
                parent.ts.restore(s)
                parseVariableAssign()
            }

            TokenType.Keyword if t.text == "this" -> parseDotStatement()
            TokenType.Keyword if t.text in setOf("val", "var") -> parseVariableDecl()
            TokenType.Keyword if t.text == "if" -> expr.parseIf()
            TokenType.Keyword if t.text == "when" -> expr.parseWhen()
            TokenType.Keyword if t.text == "for" -> parseForLoop()
            TokenType.Keyword if t.text == "while" -> parseWhileLoop()
            TokenType.Keyword if t.text == "break" -> {
                next(); Break().also { it.at(t.row, t.col) }
            }

            TokenType.Keyword if t.text == "continue" -> {
                next(); Continue().also { it.at(t.row, t.col) }
            }

            TokenType.Keyword if t.text == "return" -> {
                next(); Return(expr.parse()).also { it.at(t.row, t.col) }
            }

            TokenType.Keyword if t.text == "defer" -> {
                next(); Defer(expr.parse()).also { it.at(t.row, t.col) }
            }

            TokenType.LBrace -> StatementWrapper(expr.parse())

            else -> {
                if (enableExpressionParsing) {
                    return StatementWrapper(expr.parse())
                }
                val tok = peek()
                diagnostic("Unexpected statement: '${tok.text}'", throws = true)
                throw IllegalStateException()
            }
        }
    }

    fun parseFunctionDecl(
        modifier: List<String> = listOf("local"),
        annotations: List<Annotation> = emptyList()
    ): LFunction {
        val funTok = next(TokenType.Keyword, "fun")
        val typeParams = parent.parseTypeParams()
        val name = next(TokenType.Identifier)
        next(TokenType.LParen)
        val arguments = parent.parseList { parseFunctionArgument() }
        val returnType = if (peek().type == TokenType.Colon) {
            next()
            parent.parseType()
        } else {
            LType("Unit")
        }
        next(TokenType.LBrace)
        val module = parse(Module.Builder()).build()
        next(TokenType.RBrace)
        return LFunction(modifier, name.text, typeParams, arguments, returnType, module, annotations).also { it.at(funTok.row, funTok.col) }
    }

    fun parseFunctionCall(): LFunctionCall {
        val name = next(TokenType.Identifier)
        val typeArgs = parent.parseTypeArgs()
        next(TokenType.LParen)
        val arguments = parent.parseList { expr.parse() }
        return LFunctionCall(name.text, arguments, typeArgs).also { it.at(name.row, name.col) }
    }

    fun parseForLoop(): For {
        val forTok = next(TokenType.Keyword, "for")
        next(TokenType.LParen)
        val init = once()
        next(TokenType.Semicolon) { "Expected ';' after for-loop initializer" }
        val cond = expr.parse()
        next(TokenType.Semicolon) { "Expected ';' after for-loop condition" }
        val update = once(true)
        next(TokenType.RParen)
        val body = if (peek().type == TokenType.LBrace) {
            inModule.parseWithBraces(Module.Builder()).build()
        } else {
            Module(listOf(once()))
        }
        return For(init, cond, update, body).also { it.at(forTok.row, forTok.col) }
    }

    fun parseWhileLoop(): While {
        val whileTok = next(TokenType.Keyword, "while")
        next(TokenType.LParen)
        val cond = expr.parse()
        next(TokenType.RParen)
        return While(cond, parent.parseBlock()).also { it.at(whileTok.row, whileTok.col) }
    }

    fun parseVariableDecl(
        modifier: List<String> = listOf("local"),
        annotations: List<Annotation> = emptyList()
    ): VariableDecl {
        val kwTok = next()
        val isConstant = kwTok.text == "val"
        val name = next(TokenType.Identifier) { "Expected variable name, actually \"${it.text}\"" }.text
        val type = if (peek().type == TokenType.Colon) {
            next()
            parent.parseType()
        } else
            LType.INFER
        val initExpr = if (peek().type == TokenType.Operator && peek().text == "=") {
            next()
            expr.parse()
        } else null
        return VariableDecl(modifier, name, type, initExpr, isConstant, annotations).also { it.at(kwTok.row, kwTok.col) }
    }

    /** 解析以标识符或 this 开头、后跟 . 的语句：赋值或方法调用 */
    fun parseDotStatement(): Statement {
        val lhs = expr.parse(ExpressionParser.binaryLbp(Operator.Assign)) // 停在赋值符前，不消费
        val op = Operator.fromSymbol(peek().text)
        return if (op?.isAssignment() == true) {
            next()
            val rhs = expr.parse()
            when {
                lhs is FieldAccess -> FieldAssign(lhs.receiver, lhs.fieldName, rhs).also { it.at(lhs.row, lhs.col) }
                lhs is IndexAccess -> IndexAssign(lhs.receiver, lhs.index, rhs).also { it.at(lhs.row, lhs.col) }
                else -> { diagnostic("Invalid assignment target", throws = true); throw IllegalStateException() }
            }
        } else {
            StatementWrapper(lhs)
        }
    }

    fun parseVariableAssign(): Statement {
        val nameTok = next(TokenType.Identifier)
        val name = nameTok.text

        // 检查是否有赋值运算符
        if (peek().type != TokenType.Operator ||
            Operator.fromSymbol(peek().text)?.isAssignment() != true
        ) {
            // 没有赋值运算符, 返回变量引用表达式
            return VariableGet(name).also { it.at(nameTok.row, nameTok.col) }
        }

        val op = Operator.fromSymbol(next(TokenType.Operator).text)!!
        return VariableAssign(name, op, expr.parse()).also { it.at(nameTok.row, nameTok.col) }
    }
}