package yukifuri.lang.lingspled.compiler.front.struct

import yukifuri.lang.lingspled.compiler.front.Operator
import yukifuri.lang.lingspled.compiler.bridge.Position
import yukifuri.lang.lingspled.compiler.front.parser.Modifier

interface LCVisitor {

    fun declFunc(func: LCFunction)
    fun declVar(decl: LCVariableDecl)
    fun declClass(klass: LCClass)

    fun ctrlFor(stmt: LCCtrlFor)
    fun ctrlWhile(stmt: LCCtrlWhile)
    fun ctrlIf(stmt: LCCtrlIf)
    fun ctrlReturn(stmt: LCCtrlReturn)
    fun ctrlBreak(stmt: LCCtrlBreak)
    fun ctrlContinue(stmt: LCCtrlContinue)

    fun exprAs(expr: LCExprAs)
    fun exprUnary(expr: LCExprUnary)
    fun exprBinary(expr: LCExprBinary)
    fun exprLambda(lambda: LCLambda)
    fun exprLiteral(literal: LCLiteral<*>)
    fun exprInvoke(invocation: LCExprInvoke)
    fun exprIndex(index: LCExprIndex)
    fun exprMemberAccess(access: LCExprMemberAccess)
    fun exprVarGet(get: LCExprVariableGet)
}

/**
 * Simple Declaration in EBNF:
 * ```
 * Qn = {id "."} id;
 * ```
 */
data class LQualifiedName(val parts: List<String>)

data class LCArgument(
    val name: String?,
    val value: LCExpression,
    val position: Position,
)

data class LCParameter(
    val modifier: Modifier?,
    val name: String,
    val type: LCTypeReference,
    val default: LCExpression?,
    val position: Position
) {
    enum class Modifier(val sym: String) {
        Val("val"),
        Var("var"),
        Vararg("vararg"),

        ////////
        Crossinline("crossinline"),
        Noinline("noinline"),
        ;

        companion object {
            fun from(sym: String): Modifier? = entries.find { it.sym == sym }
        }
    }
}

data class LCLambdaParameter(
    val modifier: LCParameter.Modifier?,
    val name: String,
    val type: LCTypeReference?,
    val default: LCExpression?,
    val position: Position
)

data class LCAnnotation(
    val name: String,
    val args: List<LCArgument>
)

sealed class LCExpression(val position: Position) {
    abstract fun accept(visitor: LCVisitor)
}

sealed class LCStatement(position: Position) : LCExpression(position)

class LCExprStatement(
    val expr: LCExpression,
) : LCStatement(expr.position) {

    override fun accept(visitor: LCVisitor) = expr.accept(visitor)

    override fun toString() = expr.toString()
}

data class LCOperator(
    val infix: String?,
    val operator: Operator,
)

data class LCParenGroup(
    val expr: LCExpression,
) : LCExpression(expr.position) {

    override fun accept(visitor: LCVisitor) = expr.accept(visitor)
}

data class LCExprAs(
    val recv: LCExpression,
    val type: LCTypeRef,
    val isSafe: Boolean,
) : LCExpression(recv.position) {

    override fun accept(visitor: LCVisitor) = visitor.exprAs(this)
}

data class LCExprUnary(
    val recv: LCExpression,
    val operator: Operator,
    val prefix: Boolean,
) : LCExpression(recv.position) {

    override fun accept(visitor: LCVisitor) = visitor.exprUnary(this)
}

data class LCExprBinary(
    val left: LCExpression,
    val operator: LCOperator,
    val right: LCExpression,
) : LCExpression(left.position) {

    override fun accept(visitor: LCVisitor) = visitor.exprBinary(this)
}

data class LCExprInvoke(
    val recv: LCExpression,
    val args: List<LCArgument>,
    val lambda: LCLambda?
) : LCStatement(recv.position) {

    override fun accept(visitor: LCVisitor) = visitor.exprInvoke(this)
}

data class LCExprIndex(
    val recv: LCExpression,
    val index: LCExpression,
) : LCExpression(recv.position) {

    override fun accept(visitor: LCVisitor) = visitor.exprIndex(this)
}

class LCExprMemberAccess(
    val recv: LCExpression,
    val name: String,
    position: Position
) : LCExpression(position) {

    override fun accept(visitor: LCVisitor) = visitor.exprMemberAccess(this)

    override fun toString() =
        "LCExprMemberAccess(recv=$recv, name='$name', position=$position)"
}

class LCExprVariableGet(
    val name: String,
    position: Position
) : LCExpression(position) {

    override fun accept(visitor: LCVisitor) = visitor.exprVarGet(this)

    override fun toString() =
        "LCVariableGet(name='$name', position=$position)"
}

class LCLambda(
    val params: List<LCLambdaParameter>,
    val body: LCModule,
    position: Position,
) : LCExpression(position) {

    override fun accept(visitor: LCVisitor) = visitor.exprLambda(this)

    override fun toString() =
        "LCLambda(params=$params, body=$body, position=$position)"
}

