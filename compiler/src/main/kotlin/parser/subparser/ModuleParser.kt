package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.base.StatementWrapper
import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.module.PackageDeclaration
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser

class ModuleParser(parent: Parser) : SubParser(parent) {
    companion object {
        val allModifiers = setOf(
            "public", "private", "protected",
            "static", "final", "abstract", "open", "override", "sealed"
        )
        val accessModifiers = setOf("public", "private", "protected")
    }

    fun parse(ast: Module.Builder): Module.Builder {
        while (hasNext()) {
            skipWs()
            once(ast)
            skipStmtEnd()
        }
        return ast
    }

    fun once(ast: Module.Builder) {
        val annotations = parent.parseAnnotations()
        val modifiers = parent.parseModifiers()

        val t = peek()
        ast.add(
            when (t.type) {
                TokenType.Keyword if t.text == "fun" -> inModule.parseFunctionDecl(modifiers, annotations)
                TokenType.Keyword if t.text in setOf("class", "interface", "annotation") -> StatementWrapper(
                    cls.parse(
                        modifiers,
                        annotations
                    )
                )

                TokenType.Keyword if t.text in setOf("val", "var") -> inModule.parseVariableDecl(modifiers, annotations)
                TokenType.Keyword if t.text == "package" -> {
                    next()
                    val names = mutableListOf<String>()
                    if (peek().type == TokenType.Identifier)
                        names.add(next().text)
                    while (hasNext() && peek().type == TokenType.Dot) {
                        next()
                        if (peek().type != TokenType.Identifier) break
                        names.add(next().text)
                    }
                    PackageDeclaration(names).also { it.at(t.row, t.col) }
                }

                TokenType.Keyword if t.text == "import" -> {
                    next()
                    val parts = mutableListOf<String>()
                    if (peek().type == TokenType.Identifier) parts.add(next().text)
                    var isWildcard = false
                    while (hasNext() && peek().type == TokenType.Dot) {
                        next()
                        if (peek().type == TokenType.Operator && peek().text == "*") {
                            next(); isWildcard = true; break
                        }
                        if (peek().type != TokenType.Identifier) break
                        parts.add(next().text)
                    }
                    val alias = if (!isWildcard && hasNext() &&
                        peek().type == TokenType.Keyword && peek().text == "as") {
                        next() // consume 'as'
                        next(TokenType.Identifier).text
                    } else null
                    ImportDeclaration(parts, isWildcard, alias).also { it.at(t.row, t.col) }
                }

                else -> {
                    val tok = next()
                    diagnostic("Unexpected top-level declaration: '${tok.text}'", throws = true)
                    throw IllegalStateException()
                }
            }
        )
    }
}