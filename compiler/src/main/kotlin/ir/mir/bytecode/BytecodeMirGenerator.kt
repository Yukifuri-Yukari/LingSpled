package yukifuri.lang.lingspled.compiler.ir.mir.bytecode

import yukifuri.lang.lingspled.compiler.ast.type.LType
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool
import yukifuri.lang.lingspled.compiler.codegen.util.ByteBuffer
import yukifuri.lang.lingspled.compiler.ir.hir.base.LHExpression
import yukifuri.lang.lingspled.compiler.ir.hir.expr.*
import yukifuri.lang.lingspled.compiler.ir.hir.module.*
import yukifuri.lang.lingspled.compiler.ir.hir.stmt.*
import yukifuri.lang.lingspled.compiler.ir.hir.visitor.HirVisitor
import yukifuri.lang.lingspled.compiler.ir.sym.SymbolTable
import yukifuri.lang.lingspled.compiler.codegen.bytecode.Bytecodes as B

class BytecodeMirGenerator(private val symTable: SymbolTable) : HirVisitor {

    private val pool      = ConstantPool()
    private val functions = mutableListOf<FunctionCode>()
    private val classes   = mutableListOf<ClassCode>()

    private val buf    = ByteBuffer()
    private val slots  = SlotAllocator()
    private val labels = LabelPatcher(buf)

    // Non-null while generating a class body; funDecl routes its output here.
    private var currentMethods: MutableList<FunctionCode>? = null

    // Both break and continue emit forward JUMPs whose positions are recorded here
    // and backpatched when the enclosing loop finishes.
    // For while:   continue patches land just before the backward JUMP to the condition.
    // For for:     continue patches land just before the update expression.
    private val breakJumps    = ArrayDeque<MutableList<Int>>()
    private val continueJumps = ArrayDeque<MutableList<Int>>()

    // One list per active function; deferred exprs are collected here and emitted at each exit.
    private val deferLists = ArrayDeque<MutableList<LHExpression>>()
    // Lazily allocated hidden slot for saving a non-void return value while defers run.
    private var deferReturnSlot: SlotAllocator.SlotInfo? = null

    fun generate(hir: LHFile): FileCode {
        for (stmt in hir.statements) stmt.accept(this)
        return FileCode(functions.toList(), classes.toList(), pool)
    }

    private fun beginFunction(isMethod: Boolean) {
        buf.clear()
        slots.reset(start = if (isMethod) 1 else 0)
        slots.pushScope()
        deferLists.addLast(mutableListOf())
        deferReturnSlot = null
    }

    private fun endFunction(name: String, descriptor: String, accessFlags: Short, paramSlots: Int): FunctionCode {
        slots.popScope()
        deferLists.removeLast()
        return FunctionCode(name, descriptor, accessFlags, paramSlots, slots.count() - paramSlots, buf.build())
    }

    private fun emitDefers() {
        val defers = deferLists.lastOrNull() ?: return
        for (expr in defers.asReversed()) {
            expr.accept(this)
            val t = expr.inferredType ?: LType.UNIT
            if (t != LType.UNIT) buf.put(when {
                t.isWide                    -> B.WPOP
                t.name in NARROW_PRIMITIVES -> B.POP
                else                        -> B.APOP
            })
        }
    }

    private fun List<String>.toAccessFlags(): Short {
        var f = 0
        for (m in this) f = f or when (m) {
            "public"    -> 0x01
            "private"   -> 0x02
            "protected" -> 0x04
            "static"    -> 0x08
            "final"     -> 0x10
            "override"  -> 0x20
            else        -> 0
        }
        return f.toShort()
    }

    override fun funDecl(decl: LHFunction) {
        emitLineNum(decl.row)
        val isMethod = currentMethods != null
        beginFunction(isMethod)
        for (p in decl.params) slots.declare(p.name, p.type)
        val paramSlots = slots.count()
        for (stmt in decl.body) stmt.accept(this)
        emitDefers()
        buf.put(B.RETURNV)   // implicit exit; dead code after any explicit return
        val descriptor = "(${decl.params.joinToString("") { it.type.descriptorChar }})${decl.returnType.descriptorChar}"
        val fc = endFunction(decl.name, descriptor, decl.modifiers.toAccessFlags(), paramSlots)
        (currentMethods ?: functions).add(fc)
    }

