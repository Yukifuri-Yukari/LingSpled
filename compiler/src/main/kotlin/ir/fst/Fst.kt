package yukifuri.lang.lingspled.compiler.ir.fst

import yukifuri.lang.lingspled.compiler.ast.*
import yukifuri.lang.lingspled.compiler.ast.LABinaryExpr.BinaryOperator
import yukifuri.lang.lingspled.compiler.general.LTypeParamDecl
import yukifuri.lang.lingspled.compiler.general.LTypeRef
import yukifuri.lang.lingspled.compiler.lexer.Position
import yukifuri.lang.lingspled.compiler.symbol.ClassSymbol
import yukifuri.lang.lingspled.compiler.symbol.FunctionSymbol
import yukifuri.lang.lingspled.compiler.symbol.ParameterSymbol
import yukifuri.lang.lingspled.compiler.symbol.Symbol
import yukifuri.lang.lingspled.compiler.util.Modifiers
import yukifuri.lang.lingspled.compiler.util.Operator

/**
 * FST（Formatted Syntax Tree）——语法级脱糖/规范化层。
 *
 * 节点形态照搬 LA 层（[yukifuri.lang.lingspled.compiler.ast]）的 Expression/Statement 二分 + Visitor。
 * 与 AST 不同处：脱糖在 AST→FST 的 lowering（见 [FstGenerator]）中顺手完成——主构造器 `val/var` 参数
 * 合成 [LFClassAttribute]、赋值拆分、for 迭代器脱糖等。共享类型（[LTypeRef]/[LTypeParamDecl]/
 * [Modifiers]/[Operator]/[BinaryOperator]）直接复用 general/util，不在本层重复镜像。
 */

sealed class LFExpression(open val position: Position) {
    abstract fun accept(visitor: LFVisitor)
}

sealed class LFStatement(position: Position) : LFExpression(position)

/** 表达式作语句时的包装（照搬 [LAExprStatement]）。 */
class LFExprStatement(val expr: LFExpression) : LFStatement(expr.position) {
    override fun accept(visitor: LFVisitor) = expr.accept(visitor)
    override fun toString() = expr.toString()
}

data class LFParameter(val name: String, val type: LTypeRef) {
    /** SymbolCollection 阶段填入。 */
    var symbol: ParameterSymbol? = null
}

data class LFArgument(val name: String?, val value: LFExpression)
data class LFAnnotation(val name: String, val args: List<LFArgument>)

sealed class LFLiteral<T>(val value: T, position: Position) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.literalExpr(this)

    class LFLInteger(value: Int, position: Position) : LFLiteral<Int>(value, position) {
        override fun toString() = "LFLInteger($value)"
    }

    class LFLLong(value: Long, position: Position) : LFLiteral<Long>(value, position) {
        override fun toString() = "LFLLong($value)"
    }

    class LFLFloat(value: Float, position: Position) : LFLiteral<Float>(value, position) {
        override fun toString() = "LFLFloat($value)"
    }

    class LFLDouble(value: Double, position: Position) : LFLiteral<Double>(value, position) {
        override fun toString() = "LFLDouble($value)"
    }

    class LFLBoolean(value: Boolean, position: Position) : LFLiteral<Boolean>(value, position) {
        override fun toString() = "LFLBoolean($value)"
    }

    class LFLNull(position: Position) : LFLiteral<Unit>(Unit, position) {
        override fun toString() = "LFLNull"
    }

    class LFLString(value: String, position: Position) : LFLiteral<String>(value, position) {
        override fun toString() = "LFLString(\"$value\")"
    }

    class LFThis(position: Position) : LFLiteral<Unit>(Unit, position) {
        override fun toString() = "LFThis"
    }
}

data class LFFieldAccessExpr(
    val receiver: LFExpression? = null,
    val field: String,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.fieldAccessExpr(this)
}

data class LFIndexAccessExpr(
    val receiver: LFExpression,
    val index: LFExpression,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.indexAccessExpr(this)
}

data class LFUnaryExpr(
    val op: Operator,
    val expr: LFExpression,
    val prefix: Boolean = true,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.unaryExpr(this)
}

/**
 * 自增/自减。由 [FstGenerator] 从 AST 的 `LAUnaryExpr`（op 为 [Operator.Inc]/[Operator.Dec]）脱糖，
 * 从通用一元表达式独立出来。[prefix] = true 为前缀 `++expr`（incdec expr），false 为后缀 `expr++`（expr incdec）。
 */
data class LFIncDec(
    val op: Operator,
    val target: LFExpression,
    val prefix: Boolean,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.incDecExpr(this)

    override fun toString() =
        if (prefix) "LFIncDec(${op.symbol}$target)" else "LFIncDec($target${op.symbol})"
}

data class LFBinaryExpr(
    val left: LFExpression,
    val op: BinaryOperator,
    val right: LFExpression,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.binaryExpr(this)
}

/**
 * 赋值语句。由 [FstGenerator] 从 AST 的赋值 `LABinaryExpr` 脱糖而来：`=` 直接拆为 `target = value`，
 * 复合赋值 `a += b` 展开为 `a = a + b`（[value] 内含合成的 [LFBinaryExpr]）。赋值在 LingSpled 中是语句。
 */
