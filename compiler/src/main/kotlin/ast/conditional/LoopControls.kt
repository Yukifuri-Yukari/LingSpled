package yukifuri.lang.lingspled.compiler.ast.conditional

import yukifuri.lang.lingspled.compiler.ast.base.Statement
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor

abstract class LoopControl : Statement()

class Break : LoopControl() {
    override fun accept(visitor: AstVisitor) {
        visitor.loopCtrl(this)
    }
}

class Continue : LoopControl() {
    override fun accept(visitor: AstVisitor) {
        visitor.loopCtrl(this)
    }
}
