package yukifuri.lang.lingspled.compiler.exception

open class CompilationException(
    message: String,
    stage: String,
) : RuntimeException("Uncaught exception thrown at stage $stage: $message")

class LexemeException(
    message: String
) : CompilationException(message, "Lexeme")

class ParserException(
    message: String
) : CompilationException(message, "Parser")
