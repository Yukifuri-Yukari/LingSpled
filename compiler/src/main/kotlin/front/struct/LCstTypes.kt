package yukifuri.lang.lingspled.compiler.front.struct

enum class LVariance {
    In,
    Out,
    None
}

sealed class LCTypeDeclaration
sealed class LCTypeReference

class LCTypeDecl(
) : LCTypeDeclaration()

class LCTypeParameterDecl(
) : LCTypeDeclaration()

data class LCTypeRef(
    val name: String,
) : LCTypeReference() {

    companion object {
        val unit = LCTypeRef("ling.std.Unit")
    }
}

class LCTypeParameterRef(
) : LCTypeReference()

object LCTypeParameterNone : LCTypeReference()