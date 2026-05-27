package yukifuri.lang.lingspled.compiler.ir.mir.bytecode

import yukifuri.lang.lingspled.compiler.codegen.util.ByteBuffer

class LabelPatcher(private val buf: ByteBuffer) {

    // Emits opcode + 2-byte placeholder; returns the buffer offset of the placeholder.
    fun emitJump(opcode: Byte): Int {
        buf.put(opcode)
        val pos = buf.size
        buf.put(0.toShort())
        return pos
    }

    // Patches the placeholder at [jumpPos] so the jump lands at the current write position.
    fun patch(jumpPos: Int) {
        val offset = buf.size - jumpPos - 2
        buf.patchShort(jumpPos, offset.toShort())
    }

    // Returns the current write position (use as a loop-back target before emitting the header).
    fun here(): Int = buf.size

    // Emits a backwards (or fixed-target) jump to [target].
    fun emitBackJump(opcode: Byte, target: Int) {
        buf.put(opcode)
        val pos = buf.size
        buf.put(0.toShort())
        val offset = target - pos - 2
        buf.patchShort(pos, offset.toShort())
    }
}