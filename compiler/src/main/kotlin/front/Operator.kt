package yukifuri.lang.lingspled.compiler.front

enum class Operator(
    val nud: Int,
    val lbp: Int,
    val rbp: Int,
    val sym: String
) {

    Assign     (0, 10, 9, "="),
    PlusAssign (0, 10, 9, "+="),
    MinusAssign(0, 10, 9, "-="),
    TimesAssign(0, 10, 9, "*="),
    DivAssign  (0, 10, 9, "/="),
    ModAssign  (0, 10, 9, "%="),

    Or (0, 20, 20, "||"),
    And(0, 30, 30, "&&"),

    Eq    (0, 40, 40, "=="),
    Neq   (0, 40, 40, "!="),
    RefEq (0, 40, 40, "==="),
    RefNeq(0, 40, 40, "!=="),

    Lt(0, 50, 50, "<"),
    Gt(0, 50, 50, ">"),
    Le(0, 50, 50, "<="),
    Ge(0, 50, 50, ">="),

    In   (0, 60, 60, "in"),
    NotIn(0, 60, 60, "!in"),
    Is   (0, 60, 60, "is"),
    NotIs(0, 60, 60, "!is"),

    Elvis(0, 70, 69, "?:"),
    Infix(0, 70, 69, ""),

    Range     (0, 90, 90, ".."),
    RangeUntil(0, 90, 90, "..<"),

    Add(115, 100, 100, "+"),
    Sub(115, 100, 100, "-"),

    Mul(0, 110, 110, "*"),
    Div(0, 110, 110, "/"),
    Rem(0, 110, 110, "%"),

    As    (0, 112, 112, "as"),
    SafeAs(0, 112, 112, "as?"),

    Not(115, 0, 0, "!"),

    Inc(115, 120, 0, "++"),
    Dec(115, 120, 0, "--"),
    Factorial(0, 120, 0, "!"),

    NotNull(0, 120, 0, "!!"),
    SafeDot(0, 120, 0, "?."),
    ;

    override fun toString() = "Operator(name=$name, symbol='$sym')"

    companion object {
        fun bySymbol(sym: String): Operator? = (entries - Infix).find { it.sym == sym }
    }
}