    override fun classDecl(decl: LHClass) {
        emitLineNum(decl.row)
        val saved = currentMethods
        val methodList = mutableListOf<FunctionCode>()
        currentMethods = methodList

        for (method in decl.methods) method.accept(this)

        currentMethods = saved

        val fields = decl.fields.map { FieldDescriptor(it.name, it.type.typename(), it.isConstant, it.modifiers.toAccessFlags()) }
        classes.add(ClassCode(decl.name, decl.superClass.typename(), decl.isInterface, decl.modifiers.toAccessFlags(), fields, methodList))
    }

    override fun literal(lit: LHLiteral) {
        emitLineNum(lit.row)
        when (val v = lit.value) {
            is Boolean -> { buf.put(B.PUSH8);  buf.put(if (v) 1.toByte() else 0.toByte()) }
            is Int     -> when {
                v in Byte.MIN_VALUE..Byte.MAX_VALUE   -> { buf.put(B.PUSH8);  buf.put(v.toByte()) }
                v in Short.MIN_VALUE..Short.MAX_VALUE -> { buf.put(B.PUSH16); buf.put(v.toShort()) }
                else                                  -> { buf.put(B.PUSH32); buf.put(v) }
            }
            is Long    -> { buf.put(B.PUSH64); buf.put(v) }
            is Float   -> { buf.put(B.LDC);   buf.put(pool.float(v).toShort()) }
            is Double  -> { buf.put(B.LDC2);  buf.put(pool.dbl(v).toShort()) }
            is String  -> { buf.put(B.LDC);   buf.put(pool.str(v).toShort()) }
            else       -> buf.put(B.PUSHNUL)
        }
    }

    override fun localGet(expr: LHLocalGet) {
        emitLineNum(expr.row)
        val info = slots.lookup(expr.name) ?: return
        emitLoad(info)
    }

    override fun thisRef(expr: LHThis) {
        emitLineNum(expr.row)
        buf.put(B.ALOAD)
        buf.put(0.toShort())
    }

    override fun fieldGet(expr: LHFieldGet) {
        emitLineNum(expr.row)
        expr.receiver.accept(this)
        buf.put(B.LDFIELD)
        buf.put(pool.str(expr.field).toShort())
    }

    override fun call(expr: LHCall) {
        emitLineNum(expr.row)
        if (expr.receiver == null) {
            for (arg in expr.args) arg.accept(this)

            if (symTable.findClass(expr.name) != null) {
                // Constructor: args are already on stack; NEW allocates + calls <constructor>.
                // At MIR level the operand is a CP CFunc index; the assembler resolves it
                // to the function-table index of the class's <constructor> entry.
                val ctorIdx = pool.func(expr.name, ctorDescriptor(expr.args)).toShort()
                buf.put(B.NEW)
                buf.put(ctorIdx)
            } else {
                // Free function (user-defined or builtin).
                val retType  = expr.inferredType ?: LType.UNIT
                val argTypes = expr.args.map { it.inferredType ?: LType.ANY }
                val fdIdx    = pool.func(expr.name, descriptor(argTypes, retType)).toShort()
                buf.put(B.CALL)
                buf.put(fdIdx)
            }
        } else {
            // Method call: receiver · name(args)
            val retType  = expr.inferredType ?: LType.UNIT
            val isArith  = expr.name in ARITHMETIC_OPS && retType.name in NUMERIC_TYPES

            expr.receiver.accept(this)
            if (isArith) emitNumericCast(expr.receiver.inferredType ?: LType.ANY, retType)

            for (arg in expr.args) {
                arg.accept(this)
                if (isArith) emitNumericCast(arg.inferredType ?: LType.ANY, retType)
            }

            // For arithmetic ops all operands are promoted to retType, so descriptor reflects that.
            val argTypes = if (isArith) expr.args.map { retType }
                           else expr.args.map { it.inferredType ?: LType.ANY }
            val fdIdx = pool.func(expr.name, descriptor(argTypes, retType)).toShort()
            buf.put(B.INVOKEINST)
            buf.put(fdIdx)
        }
    }

    override fun lambdaExpr(expr: LHLambda) {
        // TODO: requires ClosureAnalysisPass to fill capture lists before codegen
    }

    override fun castExpr(expr: LHCast) {
        emitLineNum(expr.row)
        expr.expr.accept(this)
        val from = expr.expr.inferredType ?: return
        val to   = expr.targetType
        if (from == to) return
        if (!emitNumericCast(from, to))
            { buf.put(B.CAST); buf.put(pool.str(to.typename()).toShort()) }
    }

