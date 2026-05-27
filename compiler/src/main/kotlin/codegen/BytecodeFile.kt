package yukifuri.lang.lingspled.compiler.codegen

import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CFunc
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CDbl
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CFloat
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CInt
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CLong
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.CStr
import yukifuri.lang.lingspled.compiler.codegen.ConstantPool.Entry
import yukifuri.lang.lingspled.compiler.codegen.util.ByteBuffer
import yukifuri.lang.lingspled.compiler.ir.mir.bytecode.*

class BytecodeFile {
    private sealed class OutEntry {
        class Str (val value: String)                  : OutEntry()
        class I32 (val value: Int)                     : OutEntry()
        class I64 (val value: Long)                    : OutEntry()
        class F32 (val value: Float)                   : OutEntry()
        class F64 (val value: Double)                  : OutEntry()
        class FD  (val nameIdx: Int, val descIdx: Int) : OutEntry()
        class CRef(val nameIdx: Int)                   : OutEntry()
    }

    private data class FuncEntry(val fc: FunctionCode, val className: String?)

    private class OutputCp {
        val entries  = mutableListOf<OutEntry>()
        private val strMap   = mutableMapOf<String,              Int>()
        private val i32Map   = mutableMapOf<Int,                 Int>()
        private val i64Map   = mutableMapOf<Long,                Int>()
        private val f32Map   = mutableMapOf<Float,               Int>()
        private val f64Map   = mutableMapOf<Double,              Int>()
        private val fdMap    = mutableMapOf<Pair<String,String>, Int>()
        private val classMap = mutableMapOf<String,              Int>()

        fun str(v: String) = strMap.getOrPut(v) { entries.size.also { entries.add(OutEntry.Str(v)) } }
        fun i32(v: Int)    = i32Map.getOrPut(v) { entries.size.also { entries.add(OutEntry.I32(v)) } }
        fun i64(v: Long)   = i64Map.getOrPut(v) { entries.size.also { entries.add(OutEntry.I64(v)) } }
        fun f32(v: Float)  = f32Map.getOrPut(v) { entries.size.also { entries.add(OutEntry.F32(v)) } }
        fun f64(v: Double) = f64Map.getOrPut(v) { entries.size.also { entries.add(OutEntry.F64(v)) } }

        fun fd(name: String, sig: String): Int {
            val nIdx = str(name); val dIdx = str(sig)
            return fdMap.getOrPut(name to sig) { entries.size.also { entries.add(OutEntry.FD(nIdx, dIdx)) } }
        }

        fun classRef(name: String): Int {
            val nIdx = str(name)
            return classMap.getOrPut(name) { entries.size.also { entries.add(OutEntry.CRef(nIdx)) } }
        }

        fun strIdx  (v: String)                 = strMap[v]!!
        fun i32Idx  (v: Int)                    = i32Map[v]!!
        fun i64Idx  (v: Long)                   = i64Map[v]!!
        fun f32Idx  (v: Float)                  = f32Map[v]!!
        fun f64Idx  (v: Double)                 = f64Map[v]!!
        fun fdIdx   (name: String, sig: String) = fdMap[name to sig]!!
        fun classIdx(name: String)              = classMap[name]!!
    }

    fun assemble(file: FileCode): ByteArray {
        val outCp     = buildOutputCp(file)
        val funcTable = buildFuncTable(file)
        val mirToOut  = buildMirMap(file.pool, outCp)

        val buf = ByteBuffer()

        // Magic "LSpled~~" (8 bytes) + version (8 bytes, 0)
        buf.put("LSpled~~".toByteArray(Charsets.ISO_8859_1))
        buf.put(0L)

        // Constant Pool
        buf.put(outCp.entries.size.toShort())
        for (e in outCp.entries) writeCpEntry(buf, e)

        // Top-level attributes: functions + classes
        buf.put((file.functions.size + file.classes.size).toShort())
        for (fc in file.functions) {
            buf.put(0x0001.toShort())
            writeFuncBody(buf, fc, outCp, mirToOut, funcTable, file)
        }
        for (cc in file.classes) {
            buf.put(0x0002.toShort())
            writeClassBody(buf, cc, outCp, mirToOut, funcTable, file)
        }

        return buf.build()
    }