data class LFAssign(
    val target: LFExpression,
    val value: LFExpression,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.assignStmt(this)
}

data class LFInvokeExpr(
    val receiver: LFExpression,
    val arg: List<LFArgument>,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.invokeExpr(this)
}

data class LFIf(
    val condition: LFExpression,
    val then: LFModule,
    val elseBranch: LFModule?,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.ifExpr(this)
}

data class LFWhile(
    val condition: LFExpression,
    val body: LFModule,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.whileStmt(this)
}

data class LFDoWhile(
    val body: LFModule,
    val condition: LFExpression,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.doWhileStmt(this)
}

data class LFFor(
    val variable: String,
    val type: LTypeRef?,
    val iterable: LFExpression,
    val body: LFModule,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.forStmt(this)
}

data class LFTry(
    val body: LFModule,
    val catches: List<LFCatch>,
    val finallyBlock: LFModule?,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.tryExpr(this)
}

data class LFCatch(
    val name: String,
    val type: LTypeRef,
    val body: LFModule,
    val position: Position,
)

data class LFThrow(
    val expr: LFExpression,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.throwExpr(this)
}

data class LFBreak(override val position: Position) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.breakStmt(this)
}

data class LFContinue(override val position: Position) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.continueStmt(this)
}

data class LFLambda(
    val params: List<LFParameter>,
    val body: LFModule,
    override val position: Position,
) : LFExpression(position) {
    override fun accept(visitor: LFVisitor) = visitor.lambdaExpr(this)
}

data class LFModule(
    val statements: List<LFStatement>,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) {
        for (stmt in statements) stmt.accept(visitor)
    }
}

object LFFile {
    data class LFPackageDeclaration(
        val part: List<String>,
        override val position: Position,
    ) : LFStatement(position) {
        override fun accept(visitor: LFVisitor) = visitor.packageDecl(this)
        val packageFqn get() = part.joinToString(".") + "."
    }

    data class LFImportDeclaration(
        val part: List<String>,
        val wildcard: Boolean,
        val alias: String?,
        override val position: Position,
    ) : LFStatement(position) {
        override fun accept(visitor: LFVisitor) = visitor.importDecl(this)
        val importFqn get() = part.joinToString(".") + if (wildcard) ".*" else ""
    }
}

