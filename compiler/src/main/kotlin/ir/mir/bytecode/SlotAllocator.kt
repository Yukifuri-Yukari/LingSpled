package yukifuri.lang.lingspled.compiler.ir.mir.bytecode

import yukifuri.lang.lingspled.compiler.ast.type.LType

class SlotAllocator {
    enum class Kind { NARROW, WIDE, REF }

    data class SlotInfo(val index: Int, val kind: Kind)

    private val scopes = ArrayDeque<MutableMap<String, SlotInfo>>()
    private var next = 0

    // start: first usable slot index (1 for instance methods — slot 0 reserved for `this`)
    fun reset(start: Int = 0) {
        scopes.clear()
        next = start
    }

    fun pushScope() = scopes.addLast(mutableMapOf())

    fun popScope() = scopes.removeLast()

    fun declare(name: String, type: LType): SlotInfo {
        val kind = type.slotKind()
        val info = SlotInfo(next, kind)
        scopes.lastOrNull()?.set(name, info)
        next += type.slotSize   // LType.slotSize: 2 for wide, 1 otherwise
        return info
    }

    fun lookup(name: String): SlotInfo? {
        for (i in scopes.indices.reversed()) {
            scopes[i][name]?.let { return it }
        }
        return null
    }

    fun count(): Int = next

    companion object {
        fun LType.slotKind(): Kind = when {
            isWide -> Kind.WIDE
            name == "Byte" || name == "Short" || name == "Int" ||
                    name == "Boolean" || name == "Float" -> Kind.NARROW
            else                                         -> Kind.REF
        }

    }
}