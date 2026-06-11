package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.LAStatement
import yukifuri.lang.lingspled.compiler.ast.LAVisitor
import yukifuri.lang.lingspled.compiler.lexer.Position

object LAFile {
    data class LAPackageDeclaration(
        val part: List<String>,
        override val position: Position
    ) : LAStatement(position) {

        override fun accept(visitor: LAVisitor) = visitor.packageDecl(this)

        val packageFqn get() = part.joinToString(".") + "."
    }

    data class LAImportDeclaration(
        val part: List<String>,
        val wildcard: Boolean,
        val alias: String?,
        override val position: Position
    ) : LAStatement(position) {

        override fun accept(visitor: LAVisitor) = visitor.importDecl(this)

        val importFqn get() = part.joinToString(".") + if (wildcard) ".*" else ""
    }
}