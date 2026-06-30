package yukifuri.lang.lingspled.compiler.util

enum class Operator(
    val symbol: String,
    val lbp: Int,
    val rbp: Int,
    val nud: Int,
) {
    // Postfix / Unary  (priority 0 -> lbp=130; prefix nud=120)
    Inc("++", 130, 0, 120),
    Dec("--", 130, 0, 120),
    Not("!",    0, 0, 120),

    // Multiplicative   (priority 2 -> lbp=110)
    Mul("*",  110, 110, 0),
    Div("/",    110, 110, 0),
    Rem("%",    110, 110, 0),

    // Additive         (priority 3 -> lbp=100)
    Plus("+",   100, 100, 120),   // nud: also unary +
    Minus("-",  100, 100, 120),   // nud: also unary -

    // Range            (priority 4 -> lbp=90)
    RangeTo("..",     90, 90, 0),
    RangeUntil("..<", 90, 90, 0),

    // Infix Operator (Placeholder) (priority 5 -> lbp=80, left-assoc)
    Infix("0infix", 80, 80, 0),

    // Elvis            (priority 6 -> lbp=70, right-assoc)
    Elvis("?:", 70, 69, 0),

    // Named / type checks  (priority 7 -> lbp=60, non-symbolic)
    In("in",      60, 60, 0),
    NotIn("!in",  60, 60, 0),
    Is("is",      60, 60, 0),
    IsNot("!is",  60, 60, 0),
    As("as",      60, 60, 0),
    SafeAs("as?", 60, 60, 0),

    // Comparison       (priority 8 -> lbp=50)
    Lt("<",       50, 50, 0),
    Gt(">",    50, 50, 0),
    Le("<=",    50, 50, 0),
    Ge(">=", 50, 50, 0),

    // Equality         (priority 9 -> lbp=40)
    Eq("==",   40, 40, 0),
    Ne("!=", 40, 40, 0),

    // Conjunction      (priority 10 -> lbp=30)
    And("&&", 30, 30, 0),

    // Disjunction      (priority 11 -> lbp=20)
    Or("||", 20, 20, 0),

    // Assignment       (priority 12 -> lbp=10, right-assoc)
    Assign("=",    10, 9, 0),
    PlusAssign("+=",  10, 9, 0),
    MinusAssign("-=", 10, 9, 0),
    TimesAssign("*=", 10, 9, 0),
    DivAssign("/=",   10, 9, 0),
    RemAssign("%=",   10, 9, 0),

    // Arrow (when branches, lambdas) - not a binary operator
    Arrow("->", 0, 0, 0),
    ;

    fun not() = when (this) {
        In -> NotIn
        NotIn -> In
        Is -> IsNot
        IsNot -> Is
        else -> throw IllegalArgumentException("Can't negate operator: $this")
    }

    companion object {
        val nonsymbolic = setOf(
            In, NotIn, Is, IsNot, As, SafeAs
        )

        val map = entries.associateBy { it.symbol }

        fun from(s: String) = map[s] ?: throw IllegalArgumentException("No such operator: $s")
    }
}