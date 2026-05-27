package yukifuri.lang.lingspled.compiler.astwalker

class WalkerException(message: String) : RuntimeException(message)

/**
 * Thrown when a return statement is executed.
 * Used to unwind the call stack back to the function call site.
 */
class ReturnSignal(val value: LValue) : RuntimeException()
class BreakSignal : RuntimeException()
class ContinueSignal : RuntimeException()