package yukifuri.lang.lingspled.compiler.ast.visitor

import yukifuri.lang.lingspled.compiler.ast.clazz.LClass
import yukifuri.lang.lingspled.compiler.ast.conditional.Defer
import yukifuri.lang.lingspled.compiler.ast.conditional.For
import yukifuri.lang.lingspled.compiler.ast.conditional.If
import yukifuri.lang.lingspled.compiler.ast.conditional.LoopControl
import yukifuri.lang.lingspled.compiler.ast.conditional.When
import yukifuri.lang.lingspled.compiler.ast.conditional.While
import yukifuri.lang.lingspled.compiler.ast.expr.AsExpr
import yukifuri.lang.lingspled.compiler.ast.expr.BinaryExpr
import yukifuri.lang.lingspled.compiler.ast.expr.FieldAccess
import yukifuri.lang.lingspled.compiler.ast.expr.FieldAssign
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAccess
import yukifuri.lang.lingspled.compiler.ast.expr.IndexAssign
import yukifuri.lang.lingspled.compiler.ast.expr.InvokeExpr
import yukifuri.lang.lingspled.compiler.ast.expr.MethodCall
import yukifuri.lang.lingspled.compiler.ast.expr.ThisExpr
import yukifuri.lang.lingspled.compiler.ast.expr.UnaryExpr
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.function.LFunctionCall
import yukifuri.lang.lingspled.compiler.ast.function.LambdaExpr
import yukifuri.lang.lingspled.compiler.ast.function.Return
import yukifuri.lang.lingspled.compiler.ast.literal.Literal
import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.ast.module.PackageDeclaration
import yukifuri.lang.lingspled.compiler.ast.variable.VariableAssign
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet

interface AstVisitor {
    fun functionDecl(f: LFunction)
    fun functionCall(f: LFunctionCall)
    fun literal(literal: Literal<*>)
    fun binaryOp(expr: BinaryExpr)
    fun unaryOp(expr: UnaryExpr)
    fun variableGet(expr: VariableGet)
    fun variableDecl(decl: VariableDecl)
    fun visitFor(forLoop: For)
    fun visitIf(condIf: If)
    fun functionReturn(funcReturn: Return)
    fun variableAssign(assign: VariableAssign)
    fun clazz(klass: LClass)
    fun pkg(declaration: PackageDeclaration)
    fun fieldAccess(expr: FieldAccess)
    fun fieldAssign(expr: FieldAssign)
    fun methodCall(expr: MethodCall)
    fun thisRef(expr: ThisExpr)
    fun importDecl(decl: ImportDeclaration)
    fun visitWhile(whl: While)
    fun loopCtrl(controller: LoopControl)
    fun visitDefer(defer: Defer)
    fun lambdaExpr(expr: LambdaExpr)
    fun invokeExpr(expr: InvokeExpr)
    fun indexAccess(expr: IndexAccess)
    fun indexAssign(expr: IndexAssign)
    fun visitWhen(expr: When)
    fun visitAs(expr: AsExpr)
}