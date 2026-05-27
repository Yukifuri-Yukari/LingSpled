package yukifuri.lang.lingspled.compiler.ast.module

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

class PackageDeclaration(
    val name: List<String>
) : Statement() {
    override fun accept(visitor: AstVisitor) {
        visitor.pkg(this)
    }

    override fun toString(): String {
        return "PackageDeclaration(name=$name)"
    }
}