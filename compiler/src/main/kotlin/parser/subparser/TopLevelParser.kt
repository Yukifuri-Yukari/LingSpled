package yukifuri.lang.lingspled.compiler.parser.subparser

import yukifuri.lang.lingspled.compiler.ast.LAErrorStatement
import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.module.LAFile
import yukifuri.lang.lingspled.compiler.util.ClassKind
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.lang.lingspled.compiler.parser.Parser

class TopLevelParser(parent: Parser) : SubParser(parent) {

    fun parse(): LAStatement {
        val errPos = peek().position

        try {
            skipWs()
            val annotations = parseAnnotations()
            skipWs()
            val modifiers = parseModifiers()
            skipWs()
            return when {
                peek("class") -> cls.cls(annotations, modifiers)
                peek("interface") -> cls.cls(annotations, modifiers, ClassKind.Interface)
                peek("val") || peek("var") -> module.variableDecl(annotations, modifiers)
                peek("fun") -> module.functionDecl(annotations, modifiers)
                peek("package") -> {
                    // package declaration
                    val pos = next().position
                    val parts = mutableListOf<String>()
                    while (hasNext()) {
                        skipWs()
                        parts.add(expectId().text)
                        skipWs()
                        if (!tryConsume(TokenType.Dot)) break
                    }
                    LAFile.LAPackageDeclaration(parts, pos)
                }

                peek("import") -> {
                    // import declaration
                    val pos = next().position
                    val parts = mutableListOf<String>()
                    var wildcard = false
                    while (true) {
                        skipWs()
                        if (tryConsume(TokenType.Operator, "*")) {
                            wildcard = true
                            break
                        }
                        parts.add(expectId().text)
                        skipWs()
                        if (!tryConsume(TokenType.Dot)) break
                    }
                    skipWs()
                    val alias = if (peek("as")) {
                        next()
                        skipWs()
                        expectId().text
                    } else null
                    LAFile.LAImportDeclaration(parts, wildcard, alias, pos)
                }

                else -> diagnostic("Expected declaration", "Error", safePos())
            }
        } catch (_: Throwable) {
            while (hasNext() && peek().type !in safepoints) next()
            next()
        }

        return LAErrorStatement(errPos)
    }
}