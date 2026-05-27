package yukifuri.lang.lingspled.compiler.imports.declaration

class ExternalDeclarations(
    val declarations: Set<Declaration>,
) {

    override fun toString(): String {
        return "ExternalDeclarations(declarations=$declarations)"
    }
}