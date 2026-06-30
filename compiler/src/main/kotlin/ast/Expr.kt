package yukifuri.lang.lingspled.compiler.ast

import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.util.Operator

sealed class LALiteral<T>(
    val value: T,
    position: Position
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.literalExpr(this)

    class LALInteger(
        value: Int,
        position: Position
    ) : LALiteral<Int>(value, position) {

        override fun toString() = "LALInteger($value)"
    }

    class LALLong(
        value: Long,
        position: Position
    ) : LALiteral<Long>(value, position) {

        override fun toString() = "LALLong($value)"
    }

    class LAFloat(
        value: Float,
        position: Position
    ) : LALiteral<Float>(value, position) {

        override fun toString() = "LAFloat($value)"
    }

    class LADouble(
        value: Double,
        position: Position
    ) : LALiteral<Double>(value, position) {

        override fun toString() = "LADouble($value)"
    }

    class LABoolean(
        value: Boolean,
        position: Position
    ) : LALiteral<Boolean>(value, position) {

        override fun toString() = "LABoolean($value)"
    }

    class LALNull(
        position: Position
    ) : LALiteral<Unit>(Unit, position) {

        override fun toString() = "LANull"
    }

    class LALString(
        value: String,
        position: Position
    ) : LALiteral<String>(value, position) {

        override fun toString() = "LALString(\"$value\")"
    }

    class LAThis(
        position: Position
    ) : LALiteral<Unit>(Unit, position) {

        override fun toString() = "LAThis"
    }
}

data class LAFieldAccessExpr(
    val receiver: LAExpression? = null,
    val field: String,
    override val position: Position,
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.fieldAccessExpr(this)
}

data class LAIndexAccessExpr(
    val receiver: LAExpression,
    val index: LAExpression,
    override val position: Position,
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.indexAccessExpr(this)
}

data class LAUnaryExpr(
    val op: Operator,
    val expr: LAExpression,
    val prefix: Boolean = true,
    override val position: Position,
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.unaryExpr(this)
}

data class LABinaryExpr(
    val left: LAExpression,
    val op: BinaryOperator,
    val right: LAExpression,
    override val position: Position,
) : LAExpression(position) {
    override fun accept(visitor: LAVisitor) = visitor.binaryExpr(this)

    data class BinaryOperator(
        val op: Operator,
        val infix: String? = null,
    ) {

        val isInfix get() = infix != null

        override fun toString() = "BinaryOperator(${infix ?: op})"

        companion object {
            fun op(op: Operator) = BinaryOperator(op)
            fun infix(infix: String) = BinaryOperator(Operator.Infix, infix)
        }
    }
}

data class LAInvokeExpr(
    val receiver: LAExpression,
    val arg: List<LAArgument>,
    override val position: Position,
) : LAStatement(position) {

    override fun accept(visitor: LAVisitor) = visitor.invokeExpr(this)
}

/**
 * 字符串插值模板：[parts] 是字面字符串（[LALiteral.LALString]）与插值表达式交替。
 * 由 Parser 从带 [yukifuri.lang.lingspled.compiler.lexer.token.StrSeg] 的 String token 构建；
 * FST 阶段脱糖为 `"" + part + part …` 的拼接（纯语法脱糖）。
 */
data class LAStringTemplate(
    val parts: List<LAExpression>,
    override val position: Position,
) : LAExpression(position) {

    override fun accept(visitor: LAVisitor) = visitor.stringTemplate(this)
}
