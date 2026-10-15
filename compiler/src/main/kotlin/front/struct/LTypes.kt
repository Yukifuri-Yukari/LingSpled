package yukifuri.lang.lingspled.compiler.front.struct

enum class LVariance {
    In,
    Out,
    None
}

sealed class LTypeDeclaration
sealed class LTypeReference

data class LTypeDecl(
    val name: String,
    val bound: LTypeReference?,
    val variance: LVariance,
) : LTypeDeclaration()

data class LTypeParameterDecl(
    val name: String,
    val bound: LTypeReference?,
    val variance: LVariance,
) : LTypeDeclaration()

data class LTypeRef(
    val name: String,
    val typeArgs: List<LTypeReference>,
    val variance: LVariance,
) : LTypeReference() {

    val args: List<LTypeReference> get() = typeArgs

    companion object {
        val unit = LTypeRef("ling.std.Unit", emptyList(), LVariance.None)
    }
}

data class LFunctionTypeRef(
    val params: List<LTypeReference>,
    val returnType: LTypeReference,
) : LTypeReference() {

    val parameters: List<LTypeReference> get() = params
    val typeArgs: List<LTypeReference> get() = params + returnType
}

data class LTypeParameterRef(
    val name: String,
    val variance: LVariance,
) : LTypeReference()

object LTypeParameterNone : LTypeReference()
