package yukifuri.lang.lingspled.compiler.ir.hir.visitor

import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHCall
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHCast
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHFieldGet
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHLiteral
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHLocalGet
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHThis
import yukifuri.lang.lingspled.compiler.ir.hir.expr.LHLambda
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHAssign
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHDefer
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHExprStmt
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHFor
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHIf
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHLoopControl
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHWhile
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHReturn
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHVarDecl
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.LHWhen

interface HirVisitor {
    fun literal(lit: LHLiteral)
    fun localGet(expr: LHLocalGet)
    fun thisRef(expr: LHThis)
    fun call(expr: LHCall)
    fun fieldGet(expr: LHFieldGet)
    fun returnStmt(stmt: LHReturn)
    fun loopControl(ctrl: LHLoopControl)
    fun exprStmt(stmt: LHExprStmt)
    fun varDecl(decl: LHVarDecl)
    fun funDecl(decl: LHFunction)
    fun ifStmt(stmt: LHIf)
    fun whileStmt(stmt: LHWhile)
    fun forStmt(stmt: LHFor)
    fun classDecl(decl: LHClass)
    fun lambdaExpr(expr: LHLambda)
    fun castExpr(expr: LHCast)
    fun deferStmt(stmt: LHDefer)
    fun assignStmt(stmt: LHAssign)
    fun whenStmt(stmt: LHWhen)
}