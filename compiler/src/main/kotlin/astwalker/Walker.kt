package yukifuri.lang.lingspled.compiler.astwalker

import yukifuri.lang.lingspled.compiler.ast.base.Expression
import yukifuri.lang.lingspled.compiler.ast.clazz.LClass
import yukifuri.lang.lingspled.compiler.ast.expr.AsExpr
import yukifuri.lang.lingspled.compiler.ast.clazz.LClassConstructor
import yukifuri.lang.lingspled.compiler.ast.conditional.*
import yukifuri.lang.lingspled.compiler.ast.expr.*
import yukifuri.lang.lingspled.compiler.ast.function.LFunction
import yukifuri.lang.lingspled.compiler.ast.function.LFunctionCall
import yukifuri.lang.lingspled.compiler.ast.function.LambdaExpr
import yukifuri.lang.lingspled.compiler.ast.function.Return
import yukifuri.lang.lingspled.compiler.ast.literal.*
import yukifuri.lang.lingspled.compiler.ast.module.ImportDeclaration
import yukifuri.lang.lingspled.compiler.ast.module.Module
import yukifuri.lang.lingspled.compiler.ast.module.PackageDeclaration
import yukifuri.lang.lingspled.compiler.ast.variable.VariableAssign
import yukifuri.lang.lingspled.compiler.ast.variable.VariableDecl
import yukifuri.lang.lingspled.compiler.ast.variable.VariableGet
import yukifuri.lang.lingspled.compiler.ast.visitor.AstVisitor
import yukifuri.lang.lingspled.compiler.astwalker.LValue.*
import yukifuri.lang.lingspled.compiler.ast.util.Operator as Op

/**
 * Stack-based AST interpreter.
 *
 * Each visitor method evaluates its node and pushes the result onto [stack].
 * Compound nodes pop their children's results before pushing their own.
 */
class Walker : AstVisitor {
    private val stack = ArrayDeque<LValue>()
    private var env = Environment()
    private val functions = mutableMapOf<String, LFunction>()
    private val classes = mutableMapOf<String, LClass>()
    private val thisStack = ArrayDeque<LObject>()

    // Each entry is the defer list for one function call frame (LIFO execution on exit)
    private val deferStack = ArrayDeque<ArrayDeque<Expression>>()

    fun walk(ast: Module) {
        registerBuiltins()
        ast.accept(this)

        if (functions.containsKey("main")) {
            callWithDefers {
                for (statement in functions["main"]!!.body.statements) {
                    statement.accept(this)
                }
            }
        }
    }

    override fun literal(literal: Literal<*>) {
        stack.addLast(
            when (literal) {
                is IntegerLiteral -> LInt(literal.value)
                is DecimalLiteral -> LDecimal(literal.value)
                is StringLiteral -> LString(literal.value)
                is BooleanLiteral -> LBool(literal.value)
                is NullLiteral -> LNull
                else -> throw WalkerException("Unknown literal type: ${literal::class.simpleName}")
            }
        )
    }

    override fun variableGet(expr: VariableGet) {
        val value = env.tryGet(expr.name)
            ?: thisStack.lastOrNull()?.fields?.get(expr.name)
            ?: env.get(expr.name) // 触发正常的"未定义变量"报错
        stack.addLast(value)
    }

    override fun variableDecl(decl: VariableDecl) {
        val value = if (decl.initialize != null) {
            decl.initialize.accept(this)
            stack.removeLast()
        } else {
            LUnit
        }
        env.define(decl.name, value, decl.isConstant)
        stack.addLast(LUnit)
    }

    override fun visitFor(forLoop: For) {
        val outerEnv = env
        env = env.child()

        forLoop.init.accept(this)
        stack.removeLast()

        try {
            while (true) {
                forLoop.cond.accept(this)
                if (!stack.removeLast().asBool()) break

                try {
                    forLoop.body.accept(this)
                } catch (_: ContinueSignal) { /* fall through to update */
                }

                forLoop.update.accept(this)
                stack.removeLast()
            }
        } catch (_: BreakSignal) { /* exit loop */
        }

        env = outerEnv
        stack.addLast(LUnit)
    }

    override fun visitWhile(whl: While) {
        val outerEnv = env
        env = env.child()

        try {
            while (true) {
                whl.cond.accept(this)
                if (!stack.removeLast().asBool()) break

                try {
                    whl.body.accept(this)
                } catch (_: ContinueSignal) { /* next iteration */
                }
            }
        } catch (_: BreakSignal) { /* exit loop */
        }

        env = outerEnv
        stack.addLast(LUnit)
    }

    override fun loopCtrl(controller: LoopControl) {
        if (controller is Continue) throw ContinueSignal()
        else throw BreakSignal()
    }