    private fun buildOutputCp(file: FileCode): OutputCp {
        val cp  = OutputCp()
        val mir = file.pool.entries()

        // Phase 1 — strings first; FDs reference them by index
        for (e in mir) when (e) {
            is CStr  -> cp.str(e.v)
            is CFunc -> { cp.str(e.name); cp.str(e.sig) }
            else     -> {}
        }
        for (fc in file.functions) { cp.str(fc.name); cp.str(fc.descriptor) }
        for (cc in file.classes) {
            cp.str(cc.name); cp.str(cc.superClass)
            for (f in cc.fields)  { cp.str(f.name); cp.str(f.type) }
            for (m in cc.methods) { cp.str(m.name); cp.str(m.descriptor) }
        }

        // Phase 2 — scalars
        for (e in mir) when (e) {
            is CInt   -> cp.i32(e.v)
            is CLong  -> cp.i64(e.v)
            is CFloat -> cp.f32(e.v)
            is CDbl   -> cp.f64(e.v)
            else      -> {}
        }

        // Phase 3 — FunctionDescriptors (strings already interned above)
        for (e in mir) if (e is CFunc) cp.fd(e.name, e.sig)
        for (fc in file.functions) cp.fd(fc.name, fc.descriptor)
        for (cc in file.classes)   for (m in cc.methods) cp.fd(m.name, m.descriptor)

        // Phase 4 — class refs (for superIdx and class attrs)
        for (cc in file.classes) { cp.classRef(cc.name); cp.classRef(cc.superClass) }

        return cp
    }

    /** Maps each MIR CP index to its corresponding output CP index. */
    private fun buildMirMap(pool: ConstantPool, outCp: OutputCp): IntArray {
        val entries = pool.entries()
        return IntArray(entries.size) { i ->
            when (val e = entries[i]) {
                is CStr   -> outCp.strIdx(e.v)
                is CInt   -> outCp.i32Idx(e.v)
                is CLong  -> outCp.i64Idx(e.v)
                is CFloat -> outCp.f32Idx(e.v)
                is CDbl   -> outCp.f64Idx(e.v)
                is CFunc  -> outCp.fdIdx(e.name, e.sig)
            }
        }
    }

    private fun buildFuncTable(file: FileCode): List<FuncEntry> {
        val t = mutableListOf<FuncEntry>()
        for (fc in file.functions)                        t.add(FuncEntry(fc, null))
        for (cc in file.classes) for (m in cc.methods)   t.add(FuncEntry(m, cc.name))
        return t
    }

    private fun callIndex(table: List<FuncEntry>, name: String, sig: String): Int =
        table.indexOfFirst { it.className == null && it.fc.name == name && it.fc.descriptor == sig }

    private fun ctorIndex(table: List<FuncEntry>, className: String): Int =
        table.indexOfFirst { it.className == className && it.fc.name == "<constructor>" }

    // ── Bytecode patching ─────────────────────────────────────────────────────

    private fun patchBytecode(
        raw: ByteArray,
        mirEntries: List<Entry>,
        mirToOut: IntArray,
        table: List<FuncEntry>,
        file: FileCode,
    ): ByteArray {
        val b  = raw.toMutableList()
        var pc = 0
        while (pc < b.size) {
            val op = b[pc].toInt() and 0xFF
            pc++
            when (op) {
                0x01        -> pc += 1  // PUSH8   — 1-byte immediate
                0x02        -> pc += 2  // PUSH16  — 2-byte immediate
                0x03        -> pc += 4  // PUSH32  — 4-byte immediate
                0x04        -> pc += 8  // PUSH64  — 8-byte immediate

                // LOAD / STORE variants — slot index, pass through
                0x6C, 0x6D, 0x6E, 0x6F, 0x70, 0x71 -> pc += 2

                // Jump offsets — pass through
                0x5B, 0x5D, 0x5E, 0x5F, 0x60, 0x61, 0x62, 0x63, 0x64 -> pc += 2
                0x5C        -> pc += 4  // WJUMP — 4-byte offset

                // LDC / LDC2 — remap MIR CP index to output CP index
                0x7C, 0x7D  -> { writeU16(b, pc, mirToOut[readU16(b, pc)]); pc += 2 }

                // LDFIELD / STFIELD — remap MIR string index to output string index
                0x4E, 0x4F  -> { writeU16(b, pc, mirToOut[readU16(b, pc)]); pc += 2 }

                // INVOKE* — remap MIR FD index to output FD index
                0x53, 0x54, 0x55, 0x56 -> { writeU16(b, pc, mirToOut[readU16(b, pc)]); pc += 2 }

                // CALL — CP CFunc index → function table index
                0x57 -> {
                    val cf = mirEntries[readU16(b, pc)] as CFunc
                    writeU16(b, pc, callIndex(table, cf.name, cf.sig))
                    pc += 2
                }

                // NEW — CP CFunc.name is class name → find <constructor> in table
                0x4B -> {
                    val cf = mirEntries[readU16(b, pc)] as CFunc
                    writeU16(b, pc, ctorIndex(table, cf.name))
                    pc += 2
                }

                // CAST / ISINSTANCE — remap class CP index
                0x51, 0x52  -> { writeU16(b, pc, mirToOut[readU16(b, pc)]); pc += 2 }

                // LINENUM — 2-byte source line, pass through
                0x8A        -> pc += 2

                // 0-operand instructions — nothing to advance
                else -> {}
            }
        }
        return b.toByteArray()
    }

