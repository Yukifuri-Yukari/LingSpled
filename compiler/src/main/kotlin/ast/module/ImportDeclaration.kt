package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

/**
 * import lspled.io.PrintStream         -> parts=["lspled","io","PrintStream"], isWildcard=false, alias=null
 * import lspled.io.*                   -> parts=["lspled","io"],               isWildcard=true,  alias=null
 * import lspled.io.PrintStream as PS   -> parts=["lspled","io","PrintStream"], isWildcard=false, alias="PS"
 */
class ImportDeclaration(
    val parts: List<String>,
    val isWildcard: Boolean = false,
    val alias: String? = null,
) : Statement() {
    override fun accept(visitor: AstVisitor) = visitor.importDecl(this)

    val qualifiedName get() = parts.joinToString(".")

    override fun toString() = buildString {
        append("ImportDeclaration(name='$qualifiedName'")
        if (isWildcard) append(", wildcard=true")
        if (alias != null) append(", as=$alias")
        append(")")
    }
}