class LCVariableDecl(
    val name: String,
    val type: LCTypeRef?,
    val init: LCExpression,
    val mutable: Boolean,
    val modifiers: List<Modifier>,
    position: Position,
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = visitor.declVar(this)

    override fun toString() =
        "LCVariableDecl(name='$name', type=$type, init=$init, mutable=$mutable, modifiers=$modifiers, position=$position)"
}

class LCCtrlFor(
    val name: String,
    val expr: LCExpression,
    val module: LCModule,
    position: Position
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = visitor.ctrlFor(this)

    override fun toString() =
        "LCCtrlFor(name='$name', expr=$expr, module=$module, position=$position)"
}

class LCCtrlWhile(
    val expr: LCExpression,
    val module: LCModule,
    position: Position,
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = visitor.ctrlWhile(this)

    override fun toString() =
        "LCCtrlWhile(expr=$expr, module=$module, position=$position)"
}

class LCCtrlIf(
    val expr: LCExpression,
    val ifBlock: LCModule,
    val elseBlock: LCModule?,
    position: Position
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = visitor.ctrlIf(this)

    override fun toString() =
        "LCCtrlIf(expr=$expr, ifBlock=$ifBlock, elseBlock=$elseBlock, position=$position)"
}

class LCCtrlReturn(
    val expr: LCExpression?,
    position: Position
) : LCStatement(position) {

    override fun toString() =
        "LCCtrlReturn(expr=$expr, position=$position)"

    override fun accept(visitor: LCVisitor) = visitor.ctrlReturn(this)
}

class LCCtrlBreak(
    position: Position
) : LCStatement(position) {

    override fun toString() =
        "LCCtrlBreak(position=$position)"

    override fun accept(visitor: LCVisitor) = visitor.ctrlBreak(this)
}

class LCCtrlContinue(
    position: Position
) : LCStatement(position) {

    override fun toString() =
        "LCCtrlContinue(position=$position)"

    override fun accept(visitor: LCVisitor) = visitor.ctrlContinue(this)
}

class LCClass(
    val name: String,
    val modifiers: List<Modifier>,
    val supers: List<LCTypeRef>,
    val body: ClassBody,
    position: Position
) : LCStatement(position){

    override fun accept(visitor: LCVisitor) {
        visitor.declClass(this)
    }

    override fun toString() =
        "LCClass(name='$name', modifiers=$modifiers, supers=$supers, body=$body, position=$position)"

    data class Constructor(
        val modifiers: List<Modifier>,
        val params: List<LCParameter>,
        val delegationCall: LCExpression?,
        val body: LCModule?,
        val pos: Position
    )

    data class ClassBody(
        val constructors: List<Constructor>,
        val members: List<LCStatement>,
        val initBlock: InitBlock?
    )

    data class InitBlock(
        val body: LCModule,
        val pos: Position
    )
}

data class LCFile(
    val pkg: LCPackageDecl?,
    val imports: List<LCImportDecl>,
    val module: LCModule
) {

    data class LCPackageDecl(
        val fqn: LQualifiedName,
        val pos: Position,
    )

    data class LCImportDecl(
        val fqn: LQualifiedName,
        val alias: String?,
        val wildcard: Boolean,
        val pos: Position,
    )
}

open class LCModule(
    val stmt: List<LCStatement>,
    position: Position,
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = stmt.forEach { it.accept(visitor) }

    override fun toString() = "LCModule(stmt=$stmt, position=$position)"

    class Builder(val list: MutableList<LCStatement> = mutableListOf()) {

        fun add(stmt: LCStatement): Builder {
            list.add(stmt)
            return this
        }

        fun build(position: Position) = LCModule(list, position)
    }
}

abstract class LCLiteral<T>(val value: T, position: Position) : LCExpression(position) {

    override fun accept(visitor: LCVisitor) = visitor.exprLiteral(this)

    override fun toString(): String = "LC${this.javaClass.simpleName}eral(value=$value, position=$position)"

    class IntLit(value: Int, position: Position) : LCLiteral<Int>(value, position)
    class LongLit(value: Long, position: Position) : LCLiteral<Long>(value, position)
    class FloatLit(value: Float, position: Position) : LCLiteral<Float>(value, position)
    class DoubleLit(value: Double, position: Position) : LCLiteral<Double>(value, position)
    class BoolLit(value: Boolean, position: Position) : LCLiteral<Boolean>(value, position)
    class StringLit(value: String, position: Position) : LCLiteral<String>(value, position)
    class CharLit(value: Char, position: Position) : LCLiteral<Char>(value, position)
    class NullLit(position: Position) : LCLiteral<Nothing?>(null, position)
}

class LCFunction(
    val name: String,
    val params: List<LCParameter>,
    val ret: LCTypeReference,
    val stmt: LCModule,
    val modifiers: List<Modifier>,
    position: Position
) : LCStatement(position) {

    override fun accept(visitor: LCVisitor) = visitor.declFunc(this)

    override fun toString(): String {
        return "LCFunction(name='$name', params=$params, ret=$ret, stmt=$stmt, modifiers=$modifiers)"
    }
}