    // Emits a numeric conversion instruction from [from] to [to].
    // Returns true if a conversion was emitted, false if the pair is not a numeric conversion.
    private fun emitNumericCast(from: LType, to: LType): Boolean {
        if (from == to) return true
        return when {
            from.name in NARROW_PRIMITIVES && to == LType.LONG   -> { buf.put(B.N2W); true }
            from.name in NARROW_PRIMITIVES && to == LType.DOUBLE -> { buf.put(B.N2D); true }
            from.name in NARROW_PRIMITIVES && to == LType.FLOAT  -> { buf.put(B.N2F); true }
            from == LType.FLOAT && to == LType.LONG              -> { buf.put(B.F2W); true }
            from == LType.FLOAT && to == LType.DOUBLE            -> { buf.put(B.F2D); true }
            from == LType.LONG   && to.name in NARROW_PRIMITIVES -> { buf.put(B.W2N); true }
            from == LType.LONG   && to == LType.FLOAT            -> { buf.put(B.W2F); true }
            from == LType.LONG   && to == LType.DOUBLE           -> { buf.put(B.W2D); true }
            from == LType.DOUBLE && to.name in NARROW_PRIMITIVES -> { buf.put(B.D2N); true }
            from == LType.DOUBLE && to == LType.LONG             -> { buf.put(B.D2W); true }
            from == LType.DOUBLE && to == LType.FLOAT            -> { buf.put(B.D2F); true }
            from == LType.FLOAT  && to.name in NARROW_PRIMITIVES -> { buf.put(B.F2N); true }
            else -> false
        }
    }

    override fun returnStmt(stmt: LHReturn) {
        emitLineNum(stmt.row)
        val v = stmt.value
        val defers = deferLists.lastOrNull() ?: emptyList()

        if (v == null) {
            emitDefers()
            buf.put(B.RETURNV)
            return
        }

        v.accept(this)
        val t = v.inferredType ?: LType.UNIT

        if (defers.isEmpty()) {
            buf.put(when {
                t.isWide                    -> B.WRETURN
                t.name in NARROW_PRIMITIVES -> B.RETURN
                else                        -> B.ARETURN
            })
            return
        }

        // Save return value, run defers (reversed), restore and return.
        if (deferReturnSlot == null) deferReturnSlot = slots.declare("__defer_ret__", t)
        val retSlot = deferReturnSlot!!
        emitStore(retSlot)
        emitDefers()
        emitLoad(retSlot)
        buf.put(when {
            t.isWide                    -> B.WRETURN
            t.name in NARROW_PRIMITIVES -> B.RETURN
            else                        -> B.ARETURN
        })
    }

    override fun loopControl(ctrl: LHLoopControl) {
        emitLineNum(ctrl.row)
        // Both break and continue use forward JUMPs; the enclosing loop backpatches them.
        if (ctrl.isBreak) {
            breakJumps.lastOrNull()?.add(labels.emitJump(B.JUMP))
        } else {
            continueJumps.lastOrNull()?.add(labels.emitJump(B.JUMP))
        }
    }

    override fun exprStmt(stmt: LHExprStmt) {
        emitLineNum(stmt.row)
        stmt.expr.accept(this)
        val t = stmt.expr.inferredType ?: return
        if (t == LType.UNIT) return
        buf.put(when {
            t.isWide              -> B.WPOP
            t.name in NARROW_PRIMITIVES -> B.POP
            else                  -> B.APOP
        })
    }

    override fun varDecl(decl: LHVarDecl) {
        emitLineNum(decl.row)
        val type = decl.inferredType ?: decl.declaredType
        val info = slots.declare(decl.name, type)
        decl.init?.let { init ->
            init.accept(this)
            emitStore(info)
        }
    }

    override fun assignStmt(stmt: LHAssign) {
        emitLineNum(stmt.row)
        when (val tgt = stmt.target) {
            is LHLocalTarget -> {
                stmt.value.accept(this)
                val info = slots.lookup(tgt.name) ?: return
                emitStore(info)
            }
            is LHFieldTarget -> {
                tgt.receiver.accept(this)
                stmt.value.accept(this)
                buf.put(B.STFIELD)
                buf.put(pool.str(tgt.field).toShort())
            }
        }
    }

    override fun deferStmt(stmt: LHDefer) {
        emitLineNum(stmt.row)
        deferLists.lastOrNull()?.add(stmt.expr)
    }