open class LFFunction(
    val annotations: List<LFAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Function>,
    val tp: List<LTypeParamDecl>,
    val receiver: LTypeRef?,
    val name: String,
    val params: List<LFParameter>,
    val ret: LTypeRef,
    val body: LFModule?,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.funcDecl(this)

    /** SymbolCollection 阶段填入。次构造器/init/deinit 子类共用此字段。 */
    var symbol: FunctionSymbol? = null

    override fun toString() = buildString {
        append("LFFunction(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        if (tp.isNotEmpty()) append("tp=$tp, ")
        if (receiver != null) append("receiver=$receiver, ")
        append("name=\"$name\", params=$params, ret=$ret")
        if (body != null) append(", body=$body")
        append(")")
    }

    data class LFReturnStmt(
        val expr: LFExpression,
        override val position: Position,
    ) : LFStatement(position) {
        override fun accept(visitor: LFVisitor) = visitor.ret(this)
    }
}

open class LFVariableDecl(
    val annotations: List<LFAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Property>,
    val mutable: Boolean,
    val name: String,
    val type: LTypeRef?,
    val init: LFExpression?,
    val delegator: LFExpression?,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.varDecl(this)

    /** SymbolCollection 阶段填入：类属性为 PropertySymbol，局部为 LocalVariableSymbol。 */
    var symbol: Symbol? = null

    override fun toString() = buildString {
        append("LFVariableDecl(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        append("mutable=$mutable, ")
        append("name='$name'")
        if (type != null) append(", type=$type")
        if (init != null) append(", init=$init")
        if (delegator != null) append(", delegator=$delegator")
        append(", position=$position")
        append(")")
    }
}

class LFClass(
    val annotations: List<LFAnnotation>,
    val access: Modifiers.Access,
    val modifiers: List<Modifiers.Class>,
    val name: String,
    val tp: List<LTypeParamDecl>,
    val superclass: LFInvokeExpr,
    val interfaces: List<LTypeRef>,
    val primaryCtor: LFPrimaryConstructor?,
    val ctors: List<LFClassConstructor>,
    val functions: List<LFFunction>,
    val attr: List<LFClassAttribute>,
    val inits: List<LFInitBlock>,
    val deinit: LFDeinitBlock?,
    val nested: List<LFClass>,
    override val position: Position,
) : LFStatement(position) {
    override fun accept(visitor: LFVisitor) = visitor.clsDecl(this)

    /** SymbolCollection 阶段填入。 */
    var symbol: ClassSymbol? = null

    override fun toString() = buildString {
        append("LFClass(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        append("name='$name'")
        if (tp.isNotEmpty()) append(", tp=$tp")
        append(", superclass=$superclass")
        if (interfaces.isNotEmpty()) append(", interfaces=$interfaces")
        if (primaryCtor != null) append(", primaryCtor=$primaryCtor")
        if (ctors.isNotEmpty()) append(", ctors=$ctors")
        if (functions.isNotEmpty()) append(", functions=$functions")
        if (attr.isNotEmpty()) append(", attr=$attr")
        if (inits.isNotEmpty()) append(", inits=$inits")
        if (deinit != null) append(", deinit=$deinit")
        if (nested.isNotEmpty()) append(", nested=$nested")
        append(")")
    }
}

data class LFPrimaryConstructor(
    val annotations: List<LFAnnotation>,
    val access: Modifiers.Access,
    val params: List<LFClassConstructorParameter>,
    val position: Position,
)

data class LFClassConstructorParameter(
    val annotations: List<LFAnnotation>,
    val access: Modifiers.Access,
    val mutable: Boolean?,
    val name: String,
    val type: LTypeRef,
    val default: LFExpression?,
    val position: Position,
)

sealed class LFConstructorDelegation {
    data class This(val args: List<LFArgument>) : LFConstructorDelegation()
    data class Super(val args: List<LFArgument>) : LFConstructorDelegation()
}

class LFClassConstructor(
    annotations: List<LFAnnotation>,
    access: Modifiers.Access,
    params: List<LFParameter>,
    val delegation: LFConstructorDelegation? = null,
    body: LFModule?,
    position: Position,
) : LFFunction(
    annotations, access, emptyList(), emptyList(),
    null, "<constructor>", params, LTypeRef.unit, body, position,
) {
    override fun toString() = buildString {
        append("LFClassConstructor(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        append("<constructor>, params=$params")
        if (delegation != null) append(", delegation=$delegation")
        if (body != null) append(", body=$body")
        append(")")
    }
}

class LFInitBlock(
    body: LFModule,
    position: Position,
) : LFFunction(
    emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
    null, "<init>", emptyList(), LTypeRef.unit, body, position,
) {
    override fun toString() = "LFInitBlock(body=$body)"
}

class LFDeinitBlock(
    body: LFModule,
    position: Position,
) : LFFunction(
    emptyList(), Modifiers.Access.Local, emptyList(), emptyList(),
    null, "<deinit>", emptyList(), LTypeRef.unit, body, position,
) {
    override fun toString() = "LFDeinitBlock(body=$body)"
}

class LFClassAttribute(
    annotations: List<LFAnnotation>,
    access: Modifiers.Access,
    modifiers: List<Modifiers.Property>,
    mutable: Boolean,
    name: String,
    type: LTypeRef?,
    init: LFExpression?,
    delegator: LFExpression?,
    val getter: LFFunction?,
    val setter: LFFunction?,
    /** 由主构造器 `val/var` 参数合成时为 true（lowering 阶段标记）。 */
    val synthesizedFromCtor: Boolean = false,
    position: Position,
) : LFVariableDecl(annotations, access, modifiers, mutable, name, type, init, delegator, position) {
    override fun toString() = buildString {
        append("LFClassAttribute(")
        if (annotations.isNotEmpty()) append("annotations=$annotations, ")
        if (access != Modifiers.Access.Local) append("access=$access, ")
        if (modifiers.isNotEmpty()) append("modifiers=$modifiers, ")
        if (synthesizedFromCtor) append("synthesizedFromCtor=true, ")
        append("mutable=$mutable, ")
        append("name='$name'")
        if (type != null) append(", type=$type")
        if (init != null) append(", init=$init")
        if (delegator != null) append(", delegator=$delegator")
        if (getter != null) append(", getter=$getter")
        if (setter != null) append(", setter=$setter")
        append(", position=$position")
        append(")")
    }
}

interface LFVisitor {

    fun packageDecl(decl: LFFile.LFPackageDeclaration)
    fun importDecl(decl: LFFile.LFImportDeclaration)
    fun funcDecl(decl: LFFunction)
    fun varDecl(decl: LFVariableDecl)
    fun clsDecl(decl: LFClass)

    fun ifExpr(expr: LFIf)
    fun whileStmt(stmt: LFWhile)
    fun doWhileStmt(stmt: LFDoWhile)
    fun forStmt(stmt: LFFor)
    fun tryExpr(expr: LFTry)
    fun throwExpr(expr: LFThrow)
    fun lambdaExpr(expr: LFLambda)
    fun breakStmt(stmt: LFBreak)
    fun continueStmt(stmt: LFContinue)

    fun literalExpr(expr: LFLiteral<*>)
    fun fieldAccessExpr(expr: LFFieldAccessExpr)
    fun indexAccessExpr(expr: LFIndexAccessExpr)
    fun unaryExpr(expr: LFUnaryExpr)
    fun incDecExpr(expr: LFIncDec)
    fun binaryExpr(expr: LFBinaryExpr)
    fun assignStmt(stmt: LFAssign)
    fun invokeExpr(expr: LFInvokeExpr)

    fun ret(stmt: LFFunction.LFReturnStmt)
}