package yukifuri.lang.lingspled.compiler.util

import yukifuri.lang.lingspled.compiler.symbol.Symbol
import yukifuri.lang.lingspled.compiler.symbol.TypeParameterSymbol

abstract class LExpression

data class LParam(
    val name: String,
    val type: LTypeRef
)

data class LArgument(
    val name: String?,
    val expr: LExpression
)

data class LAnnotation(
    val name: String,
    val annotations: List<LAnnotation> = emptyList(),
    val arguments: List<LArgument> = emptyList(),
)

enum class Variance {
    Invariant,
    In,
    Out
}

enum class ClassKind {
    Class,
    Interface,
    Annotation,
    Enum,
}

sealed class LTypeDeclaration
sealed class LTypeReference

data class LTypeDecl(
    val name: String,
    val tp: List<LTypeParamDecl> = emptyList(),
    val annotations: List<LAnnotation> = emptyList(),
) : LTypeDeclaration()

data class LTypeParamDecl(
    val id: String,
    val variance: Variance = Variance.Invariant,
    val upbounds: List<LTypeReference> = listOf(LTypeRef.any),
) : LTypeDeclaration() {
    /** SymbolCollection / Resolution 阶段填入：本类型参数声明对应的符号。 */
    var symbol: TypeParameterSymbol? = null
}

data class LTypeRef(
    val name: String,
    val tp: List<LTypeReference> = emptyList(),
    val nullable: Boolean = false,
    val annotations: List<LAnnotation> = emptyList(),
    val variance: Variance = Variance.Invariant,
) : LTypeReference() {

    /** Resolution 遍（TYPES phase）填入：解析到的 ClassSymbol / TypeParameterSymbol。 */
    var symbol: Symbol? = null

    companion object {
        val any = primitive("ling.std.Any")
        val unit = primitive("ling.std.Unit")

        val infer = LTypeRef("/infer")

        val primByte = primitive("Byte")
        val primShort = primitive("Short")
        val primInt = primitive("Int")
        val primLong = primitive("Long")
        val primFloat = primitive("Float")
        val primDouble = primitive("Double")
        val primChar = primitive("Char")
        val primBoolean = primitive("Boolean")

        private fun primitive(name: String) = LTypeRef(name, emptyList())
    }
}

data class LTypeParamRef(
    val name: String,
    val variance: Variance = Variance.Invariant,
    val nullable: Boolean = false,
) : LTypeReference() {
    /** Resolution 阶段填入：解析到的 TypeParameterSymbol。 */
    var symbol: TypeParameterSymbol? = null
}

object LTypeParamNone : LTypeReference()
