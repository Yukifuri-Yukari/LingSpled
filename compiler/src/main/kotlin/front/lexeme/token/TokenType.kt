package yukifuri.lang.lingspled.compiler.front.lexeme.token

enum class TokenType {
    NewLine,
    Comment,
    Whitespace,

    Keyword,
    Identifier,

    Integer,
    Decimal,
    String,
    Character,
    // Boolean, Null are categorized as keyword

    Operator,

    LParen, RParen,
    LBracket, RBracket,
    LBrace, RBrace,

    Comma, // ,
    Dot, // .
    Semicolon, // ;
    Colon, // :
    Arrow, // ->
    At, // @

    ;
    companion object {
        val matchedParenthesis = mapOf(
            LParen to RParen,
            LBracket to RBracket,
            LBrace to RBrace,
        )

        val whitespaces = setOf(
            Whitespace,
            Comment,
            NewLine,
        )
    }
}