    override fun visitIf(condIf: If) {
        condIf.cond.accept(this)
        val condition = stack.removeLast()

        val stackSizeBefore = stack.size

        if (condition.asBool()) {
            condIf.then.accept(this)
        } else condIf.els?.accept(this)

        // If the branch produced a value, keep it (if-as-expression).
        // Otherwise push Unit (if-as-statement / no else branch taken).
        if (stack.size == stackSizeBefore) {
            stack.addLast(LUnit)
        }
    }

    override fun functionReturn(funcReturn: Return) {
        funcReturn.expr.accept(this)
        throw ReturnSignal(stack.removeLast())
    }

    override fun variableAssign(assign: VariableAssign) {
        assign.value.accept(this)
        val rhs = stack.removeLast()
        // 优先写本地作用域；若不存在则尝试写 this 的字段
        val thisObj = thisStack.lastOrNull()
        if (env.tryGet(assign.name) == null && thisObj != null && thisObj.fields.containsKey(assign.name)) {
            val newValue = if (assign.operator == Op.Assign) rhs
            else evalBinary(thisObj.fields[assign.name]!!, assign.operator.extractAssignment(), rhs)
            thisObj.fields[assign.name] = newValue
            stack.addLast(LUnit)
            return
        }
        if (assign.operator == Op.Assign) {
            env.set(assign.name, rhs)
        } else {
            env.set(assign.name, evalBinary(env.get(assign.name), assign.operator.extractAssignment(), rhs))
        }
        stack.addLast(LUnit)
    }

    override fun clazz(klass: LClass) {
        classes[klass.name] = klass
        stack.addLast(LUnit)
    }

    override fun pkg(declaration: PackageDeclaration) {
    }

    override fun importDecl(decl: ImportDeclaration) {}

    override fun thisRef(expr: ThisExpr) {
        stack.addLast(thisStack.lastOrNull() ?: throw WalkerException("'this' used outside of a class method"))
    }

    override fun fieldAssign(expr: FieldAssign) {
        expr.receiver.accept(this)
        val obj = stack.removeLast() as? LObject
            ?: throw WalkerException("Cannot assign field '${expr.fieldName}' on a non-object")
        expr.value.accept(this)
        obj.fields[expr.fieldName] = stack.removeLast()
        stack.addLast(LUnit)
    }

    override fun fieldAccess(expr: FieldAccess) {
        expr.receiver.accept(this)
        val recv = stack.removeLast()
        if (expr.safe && recv == LNull) { stack.addLast(LNull); return }
        if (recv is LArray) {
            stack.addLast(
                when (expr.fieldName) {
                    "size" -> LInt(recv.elements.size)
                    else -> throw WalkerException("No field '${expr.fieldName}' on Array")
                }
            )
            return
        }
        val obj = recv as? LObject
            ?: throw WalkerException("Cannot access field '${expr.fieldName}' on a non-object")
        stack.addLast(
            obj.fields[expr.fieldName]
                ?: throw WalkerException("No field '${expr.fieldName}' on ${obj.cls.name}")
        )
    }

