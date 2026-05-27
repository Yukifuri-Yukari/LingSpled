package yukifuri.lang.lingspled.compiler.ir.sym

import yukifuri.lang.lingspled.compiler.diagnostics.DiagnosticLevel
import yukifuri.lang.lingspled.compiler.diagnostics.Diagnostics
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHClass
import yukifuri.lang.lingspled.compiler.ir.hir.module.LHFunction
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor

class SymbolCollector(
    private val table: SymbolTable,
    private val diagnostics: Diagnostics,
) : HirVisitor {

    override fun funDecl(decl: LHFunction) {
        val other = table.findFunction(decl.name)
        if (other != null) {
            diagnostics.add(
                "Multiple definitions found for function ${decl.name} with same signature below",
                DiagnosticLevel.Error,
                "",
                other.func.row,
                other.func.col,
            )
            diagnostics.add(
                "Multiple definitions found for function ${decl.name} with same signature above",
                DiagnosticLevel.Error,
                "",
                decl.row,
                decl.col,
            )
            throw IllegalStateException()
        }

        table.newFunction(decl.name, decl)
    }

    override fun classDecl(decl: LHClass) {
        val other = table.findClass(decl.name)
        if (other != null) {
            diagnostics.add(
                "Multiple definitions found for class ${decl.name} below",
                DiagnosticLevel.Error,
                "",
                other.row,
                other.col
            )
            diagnostics.add(
                "Multiple definitions found for class ${decl.name} above",
                DiagnosticLevel.Error,
                "",
                decl.row,
                decl.col
            )
            throw IllegalStateException()
        }

        table.newClass(decl.name, decl)
    }

    override fun varDecl(decl: LHVarDecl) {
        val other = table.findVariable(decl.name)
        if (other != null) {
            diagnostics.add(
                "Multiple definitions found for variable ${decl.name} below",
                DiagnosticLevel.Error,
                "",
                other.row,
                other.col
            )
            diagnostics.add(
                "Multiple definitions found for variable ${decl.name} above",
                DiagnosticLevel.Error,
                "",
                decl.row,
                decl.col
            )
            throw IllegalStateException()
        }
        if (decl.init == null) {
            diagnostics.add(
                "Variable ${decl.name} is not initialized",
                DiagnosticLevel.Error,
                "",
                decl.row,
                decl.col
            )
            throw IllegalStateException()
        }
        table.newVariable(decl.name, decl.declaredType, decl.init, decl.isConstant, decl.row, decl.col)
    }

    override fun literal(lit: LHLiteral) {}
    override fun localGet(expr: LHLocalGet) {}
    override fun thisRef(expr: LHThis) {}
    override fun call(expr: LHCall) {}
    override fun fieldGet(expr: LHFieldGet) {}
    override fun returnStmt(stmt: LHReturn) {}
    override fun loopControl(ctrl: LHLoopControl) {}
    override fun exprStmt(stmt: LHExprStmt) {}
    override fun ifStmt(stmt: LHIf) {}
    override fun whileStmt(stmt: LHWhile) {}
    override fun forStmt(stmt: LHFor) {}
    override fun lambdaExpr(expr: LHLambda) {}
    override fun castExpr(expr: LHCast) {}
    override fun deferStmt(stmt: LHDefer) {}
    override fun assignStmt(stmt: LHAssign) {}
    override fun whenStmt(stmt: LHWhen) {}
}
