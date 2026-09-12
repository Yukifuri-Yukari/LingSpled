package yukifuri.lang.lingspled.compiler.front.parser

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.front.struct.*
import yukifuri.lang.lingspled.compiler.front.lexeme.token.ProcessedTokenStream
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.lang.lingspled.compiler.front.parser.subparser.ClassParser
import yukifuri.lang.lingspled.compiler.front.parser.subparser.ExpressionParser
import yukifuri.lang.lingspled.compiler.front.parser.subparser.ModuleParser

class Parser(
    val diagnostics: Diagnostics
) {

    var ts = ProcessedTokenStream(listOf())

    val expr = ExpressionParser(this)
    val module = ModuleParser(this)
    val cls = ClassParser(this)

    fun parse(ts: ProcessedTokenStream): LCFile {
        this.ts = ts

        // Package Declaration
        val pkg = if (expr.peek(TokenType.Keyword, "package")) {
            val pos = expr.next().position
            LCFile.LCPackageDecl(expr.fqn(), pos)
        } else null

        // Import Declaration
        val imports = mutableListOf<LCFile.LCImportDecl>()
        while (expr.hasNext() && expr.peek(null, "import")) {
            val pos = expr.next().position
            val fqn = expr.fqn()

            val alias = if (expr.peek(TokenType.Operator, "as")) {
                expr.next()
                expr.ident()
            } else null

            val wildcard = if (
                expr.peek(TokenType.Dot) &&
                expr.peek(1).text == "*" &&
                alias == null) {
                expr.next(2)
                true
            } else false

            imports.add(LCFile.LCImportDecl(fqn, alias, wildcard, pos))
            expr.stmtEnd(true)
        }

        val builder = LCModule.Builder()
        while (expr.hasNext()) {
            val modifiers = expr.parseModifiers()
            val stmt = when {
                expr.peek(null, "fun") -> module.functionDecl(modifiers)
                expr.peek().text in setOf("val", "var") -> module.variableDecl(modifiers)
                expr.peek().text in setOf("class", "interface") -> cls.parse(modifiers)
                else -> TODO("${expr.peek()}")
            }

            builder.add(stmt)
        }

        return LCFile(pkg, imports, builder.build(0 to 0))
    }

    override fun toString(): String {
        return "Parser(ptr=${ts.snapshot()}, peek=${if (ts.hasNext()) ts.peek() else "EOF"})"
    }

    companion object {
        val varDeclStart = setOf("val", "var")
        val lambdaParamStart = setOf(
            // Ignore IDENTIFIER before it
            TokenType.Arrow,
            TokenType.Comma,
            TokenType.Colon
        )
    }
}