    private fun readU16(b: MutableList<Byte>, pos: Int): Int =
        (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8)

    private fun writeU16(b: MutableList<Byte>, pos: Int, v: Int) {
        b[pos]     = v.toByte()
        b[pos + 1] = (v ushr 8).toByte()
    }

    private fun writeCpEntry(buf: ByteBuffer, e: OutEntry) {
        when (e) {
            is OutEntry.Str  -> {
                val latin1 = e.value.all { it.code < 256 }
                buf.put(6.toByte())
                if (latin1) {
                    buf.put(0.toByte())
                    buf.put(e.value.length.toShort())
                    buf.put(e.value.toByteArray(Charsets.ISO_8859_1))
                } else {
                    buf.put(1.toByte())
                    buf.put(e.value.length.toShort())
                    buf.put(e.value.toByteArray(Charsets.UTF_16LE))
                }
            }
            is OutEntry.I32  -> { buf.put(2.toByte()); buf.put(e.value) }
            is OutEntry.I64  -> { buf.put(3.toByte()); buf.put(e.value) }
            is OutEntry.F32  -> { buf.put(4.toByte()); buf.put(e.value) }
            is OutEntry.F64  -> { buf.put(5.toByte()); buf.put(e.value) }
            is OutEntry.FD   -> { buf.put(7.toByte()); buf.put(e.nameIdx.toShort()); buf.put(e.descIdx.toShort()) }
            is OutEntry.CRef -> { buf.put(8.toByte()); buf.put(e.nameIdx.toShort()) }
        }
    }

    private fun writeFuncBody(
        buf: ByteBuffer,
        fc: FunctionCode,
        outCp: OutputCp,
        mirToOut: IntArray,
        table: List<FuncEntry>,
        file: FileCode,
    ) {
        val code = patchBytecode(fc.bytecode, file.pool.entries(), mirToOut, table, file)
        buf.put(outCp.strIdx(fc.name).toShort())
        buf.put(outCp.fdIdx(fc.name, fc.descriptor).toShort())
        buf.put(fc.accessFlags)
        buf.put(fc.paramSlots.toShort())
        buf.put(fc.localSlots.toShort())
        buf.put(code.size)
        buf.put(code)
    }

    private fun writeClassBody(
        buf: ByteBuffer,
        cc: ClassCode,
        outCp: OutputCp,
        mirToOut: IntArray,
        table: List<FuncEntry>,
        file: FileCode,
    ) {
        buf.put(outCp.strIdx(cc.name).toShort())
        buf.put(cc.accessFlags)
        buf.put(outCp.classIdx(cc.superClass).toShort())
        buf.put(cc.fields.size.toShort())
        for (f in cc.fields) {
            buf.put(outCp.strIdx(f.name).toShort())
            buf.put(outCp.strIdx(f.type).toShort())
            buf.put(f.accessFlags)
        }
        buf.put(cc.methods.size.toShort())
        for (m in cc.methods) writeFuncBody(buf, m, outCp, mirToOut, table, file)
    }
}
