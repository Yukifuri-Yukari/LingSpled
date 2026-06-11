package yukifuri.lang.lingspled.compiler.ast

import yukifuri.lang.lingspled.compiler.ast.cls.LAClass
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFile
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.general.LExpression
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position

abstract class LAExpression(open val position: Position) : LExpression() {
    abstract fun accept(visitor: LAVisitor)
}

abstract class LAStatement(position: Position) : LAExpression(position)

class LAExprStatement(val expr: LAExpression) : LAStatement(expr.position) {

    override fun accept(visitor: LAVisitor) = expr.accept(visitor)

    override fun toString() = expr.toString()
}

data class LAParameter(
    val name: String,
    val type: LTypeRef
)

data class LAArgument(
    val name: String?,
    val value: LAExpression
)

interface LAVisitor {

    fun packageDecl(decl: LAFile.LAPackageDeclaration)
    fun importDecl(decl: LAFile.LAImportDeclaration)
    fun funcDecl(decl: LAFunction)
    fun varDecl(decl: LAVariableDecl)
    fun clsDecl(decl: LAClass)

    /* fun ifStmt(stmt: LAIf)
    fun whileStmt(stmt: LAWhile)
    fun forStmt(stmt: LAFor)
    fun whenStmt(stmt: LAWhen) */

    fun literalExpr(expr: LALiteral<*>)
    fun fieldAccessExpr(expr: LAFieldAccessExpr)
    fun indexAccessExpr(expr: LAIndexAccessExpr)
    fun unaryExpr(expr: LAUnaryExpr)
    fun binaryExpr(expr: LABinaryExpr)
    fun invokeExpr(expr: LAInvokeExpr)

    fun ret(stmt: LAFunction.LAReturnStmt)
}