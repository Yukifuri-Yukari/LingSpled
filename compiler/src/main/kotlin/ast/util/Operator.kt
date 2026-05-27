package yukifuri.lang.lingspled.compiler.ast.util

enum class Operator(
    val symbol: String,
    val priority: Int, // Small is higher priority
    val lexical: Boolean = true // include in lexer's operator token list
) {
    // Unary (priority 1)
    Not("!", 1),
    BitNot("~", 1),
    Increment("++", 1),
    Decrement("--", 1),
    NotNull("!!", 1),

    // Multiplicative (priority 2)
    Mul("*", 2),
    Div("/", 2),
    Mod("%", 2),

    // Additive (priority 3)
    Add("+", 3),
    Sub("-", 3),

    // Range (priority 4)
    Range("..", 4),

    // Bitwise shift (priority 5)
    Shl("<<", 5),
    Shr(">>", 5),
    UShr(">>>", 5),

    // Comparison (priority 6)
    Lt("<", 6),
    Gt(">", 6),
    Le("<=", 6),
    Ge(">=", 6),
    Is("is", 6),
    NotIs("!is", 6, lexical = false),
    In("in", 6),
    NotIn("!in", 6, lexical = false),

    // Equality (priority 7)
    Eq("==", 7),
    Ne("!=", 7),

    // Bitwise AND (priority 8)
    BitAnd("&", 8),

    // Bitwise XOR (priority 9)
    BitXor("^", 9),

    // Bitwise OR (priority 10)
    BitOr("|", 10),

    // Logical AND (priority 11)
    And("&&", 11),

    // Logical OR (priority 12)
    Or("||", 12),

    // Elvis (priority 13, two-token '?:', not lexed as a single token)
    Elvis("?:", 13, lexical = false),

    // Assignment (priority 14)
    Assign("=", 14),
    AddAssign("+=", 14),
    SubAssign("-=", 14),
    MulAssign("*=", 14),
    DivAssign("/=", 14),
    ModAssign("%=", 14),
    AndAssign("&=", 14),
    OrAssign("|=", 14),
    XorAssign("^=", 14),

    // Special (priority 15, matched last)
    Arrow("->", 15);

    companion object {
        val unaryOps = setOf(
            Not, BitNot,
            Increment, Decrement,
            Add, Sub,
        )

        val assignmentOps = mapOf(
            Assign to Assign,
            AddAssign to Add,
            SubAssign to Sub,
            MulAssign to Mul,
            DivAssign to Div,
            ModAssign to Mod,
            AndAssign to BitAnd,
            OrAssign to BitOr,
            XorAssign to BitXor
        )

        val bitOps = listOf(
            Shl, Shr, BitAnd, BitXor, BitOr, BitNot, UShr,
        )

        private val symbolMap: Map<String, Operator> = entries.associateBy { it.symbol }
        fun fromSymbol(symbol: String): Operator? = symbolMap[symbol]
    }

    fun toMethodName(): String = when (this) {
        Add -> "plus";  Sub -> "minus"; Mul -> "times"; Div -> "div"; Mod -> "rem"
        Range -> "rangeTo"
        Not -> "not";   BitNot -> "inv"
        BitAnd -> "and"; BitOr -> "or"; BitXor -> "xor"
        Shl -> "shl";   Shr -> "shr";  UShr -> "ushr"
        Eq  -> "equals"
        Lt  -> "lt";    Gt -> "gt";    Le -> "le";    Ge -> "ge"
        And -> "and";   Or -> "or"
        Increment -> "inc"; Decrement -> "dec"
        else -> throw IllegalArgumentException("$this has no method name")
    }

    fun isUnary() = this in unaryOps
    fun isAssignment() = this in assignmentOps
    fun extractAssignment() = assignmentOps[this] ?:
        throw IllegalArgumentException("$this is not an assignment operator.")

    override fun toString(): String = symbol
}