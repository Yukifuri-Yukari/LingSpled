package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.base.Expression

data class Annotation(
    val name: String,
    val arguments: Map<String, Expression> = emptyMap()
)