    override fun methodCall(expr: MethodCall) {
        if (expr.receiver is VariableGet &&
            env.tryGet(expr.receiver.name) == null &&
            thisStack.lastOrNull()?.fields?.containsKey(expr.receiver.name) != true
        ) {
            val cls = classes[expr.receiver.name]
            if (cls != null) {
                val clsMethods = cls.statements.filterIsInstance<LFunction>()
                    .filterNot { it is LClassConstructor }
                val method = clsMethods.find { it.name == expr.methodName && "static" in it.modifier }

                if (method != null) {
                    val args = expr.arguments.map { it.accept(this); stack.removeLast() }
                    if ("native" in method.modifier) {
                        stack.addLast(handleNative(args))
                        return
                    }
                    val callerEnv = env
                    env = env.child()
                    for ((i, param) in method.arguments.withIndex()) {
                        val value = when {
                            i < args.size -> args[i]
                            param.defaultValue != null -> {
                                param.defaultValue.accept(this); stack.removeLast()
                            }
                            else -> throw WalkerException(
                                "Static method '${method.name}': missing argument for parameter '${param.name}'"
                            )
                        }
                        env.define(param.name, value)
                    }
                    val stackBase = stack.size
                    val result = callWithDefers { method.body.accept(this) }
                    cleanStack(stackBase)
                    env = callerEnv
                    stack.addLast(result)
                    return
                }

                val staticField = cls.variables.find { it.name == expr.methodName && "static" in it.modifier }
                if (staticField != null) {
                    staticField.initialize?.accept(this) ?: throw WalkerException(
                        "Static field '${expr.methodName}' has no initializer"
                    )
                    val fieldValue = stack.removeLast()
                    if (fieldValue is LLambda) {
                        val args = expr.arguments.map { it.accept(this); stack.removeLast() }
                        if (args.size != fieldValue.arguments.size) {
                            throw WalkerException(
                                "Lambda '${expr.methodName}' requires ${fieldValue.arguments.size} arguments, got ${args.size}"
                            )
                        }
                        val callerEnv = env
                        env = fieldValue.closure.child()
                        for ((i, arg) in fieldValue.arguments.withIndex())
                            env.define(arg.name, args[i])
                        val stackBase = stack.size
                        val explicitResult = callWithDefers { fieldValue.body.accept(this) }
                        val returnValue = if (explicitResult != LUnit) explicitResult
                        else if (stack.size > stackBase) stack.last()
                        else LUnit
                        cleanStack(stackBase)
                        env = callerEnv
                        stack.addLast(returnValue)
                        return
                    } else {
                        throw WalkerException(
                            "Static field '${expr.methodName}' is not callable (type: ${fieldValue::class.simpleName})"
                        )
                    }
                }

                throw WalkerException("No static method or static field '${expr.methodName}' on ${cls.name}")
            }
        }

        expr.receiver.accept(this)
        val obj = stack.removeLast()
        if (expr.safe && obj == LNull) { stack.addLast(LNull); return }

        if (obj is LArray) {
            val args = expr.arguments.map { it.accept(this); stack.removeLast() }
            stack.addLast(
                when (expr.methodName) {
                    "get" -> obj.elements.getOrNull(args[0].asInt())
                        ?: throw WalkerException("Array index ${args[0].asInt()} out of bounds")
                    "set" -> { obj.elements[args[0].asInt()] = args[1]; LUnit }
                    "contains" -> LBool(args[0] in obj.elements)
                    "isEmpty" -> LBool(obj.elements.isEmpty())
                    "toString" -> LString(obj.toString())
                    else -> throw WalkerException("No method '${expr.methodName}' on Array")
                }
            )
            return
        }

        if (expr.methodName == "toString" && expr.arguments.isEmpty()) {
            stack.addLast(LString(formatValue(obj)))
            return
        }

        obj as? LObject ?: throw WalkerException("Cannot call method '${expr.methodName}' on a non-object: $obj")
        val args = expr.arguments.map { it.accept(this); stack.removeLast() }
        val cls = classes[obj.cls.name]!!

        // 沿继承链递归查找方法：先类自身，再父类（递归），最后接口 default 方法。
        var ifaceOwner: LClass? = null
        fun LClass.findMethod(name: String): LFunction? {
            statements.filterIsInstance<LFunction>()
                .filterNot { it is LClassConstructor }
                .find { it.name == name }
                ?.let { return it }
            for (superType in inheritances) {
                val superCls = classes[superType.name] ?: continue
                if (superCls.isInterface) {
                    val m = superCls.statements.filterIsInstance<LFunction>()
                        .find { it.name == name && "abstract" !in it.modifier }
                    if (m != null) { ifaceOwner = superCls; return m }
                } else {
                    val m = superCls.findMethod(name)
                    if (m != null) return m
                }
            }
            return null
        }
        val method = cls.findMethod(expr.methodName)
            ?: throw WalkerException("No method '${expr.methodName}' on ${obj.cls.name}")

        // 若方法来自接口，把接口的默认字段临时注入到 obj.fields
        val injectedKeys = mutableListOf<String>()
        ifaceOwner?.variables?.forEach { field ->
            if (field.name !in obj.fields) {
                val value = if (field.initialize != null) {
                    field.initialize.accept(this)
                    stack.removeLast()
                } else LUnit
                obj.fields[field.name] = value
                injectedKeys.add(field.name)
            }
        }

        val callerEnv = env
        env = env.child()
        env.define("this", obj)
        thisStack.addLast(obj)
        for ((i, param) in method.arguments.withIndex()) {
            val value = when {
                i < args.size -> args[i]
                param.defaultValue != null -> {
                    param.defaultValue.accept(this); stack.removeLast()
                }
                else -> throw WalkerException(
                    "Method '${method.name}': missing argument for parameter '${param.name}'"
                )
            }
            env.define(param.name, value)
        }
        val stackBase = stack.size
        val result = callWithDefers { method.body.accept(this) }
        cleanStack(stackBase)

        // 清理临时注入的接口字段
        injectedKeys.forEach { obj.fields.remove(it) }

        thisStack.removeLast()
        env = callerEnv
        stack.addLast(result)
    }

