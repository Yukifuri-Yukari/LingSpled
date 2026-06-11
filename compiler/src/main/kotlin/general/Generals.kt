package yukifuri.lang.lingspled.compiler.general

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
) : LTypeDeclaration()

data class LTypeRef(
    val name: String,
    val tp: List<LTypeReference> = emptyList(),
    val nullable: Boolean = false,
    val annotations: List<LAnnotation> = emptyList(),
) : LTypeReference() {

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
) : LTypeReference()

object LTypeParamNone : LTypeReference()
