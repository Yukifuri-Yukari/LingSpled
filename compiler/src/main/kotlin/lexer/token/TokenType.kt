package yukifuri.lang.lingspled.compiler.lexer.token

enum class TokenType {
    Identifier,
    Keyword,
    Operator,

    String,
    Integer,
    Decimal,
    BooleanLiteral,

    LBrace, RBrace,       // { }
    LBracket, RBracket,   // [ ]
    LParen, RParen,       // ( )
    Semicolon,            // ;
    Colon,                // :
    Dot,                  // .
    Comma,                // ,
    At,                   // @
    Question,             // ?

    NewLine,
    Comment,
}