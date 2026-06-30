package yukifuri.lang.lingspled.compiler.ast

import yukifuri.lang.lingspled.compiler.ast.cls.LAClass
import yukifuri.lang.lingspled.compiler.ast.control.LABreak
import yukifuri.lang.lingspled.compiler.ast.control.LAContinue
import yukifuri.lang.lingspled.compiler.ast.control.LADoWhile
import yukifuri.lang.lingspled.compiler.ast.control.LAFor
import yukifuri.lang.lingspled.compiler.ast.control.LAIf
import yukifuri.lang.lingspled.compiler.ast.control.LALambda
import yukifuri.lang.lingspled.compiler.ast.control.LAThrow
import yukifuri.lang.lingspled.compiler.ast.control.LATry
import yukifuri.lang.lingspled.compiler.ast.control.LAWhile
import yukifuri.lang.lingspled.compiler.ast.decl.LAVariableDecl
import yukifuri.lang.lingspled.compiler.ast.module.LAFile
import yukifuri.lang.lingspled.compiler.ast.module.LAFunction
import yukifuri.lang.lingspled.compiler.util.LExpression
import yukifuri.lang.lingspled.compiler.util.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position

abstract class LAExpression(open val position: Position) : LExpression() {
    abstract fun accept(visitor: LAVisitor)
}

abstract class LAStatement(position: Position) : LAExpression(position)

class LAExprStatement(val expr: LAExpression) : LAStatement(expr.position) {

    override fun accept(visitor: LAVisitor) = expr.accept(visitor)

    override fun toString() = expr.toString()
}

class LAErrorStatement(position: Position) : LAStatement(position) {

    override fun toString() = "LAErrorStatement()"

    override fun accept(visitor: LAVisitor) {}
}

data class LAParameter(
    val name: String,
    val type: LTypeRef,
    val vararg: Boolean = false,
    val default: LAExpression? = null,
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

    fun ifExpr(expr: LAIf)
    fun whileStmt(stmt: LAWhile)
    fun doWhileStmt(stmt: LADoWhile)
    fun forStmt(stmt: LAFor)
    fun tryExpr(expr: LATry)
    fun throwExpr(expr: LAThrow)
    fun lambdaExpr(expr: LALambda)
    fun breakStmt(stmt: LABreak)
    fun continueStmt(stmt: LAContinue)

    fun literalExpr(expr: LALiteral<*>)
    fun stringTemplate(node: LAStringTemplate)
    fun fieldAccessExpr(expr: LAFieldAccessExpr)
    fun indexAccessExpr(expr: LAIndexAccessExpr)
    fun unaryExpr(expr: LAUnaryExpr)
    fun binaryExpr(expr: LABinaryExpr)
    fun invokeExpr(expr: LAInvokeExpr)

    fun ret(stmt: LAFunction.LAReturnStmt)
}