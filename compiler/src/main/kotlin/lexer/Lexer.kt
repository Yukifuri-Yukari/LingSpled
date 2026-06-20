package yukifuri.lang.lingspled.compiler.lexer

import yukifuri.lang.lingspled.compiler.util.Operator
import yukifuri.lang.lingspled.compiler.exception.Diagnostics
import yukifuri.lang.lingspled.compiler.exception.InvalidCharacterException
import yukifuri.lang.lingspled.compiler.lexer.token.Token
import yukifuri.lang.lingspled.compiler.lexer.token.TokenStream
import yukifuri.lang.lingspled.compiler.lexer.token.TokenType
import yukifuri.libs.compilation.lex.NumberLexer
import yukifuri.libs.compilation.stream.CharStream
import yukifuri.libs.compilation.util.Verification

typealias Position = Pair<Int, Int>

class Lexer(
    private val diagnostics: Diagnostics
) {
    companion object {
        val keywords = setOf(
            "as", "break", "class", "continue", "else", "for", "fun",
            "if", "import", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "try", "typealias", "val", "var",
            "when", "while", "static", "final", "const"
        )

        val operators: List<String> = run {
            val s1 = mutableListOf<String>()
            for (op in Operator.entries - Operator.nonsymbolic) {
                s1.add(op.symbol)
            }
            s1
        }.sortedByDescending { it.length }

        val simpleTokens = mapOf(
            '{' to TokenType.LBrace,
            '}' to TokenType.RBrace,
            '[' to TokenType.LBracket,
            ']' to TokenType.RBracket,
            '(' to TokenType.LParen,
            ')' to TokenType.RParen,
            ';' to TokenType.Semicolon,
            ':' to TokenType.Colon,
            '.' to TokenType.Dot,
            ',' to TokenType.Comma,
            '@' to TokenType.At,
            '?' to TokenType.Question,
        )

        const val COMMENTS_AS_TOKEN = false
    }

    private var cs: CharStream = CharStream("")
    private val tokens = mutableListOf<Token>()
    var ts = TokenStream(tokens)

    private fun diagnostic(
        info: String, detail: String = "",
        start: Position = cs.row to cs.col, end: Position = start,
        sidenote: String = "",
    ) {
        diagnostics.add(info, detail, start, end, sidenote)
    }

    private fun skipWhitespace(): Boolean {
        if (!cs.hasNext() || !cs.peek().isWhitespace()) return false
        var emitted = false
        while (cs.hasNext() && cs.peek().isWhitespace()) {
            if (cs.peek() == '\n' && !emitted) {
                val s = cs.snapshot()
                tokens.add(Token("NEWLN", TokenType.NewLine, s.row, s.col))
                emitted = true
            }
            cs.next()
        }
        return true
    }

    private fun skipComment(): Boolean {
        if (!cs.hasNext() || cs.peek() != '/') return false
        val s = cs.snapshot()
        cs.next() // consume '/'
        if (!cs.hasNext() || (cs.peek() != '/' && cs.peek() != '*')) {
            cs.restore(s) // not a comment, put '/' back
            return false
        }
        val sb = StringBuilder("/")
        if (cs.peek() == '/') {
            // Line comment: skip until newline but leave '\n' for the main loop
            // to emit as a NewLine token — skipStmtEnd() needs it as a terminator.
            // 行注释：跳到换行符前停止，保留 '\n' 让主循环 emit NewLine token，
            // 因为 skipStmtEnd() 依赖 NewLine 作为语句终结符。
            sb.append(cs.next()) // consume second '/'
            while (cs.hasNext() && cs.peek() != '\n') sb.append(cs.next())
            if (COMMENTS_AS_TOKEN) {
                tokens.add(Token(sb.toString(), TokenType.Comment, s.row, s.col))
            }
            // '\n' intentionally NOT consumed here
        } else {
            // block comment: support nesting
            sb.append(cs.next()) // consume '*'
            var depth = 1
            while (cs.hasNext() && depth > 0) {
                val ch = cs.next()
                sb.append(ch)
                if (ch == '/' && cs.hasNext() && cs.peek() == '*') {
                    sb.append(cs.next()) // consume '*'
                    depth++
                } else if (ch == '*' && cs.hasNext() && cs.peek() == '/') {
                    sb.append(cs.next()) // consume '/'
                    depth--
                }
            }
            if (COMMENTS_AS_TOKEN) {
                tokens.add(Token(sb.toString(), TokenType.Comment, s.row, s.col))
            }
        }
        return true
    }

    private fun lexNumbers() {
        val row = cs.row
        val col = cs.col
        val number = NumberLexer.tryParseNumber(cs)
        if (number != null) {
            val type = if (number is Double || number is Float) TokenType.Decimal else TokenType.Integer
            tokens.add(Token("$number${if (number is Float) "f" else ""}", type, row, col))
        }
    }

    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun lexIdAndKeywords() {
        val row = cs.row
        val col = cs.col

        val sb = StringBuilder()
        while (cs.hasNext() && isIdentPart(cs.peek()))
            sb.append(cs.next())
        val s = sb.toString()
        if (s.isEmpty()) {
            diagnostic("Illegal Identifier")
            return
        }

        if (s == "true" || s == "false") {
            tokens.add(Token(s, TokenType.BooleanLiteral, row, col))
        } else {
            tokens.add(Token(s, if (s in keywords) TokenType.Keyword else TokenType.Identifier, row, col))
        }
    }

    private fun lexBacktickIdentifier() {
        val row = cs.row
        val col = cs.col

        cs.next() // consume starting '`'
        val sb = StringBuilder()
        while (cs.hasNext()) {
            val ch = cs.next()
            if (ch in setOf('\\'))
                diagnostic("Illegal character in backtick identifier")
            if (ch == '`') {
                // end
                val s = sb.toString()
                if (s.isEmpty()) {
                    diagnostic("Illegal Identifier")
                    return
                }
                tokens.add(Token(s, TokenType.Identifier, row, col))
                return
            }
            sb.append(ch)
        }
        diagnostic("Unterminated backtick identifier", start = row to col, end = cs.row to cs.col)
    }

    private fun escapeChar(c: Char) = when (c) {
        'n' -> '\n'
        't' -> '\t'
        'r' -> '\r'
        '\\' -> '\\'
        '"' -> '"'
        '0' -> '\u0000'
        else -> {
            diagnostic("Unknown escape sequence: \\$c")
            c
        }
    }

    private fun lexString() {
        val row = cs.row
        val col = cs.col

        cs.next() // consume first '"'
        val isMultiline = cs.hasNext() && cs.peek() == '"' && run {
            val s = cs.snapshot()
            cs.next() // consume second '"'
            if (cs.hasNext() && cs.peek() == '"') {
                cs.next() // consume third '"'
                true
            } else {
                cs.restore(s)
                false
            }
        }

        val sb = StringBuilder()

        if (isMultiline) {
            while (cs.hasNext()) {
                if (cs.peek() == '"') {
                    cs.next()
                    if (cs.hasNext() && cs.peek() == '"') {
                        cs.next()
                        if (cs.hasNext() && cs.peek() == '"') {
                            cs.next() // consume closing """
                            tokens.add(Token("\"$sb\"", TokenType.String, row, col))
                            return
                        }
                        sb.append("\"\"")
                    } else {
                        sb.append('"')
                    }
                    continue
                }
                val ch = cs.next()
                if (ch == '\\' && cs.hasNext()) {
                    sb.append(escapeChar(cs.next()))
                } else {
                    sb.append(ch)
                }
            }
            diagnostic("Unterminated multiline string", start = row to col, end = cs.row to cs.col)
        } else {
            // empty string shortcut: next char is already '"'
            if (cs.hasNext() && cs.peek() == '"') {
                cs.next()
                tokens.add(Token("\"\"", TokenType.String, row, col))
                return
            }
            while (cs.hasNext()) {
                val ch = cs.next()
                if (ch == '"') {
                    tokens.add(Token("\"$sb\"", TokenType.String, row, col))
                    return
                }
                if (ch == '\n') {
                    diagnostic("Unterminated string", start = row to col, end = cs.row to cs.col)
                    return
                }
                if (ch == '\\' && cs.hasNext()) {
                    sb.append(escapeChar(cs.next()))
                } else {
                    sb.append(ch)
                }
            }
            diagnostic("Unterminated string", start = row to col)
        }
    }

    private fun lexSimpleTokens() {
        val row = cs.row
        val col = cs.col

        val c = cs.peek()
        for (op in operators) {
            if (op.startsWith(c)) {
                val snapshot = cs.snapshot()
                val full = cs.next(op.length)
                if (op == full) {
                    tokens.add(Token(op, TokenType.Operator, row, col))
                    return
                } else {
                    cs.restore(snapshot)
                }
            }
        }
        if (c in simpleTokens) {
            cs.next()
            tokens.add(Token(c.toString(), simpleTokens[c]!!, row, col))
            return
        }
        throw InvalidCharacterException(c)
    }

    fun lex(): Lexer {
        while (cs.hasNext()) {
            while (skipWhitespace() || skipComment()) { /* magic */ }
            if (!cs.hasNext()) break

            val c = cs.peek()
            when {
                c in Verification.numbers -> lexNumbers()
                c.isLetter() -> lexIdAndKeywords()
                c == '`' -> lexBacktickIdentifier()
                c == '"' -> lexString()
                else -> lexSimpleTokens()
            }
        }

        return this
    }

    fun reset(cs: CharStream): Lexer {
        tokens.clear()
        this.cs = cs
        return this
    }
}