    override fun binaryOp(expr: BinaryExpr) {
        // Assignment operators are special: LHS is a variable name, not a value
        if (expr.op.isAssignment()) {
            handleAssignment(expr)
            return
        }

        // Short-circuit for logical operators
        if (expr.op == Op.And || expr.op == Op.Or) {
            handleLogical(expr)
            return
        }

        if (expr.op == Op.Is) { handleIsCheck(expr); return }
        if (expr.op == Op.NotIs) { handleIsCheck(expr, negate = true); return }
        if (expr.op == Op.In) { handleInCheck(expr); return }
        if (expr.op == Op.NotIn) { handleInCheck(expr, negate = true); return }
        if (expr.op == Op.Elvis) { handleElvis(expr); return }

        expr.l.accept(this)
        expr.r.accept(this)
        val r = stack.removeLast()
        val l = stack.removeLast()

        stack.addLast(evalBinary(l, expr.op, r))
    }

    override fun unaryOp(expr: UnaryExpr) {
        expr.expr.accept(this)
        val operand = stack.removeLast()

        // ++/-- 需要写回变量（val 会在 env.set 里抛错）
        if (expr.op == Op.Increment || expr.op == Op.Decrement) {
            val newValue = when (expr.op) {
                Op.Increment -> when (operand) {
                    is LInt -> LInt(operand.value + 1)
                    is LDecimal -> LDecimal(operand.value + 1)
                    else -> throw WalkerException("Cannot increment $operand")
                }

                else -> when (operand) {
                    is LInt -> LInt(operand.value - 1)
                    is LDecimal -> LDecimal(operand.value - 1)
                    else -> throw WalkerException("Cannot decrement $operand")
                }
            }
            when (val target = expr.expr) {
                is VariableGet -> env.set(target.name, newValue)
                is FieldAccess -> {
                    target.receiver.accept(this)
                    val obj = stack.removeLast() as? LObject
                        ?: throw WalkerException("Cannot increment field on a non-object")
                    obj.fields[target.fieldName] = newValue
                }

                else -> throw WalkerException("Invalid target for ++/--")
            }
            stack.addLast(operand) // 后缀语义：返回旧值
            return
        }

        stack.addLast(
            when (expr.op) {
                Op.Not -> LBool(!operand.asBool())
                Op.BitNot -> LInt(operand.asInt().inv())
                Op.Sub -> when (operand) {
                    is LInt -> LInt(-operand.value)
                    is LDecimal -> LDecimal(-operand.value)
                    else -> throw WalkerException("Cannot negate $operand")
                }
                Op.Add -> operand
                Op.NotNull -> if (operand == LNull)
                    throw WalkerException("Non-null assertion failed: value is null")
                else operand
                else -> throw WalkerException("Unknown unary operator: ${expr.op}")
            }
        )
    }

    override fun lambdaExpr(expr: LambdaExpr) {
        // 捕获当前 env 作为闭包
        stack.addLast(LLambda(expr.arguments, expr.body, env))
    }

