package yukifuri.lang.lingspled.compiler.exception

open class CompilationException(message: String) : RuntimeException(message)

class EofException : CompilationException("Unexpected EOF reached")

class InvalidCharacterException(c: Char) : CompilationException("Unrecognized character: $c")

class ParsingException(message: String) : CompilationException("Parse failed with message: $message")

class TypeInferenceException(
    a: String, b: String,
    info: String = "Inference failed between"
) : CompilationException("$info: $a vs $b")