    override fun ifStmt(stmt: LHIf) {
        emitLineNum(stmt.row)
        //  emit cond
        //  JEQ  <else / end>
        //  emit then
        //  [JUMP <end>]      ← only when els != null
        //  [emit els]
        //  <end>:
        stmt.cond.accept(this)
        val elseJump = labels.emitJump(B.JEQ)     // jump past then if cond == 0 (false)

        slots.pushScope()
        for (s in stmt.then) s.accept(this)
        slots.popScope()

        if (stmt.els != null) {
            val endJump = labels.emitJump(B.JUMP) // jump past else
            labels.patch(elseJump)              // else starts here
            slots.pushScope()
            for (s in stmt.els) s.accept(this)
            slots.popScope()
            labels.patch(endJump)
        } else {
            labels.patch(elseJump)
        }
    }

    override fun whileStmt(stmt: LHWhile) {
        emitLineNum(stmt.row)
        //  <condStart>:
        //  emit cond
        //  JEQ  <exit>
        //  emit body
        //  <continueTarget>:   ← patch all continueJumps
        //  JUMP <condStart>    ← backward
        //  <exit>:             ← patch all breakJumps
        val condStart = labels.here()
        breakJumps.addLast(mutableListOf())
        continueJumps.addLast(mutableListOf())

        stmt.cond.accept(this)
        val exitJump = labels.emitJump(B.JEQ)

        slots.pushScope()
        for (s in stmt.body) s.accept(this)
        slots.popScope()

        for (pos in continueJumps.removeLast()) labels.patch(pos)
        labels.emitBackJump(B.JUMP, condStart)

        labels.patch(exitJump)
        for (pos in breakJumps.removeLast()) labels.patch(pos)
    }

    override fun forStmt(stmt: LHFor) {
        emitLineNum(stmt.row)
        //  emit init            ← optional, in for-scope
        //  <condStart>:
        //  emit cond
        //  JEQ  <exit>
        //  emit body            ← nested body-scope
        //  <continueTarget>:    ← patch all continueJumps (land before update)
        //  emit update          ← optional
        //  JUMP <condStart>     ← backward
        //  <exit>:              ← patch all breakJumps
        slots.pushScope()                       // for-init scope (visible to cond/update/body)
        stmt.init?.accept(this)

        val condStart = labels.here()
        breakJumps.addLast(mutableListOf())
        continueJumps.addLast(mutableListOf())

        stmt.cond.accept(this)
        val exitJump = labels.emitJump(B.JEQ)

        slots.pushScope()                       // body scope
        for (s in stmt.body) s.accept(this)
        slots.popScope()

        for (pos in continueJumps.removeLast()) labels.patch(pos)
        stmt.update?.accept(this)
        labels.emitBackJump(B.JUMP, condStart)

        labels.patch(exitJump)
        for (pos in breakJumps.removeLast()) labels.patch(pos)
        slots.popScope()
    }

    override fun whenStmt(stmt: LHWhen) {}

    private fun emitLineNum(row: Int) {
        if (row > 0) { buf.put(B.LINENUM); buf.put(row.toShort()) }
    }

    private fun emitLoad(info: SlotAllocator.SlotInfo) {
        when (info.kind) {
            SlotAllocator.Kind.NARROW -> { buf.put(B.LOAD);  buf.put(info.index.toShort()) }
            SlotAllocator.Kind.WIDE   -> { buf.put(B.WLOAD); buf.put(info.index.toShort()) }
            SlotAllocator.Kind.REF    -> { buf.put(B.ALOAD); buf.put(info.index.toShort()) }
        }
    }

    private fun emitStore(info: SlotAllocator.SlotInfo) {
        when (info.kind) {
            SlotAllocator.Kind.NARROW -> { buf.put(B.STORE);  buf.put(info.index.toShort()) }
            SlotAllocator.Kind.WIDE   -> { buf.put(B.WSTORE); buf.put(info.index.toShort()) }
            SlotAllocator.Kind.REF    -> { buf.put(B.ASTORE); buf.put(info.index.toShort()) }
        }
    }

    private fun descriptor(params: List<LType>, ret: LType): String =
        "(${params.joinToString("") { it.descriptorChar }})${ret.descriptorChar}"

    private fun ctorDescriptor(args: List<LHExpression>): String =
        descriptor(args.map { it.inferredType ?: LType.ANY }, LType.UNIT)

    companion object {
        private val NARROW_PRIMITIVES = setOf("Byte", "Short", "Int", "Boolean", "Float")
        private val NUMERIC_TYPES     = NARROW_PRIMITIVES + setOf("Long", "Double")
        private val ARITHMETIC_OPS    = setOf(
            "plus", "minus", "times", "div", "rem",
            "shl", "shr", "ushr", "and", "or", "xor", "inv", "inc", "dec"
        )
    }
}
