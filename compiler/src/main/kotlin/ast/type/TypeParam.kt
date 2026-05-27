package yukifuri.lang.lingspled.compiler.ast.type

data class TypeParam(
    val name: String,
    val bound: LType? = null
) {
    override fun toString(): String = if (bound != null) "$name : ${bound.typename()}" else name
}