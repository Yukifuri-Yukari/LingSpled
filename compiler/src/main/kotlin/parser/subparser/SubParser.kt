package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.base.Argument
import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel
import yukifuri.lang.lingspled.compiler.exception.CompilationException
import yukifuri.lang.lingspled.compiler.lexer.token.Token
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser

abstract class SubParser(
    val parent: Parser
) {
    protected val module by lazy { parent.module }
    protected val expr by lazy { parent.expr }
    protected val inModule by lazy { parent.inModule }
    protected val cls by lazy { parent.classParser }

    protected fun hasNext() = parent.ts.hasNext()
    protected fun peek() = parent.ts.peek()
    protected fun next() = parent.ts.next()
    protected fun skipStmtEnd() {
        if (!hasNext()) return
        if (peek().type == TokenType.NewLine) {
            next()
            return
        }
        if (peek().type == TokenType.RBrace) {
            return
        }
        next(TokenType.Semicolon) { "Expected ';' or 'NewLine' as statement terminator" }
    }
    protected fun skipWs() = parent.skipWs()
    protected fun parseFunctionArgument(): Argument {
        val name = next(TokenType.Identifier).text
        next(TokenType.Colon)
        val type = parent.parseType()
        val defaultValue = if (peek().type == TokenType.Operator && peek().text == "=") {
            next()
            expr.parse()
        } else null
        return Argument(name, type, defaultValue)
    }
    protected fun next(
        type: TokenType? = null, text: String = "",
        extra: String = "",
        message: (Token) -> String = {
            "Unexpected Token: \"${it.text}\" at ${it.row + 1}:${it.col + 1}, Required Type: $type"
        }
    ): Token {
        val t = parent.ts.next()
        if (type == null) return t
        if (t.type != type || (t.text != text && text != "")) {
            diagnostic(message(t), extra = extra, row = t.row, col = t.col, throws = true)
        }
        return t
    }

    protected fun diagnostic(
        message: String, extra: String = "",
        level: DiagnosticLevel = DiagnosticLevel.Error,
        row: Int = peek().row, col: Int = peek().col, throws: Boolean = false
    ) {
        parent.diagnostics.add(message, level, extra, row, col)
        if (throws) throw CompilationException(message)
    }
}