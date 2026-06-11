package yukifuri.lang.lingspled.compiler.ast.cls

import yukifuri.lang.lingspled.compiler.ast.LAArgument

data class LAAnnotation(
    val name: String,
    val args: List<LAArgument>
) {
}