    override fun invokeExpr(expr: InvokeExpr) {
        expr.callee.accept(this)
        val callee = stack.removeLast() as? LLambda
            ?: throw WalkerException("Cannot invoke a non-lambda value")
        val args = expr.arguments.map { it.accept(this); stack.removeLast() }
        if (args.size != callee.arguments.size) {
            throw WalkerException(
                "Lambda requires ${
                    callee.arguments.size
                } arguments, but ${args.size} were given"
            )
        }
        val callerEnv = env
        env = callee.closure.child()
        for ((i, arg) in callee.arguments.withIndex())
            env.define(arg.name, args[i])
        val stackBase = stack.size
        val explicitResult = callWithDefers { callee.body.accept(this) }
        // lambda 隐式返回: 有显式 return 用 explicitResult, 否则取栈顶 (最后一个表达式的值)
        val returnValue = if (explicitResult != LUnit) {
            explicitResult
        } else if (stack.size > stackBase) {
            stack.last()
        } else {
            LUnit
        }
        cleanStack(stackBase)
        env = callerEnv
        stack.addLast(returnValue)
    }

    override fun functionDecl(f: LFunction) {
        functions[f.name] = f
        stack.addLast(LUnit)
    }

    override fun functionCall(f: LFunctionCall) {
        // Evaluate all arguments left-to-right, push results
        val args = mutableListOf<LValue>()
        for (arg in f.arguments) {
            arg.accept(this)
            args.add(stack.removeLast())
        }

        // Check builtins first
        val builtin = builtins[f.name]
        if (builtin != null) {
            stack.addLast(builtin(args))
            return
        }

        // Class instantiation
        val cls = classes[f.name]
        if (cls != null) {
            stack.addLast(instantiate(cls, args))
            return
        }

        // Implicit this method call: describe() inside a class method means this.describe()
        val thisObj = thisStack.lastOrNull()
        if (thisObj != null) {
            val thisCls = classes[thisObj.cls.name]!!
            val method = thisCls.statements.filterIsInstance<LFunction>()
                .find { it !is LClassConstructor && it.name == f.name }
            if (method != null) {
                val callerEnv = env
                env = env.child()
                thisStack.addLast(thisObj)
                for ((i, param) in method.arguments.withIndex())
                    env.define(param.name, args[i])
                val stackBase = stack.size
                val result = callWithDefers { method.body.accept(this) }
                cleanStack(stackBase)
                thisStack.removeLast()
                env = callerEnv
                stack.addLast(result)
                return
            }
        }

        val maybeLambda = env.tryGet(f.name)
        if (maybeLambda is LLambda) {
            if (args.size != maybeLambda.arguments.size) {
                throw WalkerException(
                    "Lambda '${f.name}' requires ${
                        maybeLambda.arguments.size
                    } arguments, actually ${args.size} were given"
                )
            }
            val callerEnv = env
            env = maybeLambda.closure.child()   // 父作用域是闭包, 不是调用者
            for ((i, arg) in maybeLambda.arguments.withIndex())
                env.define(arg.name, args[i])
            val stackBase = stack.size
            val explicitResult = callWithDefers { maybeLambda.body.accept(this) }
            val returnValue = if (explicitResult != LUnit) explicitResult
            else if (stack.size > stackBase) stack.last()
            else LUnit
            cleanStack(stackBase)
            env = callerEnv
            stack.addLast(returnValue)
            return
        }

        // User-defined function
        val func = functions[f.name]
            ?: throw WalkerException("Undefined function '${f.name}'")

        if ("native" in func.modifier) {
            stack.addLast(handleNative(args))
            return
        }

        // Create new scope and bind parameters (with default values)
        val callerEnv = env
        env = env.child()
        for ((i, param) in func.arguments.withIndex()) {
            val value = when {
                i < args.size -> args[i]
                param.defaultValue != null -> {
                    param.defaultValue.accept(this); stack.removeLast()
                }

                else -> throw WalkerException(
                    "Function '${f.name}': missing argument for parameter '${param.name}'"
                )
            }
            env.define(param.name, value)
        }
        if (args.size > func.arguments.size) {
            throw WalkerException(
                "Function '${f.name}' expects at most ${func.arguments.size} argument(s), got ${args.size}"
            )
        }

        // Execute body — catch ReturnSignal for early return
        // Save stack depth so we can discard intermediate values left by statements
        val stackBase = stack.size
        val result = callWithDefers { func.body.accept(this) }

        // Clean up any stale values statements left on the stack
        cleanStack(stackBase)

        env = callerEnv
        stack.addLast(result)
    }

    override fun indexAccess(expr: IndexAccess) {
        expr.receiver.accept(this)
        val recv = stack.removeLast()
        expr.index.accept(this)
        val idx = stack.removeLast().asInt()
        stack.addLast(
            when (recv) {
                is LArray -> recv.elements.getOrNull(idx)
                    ?: throw WalkerException("Array index $idx out of bounds (size=${recv.elements.size})")

                else -> throw WalkerException("Cannot index into $recv")
            }
        )
    }

    override fun indexAssign(expr: IndexAssign) {
        expr.receiver.accept(this)
        val recv = stack.removeLast() as? LArray
            ?: throw WalkerException("Cannot index-assign into a non-array")
        expr.index.accept(this)
        val idx = stack.removeLast().asInt()
        expr.value.accept(this)
        val value = stack.removeLast()
        if (idx < 0 || idx >= recv.elements.size)
            throw WalkerException("Array index $idx out of bounds (size=${recv.elements.size})")
        recv.elements[idx] = value
        stack.addLast(LUnit)
    }

    override fun visitAs(expr: AsExpr) {
        expr.expr.accept(this)
        // 运行时透传值，类型标注仅供静态分析
    }

    override fun visitDefer(defer: Defer) {
        if (deferStack.isEmpty()) throw WalkerException("'defer' used outside of a function")
        deferStack.last().addLast(defer.expr)
        stack.addLast(LUnit)
    }

    /**
     * Runs [body] inside a new defer frame.
     * Deferred expressions are executed in LIFO order when the frame exits,
     * regardless of whether exit is normal or via [ReturnSignal].
     * [BreakSignal]/[ContinueSignal] are re-thrown after running defers.
     */
    private fun callWithDefers(body: () -> Unit): LValue {
        deferStack.addLast(ArrayDeque())
        var result: LValue = LUnit
        var rethrow: Throwable? = null
        try {
            body()
        } catch (ret: ReturnSignal) {
            result = ret.value
        } catch (t: Throwable) {
            rethrow = t
        }
        val defers = deferStack.removeLast()
        val stackBase = stack.size
        for (expr in defers.reversed()) {
            expr.accept(this)
            cleanStack(stackBase)
        }
        if (rethrow != null) throw rethrow
        return result
    }

    private fun cleanStack(base: Int) { while (stack.size > base) stack.removeLast() }

    private fun handleNative(args: List<LValue>): LValue = when (val op = args[0]) {
        is LString -> when (op.value) {
            "getStdout" -> classes["PrintStream"]?.let { instantiate(it, emptyList()) }
                ?: throw WalkerException("PrintStream class not registered — ensure lspled.lang is imported")
            else        -> throw WalkerException("Unknown native operation: '${op.value}'")
        }
        else -> throw WalkerException("Native operation key must be a String, got ${op::class.simpleName}")
    }

    private val builtins = mutableMapOf<String, (List<LValue>) -> LValue>()

    private fun registerBuiltins() {
        builtins["println"] = { args ->
            println(args.joinToString(" ") { formatValue(it) })
            LUnit
        }
        builtins["print"] = { args ->
            print(args.joinToString(" ") { formatValue(it) })
            LUnit
        }
        builtins["toString"] = { args ->
            if (args.size != 1) throw WalkerException("toString expects 1 argument")
            LString(args[0].toString())
        }
        builtins["toInt"] = { args ->
            if (args.size != 1) throw WalkerException("toInt expects 1 argument")
            LInt(args[0].asInt())
        }
        builtins["toDecimal"] = { args ->
            if (args.size != 1) throw WalkerException("toDecimal expects 1 argument")
            LDecimal(args[0].asDecimal())
        }
        builtins["arrayOf"] = { args ->
            LArray(args.toMutableList())
        }
        builtins["Array"] = { args ->
            if (args.size != 1) throw WalkerException("Array(size) expects 1 argument")
            LArray(MutableList(args[0].asInt()) { LUnit })
        }
    }

    private fun handleAssignment(expr: BinaryExpr) {
        val target = expr.l
        expr.r.accept(this)
        val rhs = stack.removeLast()

        when (target) {
            is VariableGet -> {
                val newValue = if (expr.op == Op.Assign) rhs
                else evalBinary(env.get(target.name), expr.op.extractAssignment(), rhs)
                env.set(target.name, newValue)
                stack.addLast(newValue)
            }

            is FieldAccess -> {
                target.receiver.accept(this)
                val obj = stack.removeLast() as? LObject
                    ?: throw WalkerException("Cannot assign field '${target.fieldName}' on a non-object")
                val newValue = if (expr.op == Op.Assign) rhs
                else evalBinary(obj.fields[target.fieldName] ?: LUnit, expr.op.extractAssignment(), rhs)
                obj.fields[target.fieldName] = newValue
                stack.addLast(newValue)
            }

            else -> throw WalkerException("Invalid assignment target: ${target::class.simpleName}")
        }
    }

    private fun handleLogical(expr: BinaryExpr) {
        expr.l.accept(this)
        val l = stack.removeLast()

        if (expr.op == Op.And) {
            if (!l.asBool()) {
                stack.addLast(LBool(false))
                return
            }
            expr.r.accept(this)
            stack.addLast(LBool(stack.removeLast().asBool()))
        } else { // Or
            if (l.asBool()) {
                stack.addLast(LBool(true))
                return
            }
            expr.r.accept(this)
            stack.addLast(LBool(stack.removeLast().asBool()))
        }
    }

    private fun handleIsCheck(expr: BinaryExpr, negate: Boolean = false) {
        expr.l.accept(this)
        val value = stack.removeLast()
        val typeName = (expr.r as? VariableGet)?.name
            ?: throw WalkerException("Right side of 'is'/'!is' must be a type shortenName")
        val result = value is LObject && value.cls.name == typeName
        stack.addLast(LBool(if (negate) !result else result))
    }

    private fun handleInCheck(expr: BinaryExpr, negate: Boolean = false) {
        expr.l.accept(this)
        val value = stack.removeLast()
        expr.r.accept(this)
        val collection = stack.removeLast()
        val result = when (collection) {
            is LArray -> value in collection.elements
            is LRange -> value.asInt() in collection.start..collection.end
            else -> throw WalkerException("'in'/'!in' requires a collection or range on the right side, got $collection")
        }
        stack.addLast(LBool(if (negate) !result else result))
    }

    private fun handleElvis(expr: BinaryExpr) {
        expr.l.accept(this)
        val left = stack.removeLast()
        if (left is LNull) {
            expr.r.accept(this) // right side is already pushed by accept
        } else {
            stack.addLast(left)
        }
    }

    override fun visitWhen(expr: When) {
        val subject: LValue? = expr.expr?.let {
            it.accept(this)
            stack.removeLast()
        }

        val stackSizeBefore = stack.size
        var matched = false

        for (branch in expr.branches) {
            val outerEnv = env
            env = env.child()

            val matches: Boolean = when (branch) {
                is When.TypeBranch -> {
                    val obj = subject as? LObject
                    if (obj != null && obj.cls.name == branch.typename) {
                        if (branch.destructured.isNotEmpty()) {
                            val fields = classes[branch.typename]?.variables ?: emptyList()
                            branch.destructured.forEachIndexed { i, name ->
                                val fieldName = fields.getOrNull(i)?.name ?: name
                                env.define(name, obj.fields[fieldName] ?: LUnit)
                            }
                        }
                        true
                    } else false
                }
                is When.ExprBranch -> {
                    branch.expr.accept(this)
                    val branchVal = stack.removeLast()
                    if (subject != null) subject == branchVal else branchVal.asBool()
                }
            }

            if (matches) {
                val guardPassed = branch.guard?.let {
                    it.accept(this)
                    stack.removeLast().asBool()
                } ?: true

                if (guardPassed) {
                    branch.module.accept(this)
                    env = outerEnv
                    matched = true
                    break
                }
            }

            env = outerEnv
        }

        if (!matched) {
            expr.elseBranch?.accept(this)
        }

        if (stack.size == stackSizeBefore) {
            stack.addLast(LUnit)
        }
    }

    private fun evalBinary(l: LValue, op: Op, r: LValue): LValue {
        // String concatenation
        if (op == Op.Add && (l is LString || r is LString)) {
            return LString(formatValue(l) + formatValue(r))
        }

        // String repetition: "abc" * 3
        if (op == Op.Mul && l is LString && r is LInt) {
            return LString(l.value.repeat(r.value))
        }

        // Equality works on all types
        if (op == Op.Eq) return LBool(l == r)
        if (op == Op.Ne) return LBool(l != r)

        if (op == Op.Range) return LRange(l.asInt(), r.asInt())

        // Numeric operations
        if (!l.isNumeric() || !r.isNumeric()) {
            throw WalkerException("Cannot apply '$op' to $l and $r")
        }

        val useDecimal = l is LDecimal || r is LDecimal

        return if (useDecimal) {
            val a = l.asDecimal()
            val b = r.asDecimal()
            when (op) {
                Op.Add -> LDecimal(a + b)
                Op.Sub -> LDecimal(a - b)
                Op.Mul -> LDecimal(a * b)
                Op.Div -> LDecimal(a / b)
                Op.Mod -> LDecimal(a % b)
                Op.Lt -> LBool(a < b)
                Op.Gt -> LBool(a > b)
                Op.Le -> LBool(a <= b)
                Op.Ge -> LBool(a >= b)
                else -> throw WalkerException("Operator '$op' not supported for decimals")
            }
        } else {
            val a = l.asInt()
            val b = r.asInt()
            when (op) {
                Op.Add -> LInt(a + b)
                Op.Sub -> LInt(a - b)
                Op.Mul -> LInt(a * b)
                Op.Div -> LInt(a / b)
                Op.Mod -> LInt(a % b)
                Op.Lt -> LBool(a < b)
                Op.Gt -> LBool(a > b)
                Op.Le -> LBool(a <= b)
                Op.Ge -> LBool(a >= b)
                Op.Shl -> LInt(a shl b)
                Op.Shr -> LInt(a shr b)
                Op.BitAnd -> LInt(a and b)
                Op.BitOr -> LInt(a or b)
                Op.BitXor -> LInt(a xor b)
                else -> throw WalkerException("Operator '$op' not supported for integers")
            }
        }
    }

    private fun instantiate(cls: LClass, args: List<LValue>): LObject {
        val obj = LObject(cls)
        val savedEnv = env
        env = env.child()
        env.define("this", obj)
        thisStack.addLast(obj)

        // Find matching constructor (by parameter count, allowing defaults)
        val ctors = cls.statements.filterIsInstance<LClassConstructor>()
        val ctor = ctors.find { it.arguments.size >= args.size }

        if (ctor == null && (ctors.isNotEmpty() || args.isNotEmpty())) {
            throw WalkerException(
                "No matching constructor for '${cls.name}' with ${args.size} argument(s)"
            )
        }

        if (ctor != null) {
            // Step 1: Build complete argument list with defaults, binding each parameter as we go
            val resolvedArgs = mutableListOf<LValue>()
            for ((i, param) in ctor.arguments.withIndex()) {
                val argValue = if (i < args.size) {
                    // Use provided argument
                    args[i]
                } else if (param.defaultValue != null) {
                    // Evaluate default value in current environment (previous params already bound)
                    param.defaultValue.accept(this)
                    stack.removeLast()
                } else {
                    throw WalkerException("Missing argument for parameter '${param.name}'")
                }

                // Bind this parameter immediately so later defaults can reference it
                env.define(param.name, argValue)
                resolvedArgs.add(argValue)
            }

            // Step 2: Initialize fields
            for (field in cls.variables) {
                val isCtorParam = ctor.arguments.any { it.name == field.name }
                obj.fields[field.name] = if (isCtorParam) {
                    // Constructor property parameter - use the resolved argument value
                    val paramIndex = ctor.arguments.indexOfFirst { it.name == field.name }
                    resolvedArgs[paramIndex]
                } else if (field.initialize != null) {
                    // Regular field with initializer - evaluate in current env
                    field.initialize.accept(this)
                    stack.removeLast()
                } else {
                    LUnit
                }
            }

            // Step 3: Execute constructor body
            val stackBase = stack.size
            try {
                ctor.body.accept(this)
            } catch (_: ReturnSignal) {
            }
            cleanStack(stackBase)
        } else {
            // No constructor found, just initialize fields
            for (field in cls.variables) {
                obj.fields[field.name] = if (field.initialize != null) {
                    field.initialize.accept(this)
                    stack.removeLast()
                } else {
                    LUnit
                }
            }
        }

        // Step 4: initialize parent-class fields via the stored super-constructor args.
        // Handles `class Dog(val name: String) : Animal(name)` — writes Animal's fields
        // onto the same obj so that inherited methods (e.g. speak()) can access them.
        val parentType = cls.inheritances.firstOrNull { classes[it.name]?.isInterface == false }
        if (parentType != null) {
            val parentCls = classes[parentType.name]
            if (parentCls != null) {
                val superArgs = cls.superCtorArgs.map { it.accept(this); stack.removeLast() }
                initSuperFields(obj, parentCls, superArgs)
            }
        }

        thisStack.removeLast()
        env = savedEnv
        return obj
    }

    /**
     * Recursively initialize [obj] with fields from [cls] (a parent class) using [args].
     * Writes directly into [obj].fields — no new object is created.
     * Fields already set by a child class are never overwritten.
     */
    private fun initSuperFields(obj: LObject, cls: LClass, args: List<LValue>) {
        val ctors = cls.statements.filterIsInstance<LClassConstructor>()
        val ctor  = ctors.find { it.arguments.size >= args.size }

        if (ctor != null) {
            val resolvedArgs = mutableListOf<LValue>()
            for ((i, param) in ctor.arguments.withIndex()) {
                val v = if (i < args.size) args[i]
                else if (param.defaultValue != null) { param.defaultValue.accept(this); stack.removeLast() }
                else throw WalkerException("Super-constructor '${cls.name}': missing arg for '${param.name}'")
                env.define(param.name, v)
                resolvedArgs.add(v)
            }
            for (field in cls.variables) {
                if (field.name in obj.fields) continue   // child already owns this field
                val isCtorParam = ctor.arguments.any { it.name == field.name }
                obj.fields[field.name] = if (isCtorParam)
                    resolvedArgs[ctor.arguments.indexOfFirst { it.name == field.name }]
                else if (field.initialize != null) { field.initialize.accept(this); stack.removeLast() }
                else LUnit
            }
            val stackBase = stack.size
            try { ctor.body.accept(this) } catch (_: ReturnSignal) {}
            cleanStack(stackBase)
        } else {
            for (field in cls.variables) {
                if (field.name in obj.fields) continue
                obj.fields[field.name] = if (field.initialize != null) {
                    field.initialize.accept(this); stack.removeLast()
                } else LUnit
            }
        }

        // Recurse to grandparent class.
        val grandParentType = cls.inheritances.firstOrNull { classes[it.name]?.isInterface == false }
        if (grandParentType != null) {
            val grandParentCls = classes[grandParentType.name]
            if (grandParentCls != null) {
                val superArgs = cls.superCtorArgs.map { it.accept(this); stack.removeLast() }
                initSuperFields(obj, grandParentCls, superArgs)
            }
        }
    }

    private fun formatValue(value: LValue): String {
        if (value is LObject) {
            val toStringMethod = value.cls.statements.filterIsInstance<LFunction>()
                .find { it.name == "toString" && it.arguments.isEmpty() }
            if (toStringMethod != null) {
                return callToStringOnObject(value, toStringMethod)
            }
        }
        return value.toString()
    }

    private fun callToStringOnObject(obj: LObject, toStringMethod: LFunction): String {
        val callerEnv = env
        env = env.child()
        env.define("this", obj)
        thisStack.addLast(obj)

        val stackBase = stack.size
        val result = try {
            toStringMethod.body.accept(this)
            LUnit
        } catch (ret: ReturnSignal) {
            ret.value
        }

        cleanStack(stackBase)
        thisStack.removeLast()
        env = callerEnv

        return when (result) {
            is LString -> result.value
            else -> result.toString()
        }
    }
}