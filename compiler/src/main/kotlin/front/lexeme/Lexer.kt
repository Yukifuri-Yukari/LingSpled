package yukifuri.lang.lingspled.compiler.front.lexeme

import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.exception.LexemeException
import yukifuri.lang.lingspled.compiler.front.Operator
import yukifuri.lang.lingspled.compiler.front.lexeme.token.Token
import yukifuri.lang.lingspled.compiler.front.lexeme.token.ProcessedTokenStream
import yukifuri.lang.lingspled.compiler.front.lexeme.token.TokenType
import yukifuri.libs.compilation.lex.NumberLexer
import yukifuri.libs.compilation.stream.CharStream
import yukifuri.libs.compilation.stream.CharacterStream
import yukifuri.libs.compilation.util.Verification

class Lexer(
    val diagnostics: Diagnostics
) {
    companion object {
        fun eof(): Nothing = throw LexemeException("EOF Reached")

        val punctuation = mapOf(
            '(' to TokenType.LParen,
            ')' to TokenType.RParen,
            '[' to TokenType.LBracket,
            ']' to TokenType.RBracket,
            '{' to TokenType.LBrace,
            '}' to TokenType.RBrace,
            ',' to TokenType.Comma,
            '.' to TokenType.Dot,
            ';' to TokenType.Semicolon,
            ':' to TokenType.Colon,
            '@' to TokenType.At,
            // Arrow is in special cases
        )

        val keywords = setOf(
            "package", "fun", "true", "false", "null", "for", "while", "if", "else", "return",
            "break", "continue"
        )

        val operators = (Operator.entries - Operator.Infix).sortedByDescending { it.sym.length }
    }

    private var tokens = mutableListOf<Token>()
    private var cs: CharacterStream = CharStream("")

    private fun lexWhitespace(): Boolean {
        var skipped = false

        while (cs.hasNext() && cs.peek().isWhitespace()) {
            if (cs.peek() == '\n') {
                cs.next()
                tokens.add(Token(TokenType.NewLine, "", cs.position()))
                skipped = true
                continue
            }
            val pos = cs.position()
            // whatever it is (half-width & full-width space, tab or sth),
            // if java treat it as whitespace, join
            tokens.add(Token(TokenType.Whitespace, "\\U16+${cs.next().code}", pos))
            skipped = true
        }

        return skipped
    }

    private fun lexComment(): Token? {
        val start = cs.position()
        val sb = StringBuilder()
        if (!cs.hasNext(2) || cs.peek(2) !in setOf("//", "/*")) return null
        sb.append(cs.next())
        val isLineComment = cs.peek() == '/'
        sb.append(cs.next())

        if (isLineComment) {
            while (cs.hasNext() && cs.peek() != '\n') {
                sb.append(cs.next())
            }

            return Token(TokenType.Comment, sb.toString(), start)
        }

        var depth = 1
        while (depth > 0) {
            if (!cs.hasNext()) {
                diagnostics.add("Unterminated comment", start to cs.position())
                eof()
            }

            val c = cs.next()
            sb.append(c)
            if (c == '*' && cs.hasNext() && cs.peek() == '/') {
                sb.append(cs.next())
                depth--
            } else if (c == '/' && cs.hasNext() && cs.peek() == '*') {
                sb.append(cs.next())
                depth++
            }
        }

        return Token(TokenType.Comment, sb.toString(), start)
    }

    private fun lexNumber(): Token {
        val start = cs.position()

        val num = NumberLexer.tryParseNumber(cs) ?: run {
            diagnostics.add("Invalid number", start to cs.position())
            throw LexemeException("Invalid number at $start")
        }

        val type = when (num) {
            is Int, is Long -> TokenType.Integer
            is Float, is Double -> TokenType.Decimal
            else -> throw IllegalStateException("Unrecognized type of num: ${num.javaClass}")
        }

        val text = when (num) {
            is Int -> num.toString()
            is Long -> num.toString() + "L"
            is Float -> num.toString() + "F"
            is Double -> num.toString()
            else -> throw IllegalStateException() // Through, the type above proves
            // the type of num must be one of the 4 above.
        }

        return Token(type, text, start)
    }

    private fun validCharacter(): Char {
        val start = cs.position()
        return when (cs.peek()) {
            '\\' -> {
                cs.next()
                when (cs.next()) {
                    'u' -> cs.next(4).hexToInt().toChar()
                    'x' -> cs.next(2).hexToInt().toChar()
                    't' -> '\t'
                    'r' -> '\r'
                    'n' -> '\n'
                    'b' -> '\b'
                    else -> {
                        diagnostics.add("Invalid escape sequence", start to cs.position())
                        throw LexemeException("Invalid escape sequence at $start")
                    }
                }
            }

            else -> cs.next()
        }
    }

    private fun lexString(): Token {
        val start = cs.position()

        val isLineString = cs.peek(3) != "\"\"\""
        val sb = StringBuilder()

        if (isLineString) {
            sb.append(cs.next())
            while (cs.hasNext() && cs.peek() != '"') {
                if (cs.peek() == '\n') {
                    diagnostics.add("Unterminated string", start to cs.position())
                }
                sb.append(validCharacter())
            }
            sb.append(cs.next())
            return Token(TokenType.String, sb.toString(), start)
        }

        sb.append(cs.next(3))
        while (cs.hasNext() && cs.peek(3) != "\"\"\"") {
            sb.append(validCharacter())
        }
        sb.append(cs.next(3))
        return Token(TokenType.String, sb.toString(), start)
    }

    private fun lexIdAndKw(): Token {
        val start = cs.position()
        val sb = StringBuilder()

        if (cs.peek() == '`') {
            sb.append(cs.next())
            while (cs.hasNext() && cs.peek() != '`') {
                if (cs.peek() in setOf('\\', '/', '"', '|', ':', ";")) {
                    diagnostics.add("Invalid character in identifier", start to cs.position())
                    throw LexemeException("Invalid character in identifier at $start")
                }
                sb.append(cs.next())
            }
            if (!cs.hasNext()) {
                diagnostics.add("Unterminated identifier", start to cs.position())
                throw LexemeException("Unterminated identifier at $start")
            }
            sb.append(cs.next())
            return Token(TokenType.Identifier, sb.toString(), start)
        }

        while (cs.hasNext() && (cs.peek().isLetterOrDigit() || cs.peek() in setOf('$', '_'))) {
            sb.append(cs.next())
        }

        val id = sb.toString()

        return Token(
            if (id in keywords) TokenType.Keyword else TokenType.Identifier,
            id,
            start
        )
    }

    private fun lexBacktickId(): Token {
        val pos = cs.position()
        cs.next()
        val sb = StringBuilder()
        while (cs.hasNext() && cs.peek() != '`') {
            if (cs.peek() == '\n')
                diagnostics.add("Unterminated backtick identifier", pos to cs.position())
            if (cs.peek() in setOf('\\', '/', ';', ':', '[', ']', '(', ')', '<', '>', '.'))
                diagnostics.add("Invalid character in backtick identifier", pos to cs.position())
            sb.append(cs.next())
        }
        cs.next() // consume the closing backtick
        return Token(TokenType.Identifier, sb.toString(), pos)
    }

    /**
     * This method only parses these following tokens:
     * - Operator (e.g. `.. > < + - ==`), Operators are auto-categorized from long to short by referring to [Operator]
     * - Punctuation (e.g. `( ) [ ] { } , . ; : @`)
     * - Arrow (->), it's in the special case because it isn't an operator but a token type.
     */
    private fun simpleToken(): Token {
        val start = cs.position()

        if (cs.hasNext(2) && cs.peek(2) == "->") {
            cs.next(2)
            return Token(TokenType.Arrow, "->", start)
        }

        val possibleOperator = operators.firstOrNull {
            cs.hasNext(it.sym.length) && cs.peek(it.sym.length) == it.sym
        }
        if (possibleOperator != null) {
            cs.next(possibleOperator.sym.length)
            return Token(TokenType.Operator, possibleOperator.sym, start)
        }

        val possiblePunctuation = punctuation[cs.peek()]
        if (possiblePunctuation != null) {
            return Token(possiblePunctuation, cs.next().toString(), start)
        }

        throw LexemeException("Invalid token at $start: \"${if (cs.hasNext()) cs.peek() else ""}\"")
    }

    private fun postprocess() {
        val finalTokens = mutableListOf<Token>()
        val parentheses = ArrayDeque<TokenType>()

        var ptr = 0
        while (ptr < tokens.size) {
            val t = tokens[ptr++]

            if (t.type in setOf(TokenType.LParen, TokenType.LBracket)) {
                parentheses.add(t.type)
            }

            if (t.type in setOf(TokenType.RParen, TokenType.RBracket)) {
                if (parentheses.isEmpty())
                    throw LexemeException("Unclosed parenthesis ${t.type} at ${t.position}")
                val last = parentheses.removeLast()
                if (TokenType.matchedParenthesis[last] != t.type)
                    throw LexemeException("Unmatched parentheses between $last and ${t.type}")
            }

            if (t.type == TokenType.NewLine && parentheses.isNotEmpty()) {
                // Skip newlines inside parentheses, brackets.
                continue
            } else {
                finalTokens.add(t)
            }
        }
        tokens = finalTokens
    }

    fun lex(cs: CharacterStream): ProcessedTokenStream {
        tokens = mutableListOf()
        this.cs = cs

        while (cs.hasNext()) {
            while (true) {
                val ws = lexWhitespace()
                val comment = lexComment()

                if (comment != null) {
                    tokens.add(comment)
                }

                if (comment == null && !ws) break
            }
            if (!cs.hasNext()) break
            val c = cs.peek()
            val pos = cs.position()
            tokens.add(
                when {
                    cs.hasNext(3) && cs.peek(3) in setOf("as?", "!is", "!in")
                            && !cs.peek(3)[2].isLetter() -> Token(
                        TokenType.Operator,
                        cs.next(3),
                        pos
                    )

                    cs.hasNext(2) && cs.peek(2) in setOf("as", "is", "in")
                            && !cs.peek(3)[2].isLetter() -> Token(
                        TokenType.Operator,
                        cs.next(2),
                        pos
                    )

                    c in Verification.numbers -> lexNumber()

                    c == '`' -> lexBacktickId()
                    c.isLetter() -> lexIdAndKw()
                    c == '\''
                        -> Token(TokenType.Character, validCharacter().toString(), cs.position())

                    c == '"' -> lexString()
                    else -> simpleToken()
                }
            )
        }
        postprocess()
        return ProcessedTokenStream(tokens)
    }

    private fun CharacterStream.position() = row to col
}